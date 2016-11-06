package records.gui;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.Column.ProgressListener;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitorGet;
import records.data.datatype.DataType.GetValue;
import records.data.datatype.DataType.NumberDisplayInfo;
import records.data.datatype.DataType.TagType;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Workers;
import utility.Workers.Worker;

import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import static records.gui.DisplayValue.ProgressState.GETTING;
import static records.gui.DisplayValue.ProgressState.QUEUED;

/**
 * Created by neil on 27/10/2016.
 */
public class DisplayCache
{
    private static final int INITIAL_DISPLAY_CACHE_SIZE = 100;
    private static final int DISPLAY_CACHE_SIZE = 1000;
    // We access this more often by index than by age so we use
    // index as key and store age within:
    @MonotonicNonNull
    @OnThread(Tag.FXPlatform)
    private HashMap<@NonNull Integer, @NonNull DisplayCacheItem> displayCacheItems;
    // Count of latest age:
    private long latestAge = 0;

    @OnThread(Tag.Any)
    private final Column column;

    @OnThread(Tag.Any)
    public DisplayCache(Column column)
    {
        this.column = column;
    }

    @OnThread(Tag.FXPlatform)
    public final ObservableValue<DisplayValue> getDisplay(int index)
    {
        if (displayCacheItems == null)
            displayCacheItems = new HashMap<>(INITIAL_DISPLAY_CACHE_SIZE);
        else
        {
            DisplayCacheItem item = displayCacheItems.get(index);
            if (item != null)
                return item.display;
        }

        SimpleObjectProperty<DisplayValue> v = new SimpleObjectProperty<>(new DisplayValue(QUEUED, 0));
        ValueLoader loader = new ValueLoader(index, v);
        Workers.onWorkerThread("Value load for display: " + index, loader);

        // Add to cache, removing one if we've reached the limit:
        if (displayCacheItems.size() >= DISPLAY_CACHE_SIZE)
        {
            // We can't rely on ages being consecutive because cancellations
            // may have removed arbitrarily from cache:
            int minIndex = -1;
            long minAge = Long.MAX_VALUE;
            for (Entry<@NonNull Integer, @NonNull DisplayCacheItem> item : displayCacheItems.entrySet())
            {
                if (item.getValue().age < minAge)
                {
                    minAge = item.getValue().age;
                    minIndex = item.getKey();
                }
            }
            displayCacheItems.remove(minIndex);
        }
        displayCacheItems.put(index, new DisplayCacheItem(latestAge, v, loader));
        latestAge += 1;

        return v;
    }

    @OnThread(Tag.FXPlatform)
    public void cancelGetDisplay(int index)
    {
        if (displayCacheItems == null)
            return;

        DisplayCacheItem item = displayCacheItems.remove(index);
        if (item != null)
        {
            Workers.cancel(item.loader);
        }
    }

    /**
     * A display cache.  This is an FX thread item which has a value to display,
     * and knows which index it was calculated from in its parent column.  See
     * DisplayCache class.
     */
    @OnThread(Tag.FXPlatform)
    private static class DisplayCacheItem
    {
        public final long age;
        public final ValueLoader loader;
        public final SimpleObjectProperty<DisplayValue> display;

        public DisplayCacheItem(long age, SimpleObjectProperty<DisplayValue> display, ValueLoader loader)
        {
            this.age = age;
            this.display = display;
            this.loader = loader;
        }
    }


    private class ValueLoader implements Worker
    {
        private final int index;
        private final SimpleObjectProperty<DisplayValue> v;
        @OnThread(value = Tag.Any, requireSynchronized = true)
        private long originalFinished;
        @OnThread(value = Tag.Any, requireSynchronized = true)
        private long us;

        @OnThread(Tag.FXPlatform)
        public ValueLoader(int index, SimpleObjectProperty<DisplayValue> v)
        {
            this.index = index;
            this.v = v;
        }

        public void run()
        {
            try
            {
                ProgressListener prog = d -> {
                    Platform.runLater(() -> v.setValue(new DisplayValue(GETTING, d)));
                };
                DisplayValue val = column.getType().apply(new DataTypeVisitorGet<DisplayValue>()
                {
                    @Override
                    public DisplayValue number(GetValue<Number> g, NumberDisplayInfo displayInfo) throws InternalException, UserException
                    {
                        return new DisplayValue(g.getWithProgress(index, prog), displayInfo.getDisplayPrefix());
                    }

                    @Override
                    public DisplayValue text(GetValue<String> g) throws InternalException, UserException
                    {
                        return new DisplayValue(g.getWithProgress(index, prog));
                    }

                    @Override
                    public DisplayValue tagged(List<TagType> tagTypes, GetValue<Integer> g) throws InternalException, UserException
                    {
                        int tag = g.getWithProgress(index, prog);
                        TagType tagType = tagTypes.get(tag);
                        @Nullable DataType inner = tagType.getInner();
                        if (DataType.canFitInOneNumeric(tagTypes))
                        {
                            if (inner == null)
                                return new DisplayValue(tagType.getName());
                            else
                                return inner.apply(this);
                        }
                        else
                        {
                            return new DisplayValue(tagType.getName() + (inner == null ? "" : (" " + inner.apply(this))));
                        }
                    }
                });
                Platform.runLater(() -> v.setValue(val));
            }
            catch (UserException | InternalException e)
            {
                Platform.runLater(() -> {
                    String msg = e.getLocalizedMessage();
                    v.setValue(new DisplayValue(msg == null ? "ERROR" : msg, true));
                });
            }
        }

        @Override
        public synchronized void queueMoved(long finished, long lastQueued)
        {
            double progress = (double)(finished - originalFinished) / (double)(us - originalFinished);
            Platform.runLater(() -> v.setValue(new DisplayValue(QUEUED, progress)));
        }

        @Override
        public synchronized void addedToQueue(long finished, long us)
        {
            this.originalFinished = finished;
            this.us = us;
        }
    }
}
