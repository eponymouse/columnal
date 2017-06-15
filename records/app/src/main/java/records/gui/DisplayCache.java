package records.gui;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column.ProgressListener;
import records.data.datatype.DataTypeValue.GetValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.FXPlatformFunction;
import utility.Pair;
import utility.Workers;
import utility.Workers.Priority;
import utility.Workers.Worker;
import records.gui.stable.StableView.ColumnHandler;
import records.gui.stable.StableView.CellContentReceiver;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * DisplayCache is responsible for managing the thread-hopping loading
 * of values, and the display in case of errors or loading bars.
 * The display of actual items is handled by the caller of this class,
 * as is any saving of edited values.
 *
 * V is the type of the value being displayed, e.g. Number
 * G is the type of the graphical item used to display it
 *    Once constructed, each G will only be used for a fixed row index,
 *    and its value will only change by being edited on the FX thread.
 */
@OnThread(Tag.FXPlatform)
public abstract class DisplayCache<V, G> implements ColumnHandler
{
    public static enum ProgressState
    {
        GETTING, QUEUED;
    }

    private static final int INITIAL_DISPLAY_CACHE_SIZE = 60;
    private static final int MAX_DISPLAY_CACHE_SIZE = 500;
    @OnThread(Tag.FXPlatform)
    private final LoadingCache<@NonNull Integer, @NonNull DisplayCacheItem> displayCacheItems;

    private final GetValue<V> getValue;
    private final @Nullable FXPlatformConsumer<VisibleDetails> formatVisibleCells;
    private final FXPlatformFunction<G, Region> getNode;
    private int firstVisibleRowIndexIncl = -1;
    private int lastVisibleRowIndexIncl = -1;
    private double latestWidth = -1;

