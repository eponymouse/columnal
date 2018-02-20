package records.gui.stable;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import javafx.application.Platform;
import log.Log;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import records.data.Column;
import records.data.Column.ProgressListener;
import records.data.ColumnId;
import records.data.datatype.DataTypeValue.GetValue;
import records.error.InternalException;
import records.error.UserException;
import records.gui.stf.EditorKitSimpleLabel;
import records.gui.stf.StructuredTextField.EditorKit;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Workers;
import utility.Workers.Priority;
import utility.Workers.Worker;
import utility.gui.FXUtility;
import utility.gui.TranslationUtility;

import java.util.Map.Entry;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;

/**
 * EditorKitCache is responsible for managing the thread-hopping loading
 * of values for a single column, and the display in case of errors or loading bars.
 * The display of actual items is handled by the caller of this class,
 * as is any saving of edited values.
 *
 * V is the type of the value being displayed, e.g. Number
 */
@OnThread(Tag.FXPlatform)
public final class EditorKitCache<V> implements ColumnHandler
{
    public static enum ProgressState
    {
        GETTING, QUEUED;
    }

    private static final int INITIAL_DISPLAY_CACHE_SIZE = 60;
    private static final int MAX_DISPLAY_CACHE_SIZE = 500;
    // Maps row index to cached item:
    @OnThread(Tag.FXPlatform)
    private final Cache<@NonNull Integer, @NonNull DisplayCacheItem> displayCacheItems;

    @OnThread(Tag.Any)
    private final GetValue<V> getValue;
    private final @Nullable FXPlatformConsumer<VisibleDetails> formatVisibleCells;
    private final MakeEditorKit<V> makeEditorKit;
    private final int columnIndex;
    private double latestWidth = -1;

    @OnThread(Tag.Any)
    public EditorKitCache(int columnIndex, GetValue<V> getValue, @Nullable FXPlatformConsumer<VisibleDetails> formatVisibleCells, MakeEditorKit<V> makeEditorKit)
    {
        this.columnIndex = columnIndex;
        this.getValue = getValue;
        this.formatVisibleCells = formatVisibleCells;
        this.makeEditorKit = makeEditorKit;
        displayCacheItems = CacheBuilder.newBuilder()
            .initialCapacity(INITIAL_DISPLAY_CACHE_SIZE)
            .maximumSize(MAX_DISPLAY_CACHE_SIZE)
            .build();
    }

    public static interface MakeEditorKit<V>
    {
        @OnThread(Tag.FXPlatform)
        public EditorKit<V> makeKit(int rowIndex, V initialValue, FXPlatformConsumer<CellPosition> relinquishFocus) throws InternalException, UserException;
    }

/*
    protected abstract EditorKit<V> makeEditorKit(int rowIndex, V value, FXPlatformConsumer<Boolean> onFocusChange, FXPlatformRunnable relinquishFocus) throws InternalException, UserException;
*/
    /**
     * Details about the cells which are currently visible.  Used to format columns, if a column is adjusted
     * based on what is on screen (e.g. aligning the decimal point in a numeric column)
     */
    public class VisibleDetails
    {
        /*
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
                @Nullable DisplayCacheItem item = displayCacheItems.getIfPresent(i);
                @Nullable Either<Pair<V, G>, @Localized String> loadedItemOrError = item == null ? null : item.loadedItemOrError;
                if (loadedItemOrError != null)
                    visibleCells.add(loadedItemOrError.<@Nullable G>either(p -> p.getSecond(), s -> (@Nullable G)null));
                else
                    visibleCells.add(null);
            }
        }
        */
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
    public void fetchValue(int rowIndex, FXPlatformConsumer<Boolean> focusListener, FXPlatformConsumer<CellPosition> relinquishFocus, EditorKitCallback setCellContent)
    {
        try
        {
            displayCacheItems.get(rowIndex, () -> new DisplayCacheItem(rowIndex, focusListener, relinquishFocus, setCellContent)).updateDisplay();
        }
        catch (ExecutionException e)
        {
            Log.log(e);
            setCellContent.loadedValue(rowIndex, columnIndex, new EditorKitSimpleLabel<>(e.getLocalizedMessage()));
        }
        formatVisible(OptionalInt.of(rowIndex));
    }

    @Override
    public final void columnResized(double width)
    {
        latestWidth = width;
        formatVisible(OptionalInt.empty());
    }

    @Override
    public boolean isEditable()
    {
        // Default is that we are editable:
        return true;
    }

    /*
    protected final @Nullable G getRowIfShowing(int index)
    {
        @Nullable DisplayCacheItem item = displayCacheItems.getIfPresent(index);
        if (item != null && item.loadedItemOrError != null)
        {
            return item.loadedItemOrError.<@Nullable G>either(p -> p.getSecond(), s -> null);
        }
        return null;
    }*/

