package records.data;

import annotation.qual.Value;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column.ProgressListener;
import records.data.DisplayValue.ProgressState;
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
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.Workers;
import utility.Workers.Priority;
import utility.Workers.Worker;

import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;

import static records.data.DisplayValue.ProgressState.GETTING;
import static records.data.DisplayValue.ProgressState.QUEUED;

/**
 * Created by neil on 27/10/2016.
 */
@OnThread(Tag.FXPlatform)
public class DisplayCache
{
    private static final int INITIAL_DISPLAY_CACHE_SIZE = 60;
    private static final int MAX_DISPLAY_CACHE_SIZE = 500;
    @OnThread(Tag.FXPlatform)
    private final LoadingCache<@NonNull Integer, @NonNull DisplayCacheItem> displayCacheItems;

    @OnThread(Tag.Any)
    private final Column column;

    @OnThread(Tag.Any)
    public DisplayCache(Column column)
    {
        this.column = column;
        displayCacheItems = CacheBuilder.newBuilder()
            .initialCapacity(INITIAL_DISPLAY_CACHE_SIZE)
            .maximumSize(MAX_DISPLAY_CACHE_SIZE)
            .build(new CacheLoader<Integer, DisplayCacheItem>()
            {
                @Override
                @OnThread(value = Tag.FXPlatform, ignoreParent = true)
                public DisplayCacheItem load(Integer index) throws Exception
                {
                    return new DisplayCacheItem(index);
                }
            });
    }

    @OnThread(Tag.FXPlatform)
    public final void fetchDisplay(int index, FXPlatformConsumer<DisplayValue> callback)
    {
        try
        {
            @NonNull DisplayCacheItem item = displayCacheItems.get(index);
            item.setLoaderAndCall(callback);
        }
        catch (ExecutionException e)
        {
            callback.consume(new DisplayValue(index, e.getLocalizedMessage(), true));
        }
    }

    @OnThread(Tag.FXPlatform)
    public void cancelGetDisplay(int index)
    {
        @Nullable DisplayCacheItem item = displayCacheItems.getIfPresent(index);
        if (item != null)
        {
            item.cancelLoad();
            displayCacheItems.invalidate(index);
        }
    }

    /**
     * A display cache.  This is an FX thread item which has a value to display,
     * and knows which index it was calculated from in its parent column.  See
     * DisplayCache class.
     */
    @OnThread(Tag.FXPlatform)
    private class DisplayCacheItem
    {
        private final ValueLoader loader;
        @OnThread(value = Tag.FXPlatform, requireSynchronized = true)
        private @NonNull DisplayValue latestItem;
        @OnThread(value = Tag.FXPlatform, requireSynchronized = true)
        private @Nullable FXPlatformConsumer<DisplayValue> setDisplay;

        @SuppressWarnings("initialization") // ValueLoader, though I don't quite understand why
        public DisplayCacheItem(int index)
        {
            latestItem = new DisplayValue(index, ProgressState.QUEUED, 0.0);
            loader = new ValueLoader(index, this);
            Workers.onWorkerThread("Value load for display: " + index, Priority.FETCH, loader);
        }

        public synchronized void update(DisplayValue latestItem)
        {
            this.latestItem = latestItem;
            if (setDisplay != null)
            {
                setDisplay.consume(latestItem);
            }
        }

        public synchronized void setLoaderAndCall(FXPlatformConsumer<DisplayValue> callback)
        {
            this.setDisplay = callback;
            setDisplay.consume(latestItem);
        }

        public synchronized void cancelLoad()
        {
            this.setDisplay = null;
            Workers.cancel(loader);
        }
    }


    private class ValueLoader implements Worker
    {
        private final int originalIndex;
        private final DisplayCacheItem displayCacheItem;
        @OnThread(value = Tag.Any, requireSynchronized = true)
        private long originalFinished;
        @OnThread(value = Tag.Any, requireSynchronized = true)
        private long us;

        @OnThread(Tag.FXPlatform)
        @SuppressWarnings("initialization") // For displayCacheItem
        public ValueLoader(int index, @UnknownInitialization DisplayCacheItem displayCacheItem)
        {
            this.originalIndex = index;
            this.displayCacheItem = displayCacheItem;
        }

        public void run()
        {
            try
            {
                ProgressListener prog = d -> {
                    Platform.runLater(() -> displayCacheItem.update(new DisplayValue(originalIndex, GETTING, d)));
                };
                DisplayValue val = getDisplayValue(column.getType(), originalIndex, prog);
                Platform.runLater(() -> displayCacheItem.update(val));
            }
            catch (UserException | InternalException e)
            {
                e.printStackTrace();
                Platform.runLater(() -> {
                    String msg = e.getLocalizedMessage();
                    displayCacheItem.update(new DisplayValue(originalIndex, msg == null ? "ERROR" : ("ERR:" + msg), true));
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
        @OnThread(Tag.Simulation)
        public synchronized void queueMoved(long finished, long lastQueued)
        {
            double progress = (double)(finished - originalFinished) / (double)(us - originalFinished);
            Platform.runLater(() -> displayCacheItem.update(new DisplayValue(originalIndex, QUEUED, progress)));
        }

        @Override
        @OnThread(Tag.FX)
        public synchronized void addedToQueue(long finished, long us)
        {
            this.originalFinished = finished;
            this.us = us;
        }
    }
}