    @OnThread(Tag.Any)
    public DisplayCache(GetValue<V> getValue, @Nullable FXPlatformConsumer<VisibleDetails> formatVisibleCells, FXPlatformFunction<G, Region> getNode)
    {
        this.getValue = getValue;
        this.formatVisibleCells = formatVisibleCells;
        this.getNode = getNode;
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

    protected abstract G makeGraphical(int rowIndex, V value);

    public class VisibleDetails
    {
        public final int firstVisibleRowIndex;
        public final List<@Nullable G> visibleCells; // First one is firstVisibleRowIndex; If any are null it is because they are still loading
        public final OptionalInt newVisibleIndex; // Index into visibleCells, not a row number, which is the cause for this update
        public final double width;

        private VisibleDetails(OptionalInt rowIndex, int firstVisibleRowIndexIncl, int lastVisibleRowIndexIncl, double width)
        {
            this.firstVisibleRowIndex = firstVisibleRowIndexIncl;
            // Why doesn't OptionalInt have a map method?
            this.newVisibleIndex = rowIndex.isPresent() ? OptionalInt.of(rowIndex.getAsInt() - firstVisibleRowIndexIncl) : OptionalInt.empty();
            this.width = width;

            visibleCells = new ArrayList<>(lastVisibleRowIndexIncl - firstVisibleRowIndexIncl + 1);
            for (int i = firstVisibleRowIndexIncl; i <= lastVisibleRowIndexIncl; i++)
            {
                @Nullable Either<Pair<V, G>, @Localized String> loadedItemOrError = displayCacheItems.getUnchecked(i).loadedItemOrError;
                if (loadedItemOrError != null)
                    visibleCells.add(loadedItemOrError.<@Nullable G>either(p -> p.getSecond(), s -> (@Nullable G)null));
                else
                    visibleCells.add(null);
            }
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

    @Override
    public final void fetchValue(int rowIndex, CellContentReceiver receiver, int firstVisibleRowIndexIncl, int lastVisibleRowIndexIncl)
    {
        this.firstVisibleRowIndexIncl = firstVisibleRowIndexIncl;
        this.lastVisibleRowIndexIncl = lastVisibleRowIndexIncl;
        @NonNull DisplayCacheItem item = displayCacheItems.getUnchecked(rowIndex);
        formatVisible(OptionalInt.of(rowIndex));

        item.setCallback(receiver);
    }

    @Override
    public final void columnResized(double width)
    {
        latestWidth = width;
        formatVisible(OptionalInt.empty());
    }

    protected final @Nullable G getRowIfShowing(int index)
    {
        @Nullable DisplayCacheItem item = displayCacheItems.getIfPresent(index);
        if (item != null && item.loadedItemOrError != null)
        {
            return item.loadedItemOrError.<@Nullable G>either(Pair::getSecond, s -> null);
        }
        return null;
    }

    protected final void formatVisible(OptionalInt rowIndexUpdated)
    {
        if (formatVisibleCells != null && firstVisibleRowIndexIncl != -1 && lastVisibleRowIndexIncl != -1 && latestWidth > 0)
            formatVisibleCells.consume(new VisibleDetails(rowIndexUpdated, firstVisibleRowIndexIncl, lastVisibleRowIndexIncl, latestWidth));
    }

    /**
     * A display cache.  This sets off the loader for its value, and in the mean time
     * displays a loading bar, until it turns into either an error message or loaded item;
     */
    @OnThread(Tag.FXPlatform)
    private class DisplayCacheItem
    {
        // The loader which is fetching the item
        private final ValueLoader loader;
        // The row index (fixed) of this item
        private final int rowIndex;
        // The result of loading: either value or error.  If null, still loading
        @OnThread(Tag.FXPlatform)
        private @MonotonicNonNull Either<Pair<V, G>, @Localized String> loadedItemOrError;
        private double progress = 0;
        @OnThread(Tag.FXPlatform)
        private @MonotonicNonNull CellContentReceiver callback;

        @SuppressWarnings("initialization") // ValueLoader, though I don't quite understand why
        public DisplayCacheItem(int index)
        {
            this.rowIndex = index;
            loader = new ValueLoader(index, this);
            Workers.onWorkerThread("Value load for display: " + index, Priority.FETCH, loader);
            triggerCallback();
        }

        public synchronized void update(V loadedItem)
        {
            this.loadedItemOrError = Either.left(new Pair<>(loadedItem, makeGraphical(rowIndex, loadedItem)));
            triggerCallback();
            formatVisible(OptionalInt.of(rowIndex));
        }

        @OnThread(Tag.FXPlatform)
        public void triggerCallback()
        {
            if (callback != null)
            {
                if (loadedItemOrError != null)
                {
                    callback.setCellContent(rowIndex, loadedItemOrError.either(p -> getNode.apply(p.getSecond()), Label::new));
                }
                else
                {
                    callback.setCellContent(rowIndex, new Label("Loading: " + progress));
                }
            }
        }

        public synchronized void cancelLoad()
        {
            Workers.cancel(loader);
        }

        public void updateProgress(ProgressState progressState, double progress)
        {
            // TODO store progressState
            this.progress = progress;
            triggerCallback();
        }

        public void error(@Localized String error)
        {
            this.loadedItemOrError = Either.right(error);
            triggerCallback();
        }

        public void setCallback(CellContentReceiver receiver)
        {
            this.callback = receiver;
            triggerCallback();
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
                    Platform.runLater(() -> displayCacheItem.updateProgress(ProgressState.GETTING, d));
                };
                prog.progressUpdate(0.0);
                V val = getValue.getWithProgress(originalIndex, prog);
                Platform.runLater(() -> displayCacheItem.update(val));
            }
            catch (UserException | InternalException e)
            {
                e.printStackTrace();
                Platform.runLater(new Runnable()
                {
                    @Override
                    @SuppressWarnings("localization") // TODO localise this
                    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
                    public void run()
                    {
                        String msg = e.getLocalizedMessage();
                        displayCacheItem.error(msg == null ? "ERROR" : ("ERR:" + msg));
                    }
                });
            }
        }

        @Override
        @OnThread(Tag.Simulation)
        public synchronized void queueMoved(long finished, long lastQueued)
        {
            double progress = (double)(finished - originalFinished) / (double)(us - originalFinished);
            Platform.runLater(() -> displayCacheItem.updateProgress(ProgressState.QUEUED, progress));
        }

        @Override
        @OnThread(Tag.Any)
        public synchronized void addedToQueue(long finished, long us)
        {
            this.originalFinished = finished;
            this.us = us;
        }
    }
}