    protected final void formatVisible(OptionalInt rowIndexUpdated)
    {
        /* TODO
        if (formatVisibleCells != null && firstVisibleRowIndexIncl != -1 && lastVisibleRowIndexIncl != -1 && latestWidth > 0)
            formatVisibleCells.consume(new VisibleDetails(rowIndexUpdated, firstVisibleRowIndexIncl, lastVisibleRowIndexIncl, latestWidth));
        */
    }

    @Override
    public @OnThread(Tag.FXPlatform) void modifiedDataItems(int startRowIncl, int endRowIncl)
    {
        for (int row = startRowIncl; row <= endRowIncl; row++)
            displayCacheItems.invalidate(row);
    }

    @Override
    public @OnThread(Tag.FXPlatform) void removedAddedRows(int startRowIncl, int removedRowsCount, int addedRowsCount)
    {
        if (removedRowsCount == 0 && addedRowsCount == 0)
            return; // Shouldn't be called like this, but if so, nothing to do

        // Take copy:
        TreeMap<Integer, DisplayCacheItem> prev = new TreeMap<>(displayCacheItems.asMap());
        @Nullable Entry<Integer, DisplayCacheItem> maxEntry = prev.lastEntry();
        if (maxEntry == null)
            return; // If nothing in the map, nothing to do anyway
        int maxPresent = maxEntry.getKey();
        // Now we must invalidate/modify previous entries:
        for (int row = startRowIncl; row < maxPresent; row++)
        {
            // Row is invalid at old key, for sure:
            displayCacheItems.invalidate(row);

            @Nullable DisplayCacheItem prevAtRow = prev.get(row);
            // We can add back at new pos if it wasn't in the removed section:
            if (row >= startRowIncl + removedRowsCount && prevAtRow != null)
            {
                displayCacheItems.put(row - removedRowsCount + addedRowsCount, prevAtRow);
            }
        }
    }

    @Override
    public @OnThread(Tag.FXPlatform) void addedColumn(Column newColumn)
    {
    }

    @Override
    public @OnThread(Tag.FXPlatform) void removedColumn(ColumnId oldColumnId)
    {
    }

    @OnThread(Tag.Simulation)
    protected final void store(int rowIndex, V v) throws UserException, InternalException
    {
        getValue.set(rowIndex, v);
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
        private @MonotonicNonNull Either<Pair<V, EditorKit<V>>, @Localized String> loadedItemOrError;
        private double progress = 0;
        @OnThread(Tag.FXPlatform)
        private final EditorKitCallback callbackSetCellContent;
        private final FXPlatformConsumer<Boolean> onFocusChange;
        private final FXPlatformConsumer<CellPosition> relinquishFocus;

        @SuppressWarnings("initialization") // ValueLoader, though I don't quite understand why
        public DisplayCacheItem(int index, FXPlatformConsumer<Boolean> onFocusChange, FXPlatformConsumer<CellPosition> relinquishFocus, EditorKitCallback callbackSetCellContent)
        {
            this.rowIndex = index;
            loader = new ValueLoader(index, this);
            this.onFocusChange = onFocusChange;
            this.relinquishFocus = relinquishFocus;
            this.callbackSetCellContent = callbackSetCellContent;
            Workers.onWorkerThread("Value load for display: " + index, Priority.FETCH, loader);
        }

        public synchronized void update(V loadedItem)
        {
            FXUtility.alertOnErrorFX_(() -> {
                this.loadedItemOrError = Either.left(new Pair<>(loadedItem, makeEditorKit.makeKit(rowIndex, loadedItem, relinquishFocus)/*makeGraphical(rowIndex, loadedItem, onFocusChange, relinquishFocus)*/));
            });
            updateDisplay();
            formatVisible(OptionalInt.of(rowIndex));
        }

        @OnThread(Tag.FXPlatform)
        public void updateDisplay()
        {
            if (loadedItemOrError != null)
            {
                EditorKit<V> editorKit = loadedItemOrError.either(p -> p.getSecond(), err -> new EditorKitSimpleLabel<>(err));
                this.callbackSetCellContent.loadedValue(rowIndex, columnIndex, editorKit);
            }
            else
            {
                //this.callbackSetCellContent.loadedValue(new Label("Loading: " + progress));
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
            updateDisplay();
        }

        public void error(@Localized String error)
        {
            this.loadedItemOrError = Either.right(error);
            updateDisplay();
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
                Log.log(e);
                Platform.runLater(new Runnable()
                {
                    @Override
                    @SuppressWarnings("localization") // TODO localise this
                    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
                    public void run()
                    {
                        String msg = e.getLocalizedMessage();
                        displayCacheItem.error(msg == null ? TranslationUtility.getString("loading.error.nodetail") : TranslationUtility.getString("loading.error.detail", msg));
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
