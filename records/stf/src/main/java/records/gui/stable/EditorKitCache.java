package records.gui.stable;

import annotation.qual.Value;
import annotation.units.TableDataColIndex;
import annotation.units.TableDataRowIndex;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
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
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue.GetValue;
import records.error.InternalException;
import records.error.InvalidImmediateValueException;
import records.error.UserException;
import records.gui.flex.EditorKitInterface;
import records.gui.flex.EditorKitSimpleLabel;
import records.gui.flex.EditorKit;
import records.gui.flex.FlexibleTextField;
import records.gui.kit.Document;
import records.gui.kit.ReadOnlyDocument;
import records.gui.kit.RecogniserDocument;
import records.gui.stf.TableDisplayUtility.GetDataPosition;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.Workers;
import utility.Workers.Priority;
import utility.Workers.Worker;
import utility.gui.FXUtility;
import utility.gui.TranslationUtility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
public final class EditorKitCache<@Value V> implements ColumnHandler
{
    public static enum ProgressState
    {
        GETTING, QUEUED;
    }

    // Remember that these values are *per-column* so will
    // be multiplied by number of rows:
    private static final int INITIAL_DISPLAY_CACHE_SIZE = 50;
    private static final int MAX_DISPLAY_CACHE_SIZE = 100;
    // Maps row index to cached item:
    @OnThread(Tag.FXPlatform)
    private final Cache<@NonNull Integer, @NonNull DisplayCacheItem> displayCacheItems;

    @OnThread(Tag.Any)
    private final GetValue<@Value V> getValue;
    @OnThread(Tag.Any)
    private final DataType dataType;
    private final GetDataPosition getDataPosition;
    private final @Nullable FXPlatformConsumer<VisibleDetails> formatVisibleCells;
    private final MakeEditorKit<@Value V> makeEditorKit;
    private final @TableDataColIndex int columnIndex;
    private double latestWidth = -1;

    @OnThread(Tag.Any)
    public EditorKitCache(@TableDataColIndex int columnIndex, DataType dataType, GetValue<@Value V> getValue, @Nullable FXPlatformConsumer<VisibleDetails> formatVisibleCells, GetDataPosition getDataPosition, MakeEditorKit<@Value V> makeEditorKit)
    {
        this.columnIndex = columnIndex;
        this.dataType = dataType;
        this.getValue = getValue;
        this.getDataPosition = getDataPosition;
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
        public RecogniserDocument<V> makeKit(@TableDataRowIndex int rowIndex, Pair<String, @Nullable V> initialValue, FXPlatformConsumer<CellPosition> relinquishFocus) throws InternalException, UserException;
    }

/*
    protected abstract EditorKit<V> makeEditorKit(int rowIndex, V value, FXPlatformConsumer<Boolean> onFocusChange, FXPlatformRunnable relinquishFocus) throws InternalException, UserException;
*/
    /**
     * Details about the cells which are currently visible.  Used to format columns, if a column is adjusted
     * based on what is on screen (e.g. aligning the decimal point in a numeric column)
     */
    @OnThread(Tag.FXPlatform)
    public class VisibleDetails
    {
        public final ImmutableList<FlexibleTextField> visibleCells; // First one is firstVisibleRowIndex.
        public final double width;

