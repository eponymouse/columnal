package records.gui;

import annotation.qual.Value;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.Column.ProgressListener;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.NumberInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeValue;
import records.data.datatype.DataTypeValue.DataTypeVisitorGet;
import records.data.datatype.DataTypeValue.GetValue;
import records.data.datatype.TypeId;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;
import utility.Workers;
import utility.Workers.Worker;

import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
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
    public final ObservableValue<DisplayValueBase> getDisplay(int index)
    {
        if (displayCacheItems == null)
            displayCacheItems = new HashMap<>(INITIAL_DISPLAY_CACHE_SIZE);
        else
        {
            DisplayCacheItem item = displayCacheItems.get(index);
            if (item != null)
                return item.display;
        }

        SimpleObjectProperty<DisplayValueBase> v = new SimpleObjectProperty<>(new DisplayValue(index, QUEUED, 0));
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
        public final SimpleObjectProperty<DisplayValueBase> display;

        public DisplayCacheItem(long age, SimpleObjectProperty<DisplayValueBase> display, ValueLoader loader)
        {
            this.age = age;
            this.display = display;
            this.loader = loader;
        }
    }


    private class ValueLoader implements Worker
    {
        private final int originalIndex;
        private final SimpleObjectProperty<DisplayValueBase> v;
        @OnThread(value = Tag.Any, requireSynchronized = true)
        private long originalFinished;
        @OnThread(value = Tag.Any, requireSynchronized = true)
        private long us;

        @OnThread(Tag.FXPlatform)
        public ValueLoader(int index, SimpleObjectProperty<DisplayValueBase> v)
        {
            this.originalIndex = index;
            this.v = v;
        }

        public void run()
        {
            try
            {
                ProgressListener prog = d -> {
                    Platform.runLater(() -> v.setValue(new DisplayValue(originalIndex, GETTING, d)));
                };
                DisplayValue val = getDisplayValue(column.getType(), originalIndex, prog);
                Platform.runLater(() -> v.setValue(val));
            }
            catch (UserException | InternalException e)
            {
                e.printStackTrace();
                Platform.runLater(() -> {
                    String msg = e.getLocalizedMessage();
                    v.setValue(new DisplayValue(originalIndex, msg == null ? "ERROR" : ("ERR:" + msg), true));
                });
            }
        }

        @OnThread(Tag.Simulation)
        private DisplayValue getDisplayValue(DataTypeValue type, int index, final ProgressListener prog) throws InternalException, UserException
        {
            return type.applyGet(new DataTypeVisitorGet<DisplayValue>()
                        {
                            @Override
                            @OnThread(Tag.Simulation)
                            public DisplayValue number(GetValue<@Value Number> g, NumberInfo displayInfo) throws InternalException, UserException
                            {
                                return new DisplayValue(originalIndex, g.getWithProgress(index, prog), displayInfo.getUnit(), displayInfo.getMinimumDP());
                            }

                            @Override
                            @OnThread(Tag.Simulation)
                            public DisplayValue text(GetValue<@Value String> g) throws InternalException, UserException
                            {
                                return new DisplayValue(originalIndex, g.getWithProgress(index, prog));
                            }

                            @Override
                            @OnThread(Tag.Simulation)
                            public DisplayValue tagged(TypeId typeName, List<TagType<DataTypeValue>> tagTypes, GetValue<Integer> g) throws InternalException, UserException
                            {
                                int tag = g.getWithProgress(index, prog);
                                TagType<DataTypeValue> tagType = tagTypes.get(tag);
                                @Nullable DataTypeValue inner = tagType.getInner();
                                if (DataType.canFitInOneNumeric(tagTypes))
                                {
                                    if (inner == null)
                                        return new DisplayValue(originalIndex, tagType.getName());
                                    else
                                        return inner.applyGet(this);
                                }
                                else
                                {
                                    return new DisplayValue(originalIndex, tagType.getName() + (inner == null ? "" : (" " + inner.applyGet(this))));
                                }
                            }

                            @Override
                            public DisplayValue tuple(List<DataTypeValue> types) throws InternalException, UserException
                            {
                                return DisplayValue.tuple(originalIndex, Utility.mapListEx(types, t -> t.applyGet(this)));
                            }

                            @Override
                            public DisplayValue array(@Nullable DataType inner, GetValue<Pair<Integer, DataTypeValue>> g) throws InternalException, UserException
                            {
                                @NonNull Pair<Integer, DataTypeValue> details = g.get(index);
                                ArrayList<DisplayValue> values = new ArrayList<>(details.getFirst());
                                for (int i = 0; i < details.getFirst(); i++)
                                {
                                    values.add(getDisplayValue(details.getSecond(), i, prog));
                                }
                                return DisplayValue.array(originalIndex, values);
                            }

                            @Override
                            public DisplayValue bool(GetValue<@Value Boolean> g) throws InternalException, UserException
                            {
                                return new DisplayValue(originalIndex, g.getWithProgress(index, prog));
                            }

                            @Override
                            public DisplayValue date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> g) throws InternalException, UserException
                            {
                                return new DisplayValue(originalIndex, g.getWithProgress(index, prog));
                            }
                        });
        }

        @Override
        public synchronized void queueMoved(long finished, long lastQueued)
        {
            double progress = (double)(finished - originalFinished) / (double)(us - originalFinished);
            Platform.runLater(() -> v.setValue(new DisplayValue(originalIndex, QUEUED, progress)));
        }

        @Override
        public synchronized void addedToQueue(long finished, long us)
        {
            this.originalFinished = finished;
            this.us = us;
        }
    }
}