        private VisibleDetails(double width, Collection<? extends FlexibleTextField> visibleFields)
        {
            this.width = width;
            visibleCells = ImmutableList.copyOf(visibleFields);
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
    public void fetchValue(@TableDataRowIndex int rowIndex, FXPlatformConsumer<Boolean> focusListener, FXPlatformConsumer<CellPosition> relinquishFocus, EditorKitCallback setCellContent)
    {
        try
        {
            displayCacheItems.get(rowIndex, () -> new DisplayCacheItem(rowIndex, focusListener, relinquishFocus, setCellContent)).updateDisplay();
        }
        catch (ExecutionException e)
        {
            Log.log(e);
            setCellContent.loadedValue(rowIndex, columnIndex, new ReadOnlyDocument(e.getLocalizedMessage()));
        }
        // No need to call formatVisible here as styleTogether
        // will be called after fetches by VirtualGridSupplierIndividual
    }

    @Override
    public @OnThread(Tag.Simulation) @Value Object getValue(int index) throws InternalException, UserException
    {
        return getValue.getWithProgress(index, null);
    }

    @Override
    public void styleTogether(Collection<? extends FlexibleTextField> cellsInColumn, double columnSize)
    {
        latestWidth = columnSize;
        formatVisible(cellsInColumn, OptionalInt.empty());
    }

    @Override
    public final void columnResized(double width)
    {
        latestWidth = width;
        //formatVisible(OptionalInt.empty());
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

    protected final void formatVisible(Collection<? extends FlexibleTextField> cellsInColumn, OptionalInt rowIndexUpdated)
    {
        @TableDataRowIndex int firstVisibleRowIndexIncl = getDataPosition.getFirstVisibleRowIncl();
        @TableDataRowIndex int lastVisibleRowIndexIncl = getDataPosition.getLastVisibleRowIncl();
        
        //Log.debug("formatVisible: " + formatVisibleCells + " " + firstVisibleRowIndexIncl + " " + lastVisibleRowIndexIncl + " " + latestWidth);
        if (formatVisibleCells != null && firstVisibleRowIndexIncl != -1 && lastVisibleRowIndexIncl != -1 && latestWidth > 0)
            formatVisibleCells.consume(new VisibleDetails(latestWidth, cellsInColumn));
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

    /*
    @OnThread(Tag.Simulation)
    protected final void store(int rowIndex, V v) throws UserException, InternalException
    {
        getValue.set(rowIndex, v);
    }
    /*

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
        private final @TableDataRowIndex int rowIndex;
        // The result of loading: either value or error.  If null, still loading
        @OnThread(Tag.FXPlatform)
        private @MonotonicNonNull Either<RecogniserDocument<@Value V>, @Localized String> loadedItemOrError;
        private double progress = 0;
        @OnThread(Tag.FXPlatform)
        private final EditorKitCallback callbackSetCellContent;
        private final FXPlatformConsumer<Boolean> onFocusChange;
        private final FXPlatformConsumer<CellPosition> relinquishFocus;

        public DisplayCacheItem(@TableDataRowIndex int index, FXPlatformConsumer<Boolean> onFocusChange, FXPlatformConsumer<CellPosition> relinquishFocus, EditorKitCallback callbackSetCellContent)
        {
            this.rowIndex = index;
            loader = new ValueLoader(index, Utility.later(this));
            this.onFocusChange = onFocusChange;
            this.relinquishFocus = relinquishFocus;
            this.callbackSetCellContent = callbackSetCellContent;
            Workers.onWorkerThread("Value load for display: " + index, Priority.FETCH, loader);
        }

        public synchronized void update(String content, @Nullable @Value V loadedItem)
        {
            FXUtility.alertOnErrorFX_("Error loading value for display", () -> {
                this.loadedItemOrError = Either.<RecogniserDocument<@Value V>, @Localized String>left(makeEditorKit.makeKit(rowIndex, new Pair<>(content, loadedItem), relinquishFocus)/*makeGraphical(rowIndex, loadedItem, onFocusChange, relinquishFocus)*/);
            });
            updateDisplay();
            //formatVisible(OptionalInt.of(rowIndex));
        }

        @OnThread(Tag.FXPlatform)
        public void updateDisplay()
        {
            if (loadedItemOrError != null)
            {
                Document document = loadedItemOrError.<Document>either(k -> k, err -> new ReadOnlyDocument(err));
                this.callbackSetCellContent.loadedValue(rowIndex, columnIndex, document);
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
        public ValueLoader(int index, DisplayCacheItem displayCacheItem)
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
                @Value V val = getValue.getWithProgress(originalIndex, prog);
                String valAsStr = DataTypeUtility.valueToString(dataType, val, null);
                Platform.runLater(() -> displayCacheItem.update(valAsStr, val));
            }
            catch (InvalidImmediateValueException e)
            {
                Platform.runLater(() -> displayCacheItem.update(e.getInvalid(), null));
            }
            catch (UserException | InternalException e)
            {
                if (e instanceof InternalException)
                    Log.log(e);
                Platform.runLater(new Runnable()
                {
                    @Override
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
