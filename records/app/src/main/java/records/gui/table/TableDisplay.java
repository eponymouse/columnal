package records.gui.table;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import annotation.units.GridAreaRowIndex;
import annotation.units.TableDataColIndex;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.text.TextFlow;
import log.Log;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import records.data.*;
import records.data.Column.AlteredState;
import records.data.RecordSet.RecordSetListener;
import records.data.Table.InitialLoadDetails;
import records.data.Table.Display;
import records.data.Table.TableDisplayBase;
import records.data.TableOperations.DeleteColumn;
import records.data.TableOperations.DeleteRows;
import records.data.TableOperations.InsertRows;
import records.data.TableOperations.RenameTable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitorEx;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.data.datatype.DataTypeValue.DataTypeVisitorGet;
import records.data.datatype.DataTypeValue.GetValue;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeId;
import records.data.unit.Unit;
import records.error.ExceptionWithStyle;
import records.error.InternalException;
import records.error.UserException;
import records.errors.ExpressionErrorException;
import records.errors.ExpressionErrorException.EditableExpression;
import records.exporters.manager.ExporterManager;
import records.gui.*;
import records.gui.EditImmediateColumnDialog.InitialFocus;
import records.gui.grid.CellSelection;
import records.gui.grid.GridArea;
import records.gui.grid.GridAreaCellPosition;
import records.gui.grid.RectangleBounds;
import records.gui.grid.VirtualGrid.ListenerOutcome;
import records.gui.grid.VirtualGridSupplier.ItemState;
import records.gui.grid.VirtualGridSupplier.ViewOrder;
import records.gui.grid.VirtualGridSupplier.VisibleBounds;
import records.gui.grid.VirtualGridSupplierFloating;
import records.gui.grid.VirtualGridSupplierFloating.FloatingItem;
import records.gui.stable.ColumnOperation;
import records.gui.stable.ColumnDetails;
import records.gui.stable.SimpleColumnOperation;
import records.gui.dtf.TableDisplayUtility;
import records.gui.dtf.TableDisplayUtility.GetDataPosition;
import records.gui.table.PickTypeTransformDialog.TypeTransform;
import records.importers.ClipboardUtils;
import records.importers.ClipboardUtils.RowRange;
import records.transformations.Aggregate;
import records.transformations.Concatenate;
import records.transformations.Filter;
import records.transformations.Join;
import records.transformations.ManualEdit;
import records.transformations.ManualEdit.ColumnReplacementValues;
import records.transformations.Sort;
import records.transformations.Sort.Direction;
import records.transformations.Calculate;
import records.transformations.expression.CallExpression;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.MultipleTableLookup;
import records.transformations.expression.IdentExpression;
import records.transformations.expression.StandardFunction;
import records.transformations.expression.TypeState;
import records.transformations.function.FunctionList;
import records.transformations.function.Mean;
import records.transformations.function.Sum;
import records.transformations.function.ToString;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.*;
import utility.Utility.ListEx;
import utility.Utility.Record;
import utility.Workers.Priority;
import utility.gui.Clickable;
import utility.gui.FXUtility;
import utility.gui.GUI;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * A specialisation of DataDisplay that links it to an actual Table.
 */
@OnThread(Tag.FXPlatform)
public class TableDisplay extends DataDisplay implements RecordSetListener, TableDisplayBase
{
    private static final int INITIAL_LOAD = 100;
    private static final int LOAD_CHUNK = 100;
    // Can be null if there is error in initial loading
    @OnThread(Tag.Any)
    private final @Nullable RecordSet recordSet;
    // The latest error message:
    @OnThread(Tag.FXPlatform)
    private final ObjectProperty<@Nullable ExceptionWithStyle> errorMessage = new SimpleObjectProperty<>(null);
    private final Table table;
    private final View parent;
    @OnThread(Tag.Any)
    private final AtomicReference<CellPosition> mostRecentBounds;
    // Should only be set in loadPosition and setDisplay:
    private final ObjectProperty<Pair<Display, ImmutableList<ColumnId>>> columnDisplay = new SimpleObjectProperty<>(new Pair<>(Display.ALL, ImmutableList.of()));
    private final TableBorderOverlay tableBorderOverlay;
    private final @Nullable TableHat tableHat;
    private final TableErrorDisplay tableErrorDisplay;
    private boolean currentKnownRowsIsFinal = false;

    private TableId curTableId;

    private final FXPlatformRunnable onModify;
    private boolean queuedUpdateRows = false;

    @OnThread(Tag.Any)
    public Table getTable()
    {
        return table;
    }

    @Override
    public void cleanupFloatingItems(VirtualGridSupplierFloating floatingItems)
    {
        super.cleanupFloatingItems(floatingItems);
        if (tableHat != null)
            floatingItems.removeItem(tableHat);
        floatingItems.removeItem(tableErrorDisplay);
        floatingItems.removeItem(tableBorderOverlay);
    }

    @Override
    public @OnThread(Tag.FXPlatform) void updateKnownRows(@GridAreaRowIndex int checkUpToRowInclGrid, FXPlatformRunnable updateSizeAndPositions)
    {
        @TableDataRowIndex int checkUpToRowIncl = getRowIndexWithinTable(checkUpToRowInclGrid);
        if (!currentKnownRowsIsFinal && currentKnownRows < checkUpToRowIncl && recordSet != null && !queuedUpdateRows)
        {
            final @NonNull RecordSet recordSetFinal = recordSet;
            queuedUpdateRows = true;
            Workers.onWorkerThread("Fetching row size", Priority.FETCH, () -> {
                try
                {
                    // Short-cut: check if the last index we are interested in has a row.  If so, can return early:
                    // TODO restore this optimisation, but note that it was removed because
                    // it wasn't working, and would show tables too short:
                    /*
                    boolean lastRowValid = recordSetFinal.indexValid(checkUpToRowIncl);
                    if (lastRowValid)
                    {
                        Platform.runLater(() -> {
                            currentKnownRows = checkUpToRowIncl;
                            currentKnownRowsIsFinal = false;
                            updateSizeAndPositions.run();
                        });
                    } else
                        */
                    {
                        // Just a matter of working out where it ends.  Since we know end is close,
                        // just force with getLength:
                        @SuppressWarnings("units")
                        @TableDataRowIndex int length = recordSetFinal.getLength();
                        Platform.runLater(() -> {
                            queuedUpdateRows = false;
                            currentKnownRows = length;
                            currentKnownRowsIsFinal = true;
                            updateSizeAndPositions.run();
                        });
                    }
                }
                catch (InternalException | UserException e)
                {
                    Log.log(e);
                    // We just don't call back the update function
                }
            });
        }
    }

    @Override
    public @OnThread(Tag.FXPlatform) Pair<ListenerOutcome, @Nullable FXPlatformConsumer<VisibleBounds>> selectionChanged(@Nullable CellSelection oldSelection, @Nullable CellSelection newSelection)
    {
        ListenerOutcome outcome = super.selectionChanged(oldSelection, newSelection).getFirst();        
        return new Pair<>(outcome, tableBorderOverlay::updateClip);
    }

    public CellPosition getDataPosition(@UnknownInitialization(DataDisplay.class) TableDisplay this, @TableDataRowIndex int rowIndex, @TableDataColIndex int columnIndex)
    {
        return getPosition().offsetByRowCols(getDataDisplayTopLeftIncl().rowIndex + rowIndex, getDataDisplayTopLeftIncl().columnIndex + columnIndex);
    }

    private TableDisplayUtility.GetDataPosition makeGetDataPosition(@UnknownInitialization(DataDisplay.class) TableDisplay this)
    {
        return new GetDataPosition()
        {
            @SuppressWarnings("units")
            private final @TableDataRowIndex int invalid = -1;
            
            @Override
            public @OnThread(Tag.FXPlatform) CellPosition getDataPosition(@TableDataRowIndex int rowIndex, @TableDataColIndex int columnIndex)
            {
                return TableDisplay.this.getDataPosition(rowIndex, columnIndex);
            }

            @Override
            public @OnThread(Tag.FXPlatform) @TableDataRowIndex int getFirstVisibleRowIncl()
            {
                Optional<VisibleBounds> bounds = withParent(g -> g.getVisibleBounds());
                if (!bounds.isPresent())
                    return invalid;
                @SuppressWarnings("units")
                @GridAreaRowIndex int gridAreaRow = bounds.get().firstRowIncl - getPosition().rowIndex;
                @TableDataRowIndex int rowIndexWithinTable = TableDisplay.this.getRowIndexWithinTable(gridAreaRow);
                @SuppressWarnings("units")
                @TableDataRowIndex int zero = 0;
                if (rowIndexWithinTable < zero)
                    return zero;
                else
                    return rowIndexWithinTable;
            }

            @Override
            public @OnThread(Tag.FXPlatform) @TableDataRowIndex int getLastVisibleRowIncl()
            {
                Optional<VisibleBounds> bounds = withParent(g -> g.getVisibleBounds());
                if (!bounds.isPresent())
                    return invalid;
                @SuppressWarnings("units")
                @GridAreaRowIndex int gridAreaRow = bounds.get().lastRowIncl - getPosition().rowIndex;
                @TableDataRowIndex int rowIndexWithinTable = TableDisplay.this.getRowIndexWithinTable(gridAreaRow);
                if (rowIndexWithinTable >= currentKnownRows)
                {
                    @SuppressWarnings("units")
                    @TableDataRowIndex int lastRow = currentKnownRows - 1;
                    return lastRow;
                }
                else
                    return rowIndexWithinTable;
            }
        };
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public void modifiedDataItems(int startRowIncl, int endRowIncl)
    {
        onModify.run();
    }

    @Override
    public void removedAddedRows(int startRowIncl, int removedRowsCount, int addedRowsCount)
    {
        if (startRowIncl < currentKnownRows)
        {
            @SuppressWarnings("units")
            @TableDataRowIndex int difference = addedRowsCount - removedRowsCount;
            this.currentKnownRows += difference;
        }
        currentKnownRowsIsFinal = false;
        updateParent();
        onModify.run();
    }

    @Override
    public @OnThread(Tag.FXPlatform) void addedColumn(Column newColumn)
    {
        if (recordSet != null)
            setColumns(TableDisplayUtility.makeStableViewColumns(recordSet, table.getShowColumns(), c -> null, makeGetDataPosition(), onModify), table.getOperations(), c -> getColumnActions(parent.getManager(), getTable(), c));
    }

    @Override
    public @OnThread(Tag.FXPlatform) void removedColumn(ColumnId oldColumnId)
    {
        if (recordSet != null) 
            setColumns(TableDisplayUtility.makeStableViewColumns(recordSet, table.getShowColumns(), c -> null, makeGetDataPosition(), onModify), table.getOperations(), c -> getColumnActions(parent.getManager(), getTable(), c));
    }

    //TODO @Override
    protected @Nullable FXPlatformConsumer<ColumnId> hideColumnOperation()
    {
        return columnId -> {
            // Do null checks at run-time:
            if (table == null || columnDisplay == null || parent == null)
                return;
            switch (columnDisplay.get().getFirst())
            {
                case COLLAPSED:
                    // Leave it collapsed; not sure this can happen then anyway
                    break;
                case ALL:
                    // Hide just this one:
                    setDisplay(Display.CUSTOM, ImmutableList.of(columnId));
                    break;
                case ALTERED:
                    try
                    {
                        RecordSet data = table.getData();
                        List<ColumnId> alteredColumnNames = data.getColumns().stream().filter(c -> c.getAlteredState() != AlteredState.UNALTERED).<ColumnId>map(c -> c.getName()).collect(Collectors.<ColumnId>toList());
                        setDisplay(Display.CUSTOM, Utility.<ColumnId>prependToList(columnId, alteredColumnNames));
                    }
                    catch (UserException | InternalException e)
                    {
                        FXUtility.showError("Error hiding column", e);
                    }
                    break;
                case CUSTOM:
                    // Just tack this one on the blacklist:
                    setDisplay(Display.CUSTOM, Utility.prependToList(columnId, columnDisplay.get().getSecond()));
                    break;
            }
        };
    }

    @Override
    @SuppressWarnings("units")
    protected @TableDataRowIndex int getCurrentKnownRows()
    {
        return currentKnownRows + (displayColumns == null || displayColumns.isEmpty() ? 0 : getHeaderRowCount()) + (canExpandDown() ? 1 : 0);
    }

    @Override
    protected boolean canExpandDown()
    {
        return table.getOperations().appendRows != null;
    }

    @Override
    protected boolean canExpandRight()
    {
        return showAddColumnArrow(table);
    }

    private int internal_getColumnCount(@UnknownInitialization(DataDisplay.class) TableDisplay this, Table table)
    {
        return (displayColumns == null ? 0 : displayColumns.size()) + (showAddColumnArrow(table) ? 1 : 0);
    }

    private boolean showAddColumnArrow(@UnknownInitialization(DataDisplay.class) TableDisplay this, Table table)
    {
        return addColumnOperation(table) != null &&
            columnDisplay.get().getFirst() != Display.COLLAPSED;
    }

    @Override
    protected CellPosition recalculateBottomRightIncl()
    {
        if (columnDisplay.get().getFirst() == Display.COLLAPSED)
            return getPosition();
        else
            return getPosition().offsetByRowCols(Math.max(0, getCurrentKnownRows() - 1), Math.max(0, internal_getColumnCount(table) - 1));
    }

    // The last data row in grid area terms, not including any append buttons
    @SuppressWarnings("units")
    @OnThread(Tag.FXPlatform)
    public GridAreaCellPosition getDataDisplayBottomRightIncl(@UnknownInitialization(DataDisplay.class) TableDisplay this)
    {
        return new GridAreaCellPosition(Math.max(0, getHeaderRowCount() + (columnDisplay.get().getFirst() == Display.COLLAPSED ? 0 : currentKnownRows - 1)), Math.max(0, displayColumns == null ? 0 : (displayColumns.size() - 1)));
    }

    @Override
    protected void headerMiddleClicked()
    {
        // We cycle through ALL (incl CUSTOM) -> ALTERED -> COLLAPSED 
        // ALTERED is skipped if N/A.
        switch (columnDisplay.get().getFirst())
        {
            case COLLAPSED:
                setDisplay(columnDisplay.get().replaceFirst(Display.ALL));
                break;
            case ALTERED:
                setDisplay(columnDisplay.get().replaceFirst(Display.COLLAPSED));
                break;
            default:
                // If ALTERED is applicable, do that.  Otherwise go to COLLAPSED
                Display display = Display.COLLAPSED;
                if (recordSet != null)
                {
                    long countOfAltered = recordSet.getColumns().stream().filter(c -> c.getAlteredState() != AlteredState.UNALTERED).count();
                    // If all or none are altered, that's equivalent to collapsing
                    // or showing all, so just skip it for this cycling:
                    if (countOfAltered != 0 && countOfAltered != recordSet.getColumns().size())
                    {
                        display = Display.ALTERED;
                    }
                }
                setDisplay(columnDisplay.get().replaceFirst(display));
                break;
        }
    }

    @Override
    protected boolean isShowingRowLabels()
    {
        return true;
    }

    @Override
    public void doCopy(@Nullable RectangleBounds bounds)
    {
        Log.debug("Copying from " + bounds);
        int firstColumn = bounds == null ? 0 : Math.max(0, bounds.topLeftIncl.columnIndex - getPosition().columnIndex);
        if (firstColumn >= displayColumns.size())
            return; // No valid data to copy
        int lastColumnIncl = bounds == null ? displayColumns.size() - 1 : Math.min(displayColumns.size() - 1, bounds.bottomRightIncl.columnIndex - getPosition().columnIndex);
        if (lastColumnIncl < firstColumn)
            return; // No valid data range to copy

        final SimulationSupplier<RowRange> calcRowRange;
        if (bounds == null)
        {
            calcRowRange = new CompleteRowRangeSupplier();
        }
        else
        {
            @SuppressWarnings("units")
            @TableDataRowIndex int firstRowIncl = Math.max(0, bounds.topLeftIncl.rowIndex - (getPosition().rowIndex + getHeaderRowCount()));
            @SuppressWarnings("units")
            @TableDataRowIndex int lastRowIncl = Math.min(getBottomRightIncl().rowIndex, bounds.bottomRightIncl.rowIndex) - (getPosition().rowIndex + getHeaderRowCount());
            calcRowRange = () -> new RowRange(firstRowIncl, lastRowIncl);
        }
        
        try
        {
            List<Pair<ColumnId, DataTypeValue>> columns = Utility.mapListEx(displayColumns.subList(firstColumn, lastColumnIncl + 1), c -> new Pair<>(c.getColumnId(), c.getColumnType().fromCollapsed((i, prog) -> c.getColumnHandler().getValue(i))));

            ClipboardUtils.copyValuesToClipboard(parent.getManager().getUnitManager(), parent.getManager().getTypeManager(), columns, calcRowRange, null);
        }
        catch (UserException | InternalException e)
        {
            Log.log(e);
        }
    }

    @Override
    public void doDelete()
    {
        Workers.onWorkerThread("Deleting table", Priority.SAVE, () -> FXUtility.alertOnError_("Deleting table", () -> table.getManager().remove(table.getId())));
    }

    @Override
    public void setPosition(@UnknownInitialization(GridArea.class) TableDisplay this, CellPosition cellPosition)
    {
        super.setPosition(cellPosition);
        if (mostRecentBounds != null)
            updateMostRecentBounds();
    }
    
    /*
    @Override
    public int getFirstPossibleRowIncl()
    {
        return TableDisplay.this.getPosition().rowIndex;
    }

    @Override
    public int getLastPossibleRowIncl()
    {
        return TableDisplay.this.getPosition().rowIndex + 10; // TODO
    }

    @Override
    public int getFirstPossibleColumnIncl()
    {
        return TableDisplay.this.getPosition().columnIndex;
    }

    @Override
    public int getLastPossibleColumnIncl()
    {
        return TableDisplay.this.getPosition().columnIndex + displayColumns.size() - 1;
    }
    */
    
    @Override
    protected @Nullable FXPlatformConsumer<TableId> renameTableOperation(Table table)
    {
        @Nullable RenameTable renameTable = table.getOperations().renameTable;
        if (renameTable == null)
            return null;
        @NonNull RenameTable renameTableFinal = renameTable;
        
        return newTableId -> {
            if (Objects.equals(newTableId, curTableId))
                return; // Ignore if hasn't actually changed

            if (newTableId != null)
                Workers.onWorkerThread("Renaming table", Priority.SAVE, () -> renameTableFinal.renameTable(newTableId));

            if (newTableId != null)
                curTableId = newTableId;
        };
    }

    @OnThread(Tag.FXPlatform)
    public TableDisplay(View parent, VirtualGridSupplierFloating supplierFloating, Table table)
    {
        super(parent.getManager(), table, supplierFloating);
        this.parent = parent;
        this.table = table;
        this.curTableId = table.getId();
        @Nullable RecordSet recordSet = null;
        try
        {
            recordSet = table.getData();
        }
        catch (UserException | InternalException e)
        {
            Log.log(e);
            errorMessage.set(e);
        }
        // Crucial to set onModify before calling setupWithRecordSet:
        this.onModify = () -> {
            parent.modified();
            Workers.onWorkerThread("Updating dependents", Workers.Priority.FETCH, () -> FXUtility.alertOnError_("Error updating dependent transformations", () -> parent.getManager().<Table>edit(table.getId(), null, null)));
        };
        
        this.recordSet = recordSet;
        if (recordSet != null)
            setupWithRecordSet(parent.getManager(), table, recordSet);
        
        // Hat:
        if (table instanceof Transformation)
        {
            @UnknownInitialization(DataDisplay.class) TableDisplay us = this;
            @SuppressWarnings("initialization") // Don't understand why we need this here
            @Initialized TableHat hat = new TableHat(us, parent, (Transformation) table);
            this.tableHat = hat;
            supplierFloating.addItem(this.tableHat);
        }
        else
        {
            // No hat for plain data tables:
            tableHat = null;
        }
        // Error
        tableErrorDisplay = new TableErrorDisplay();
        supplierFloating.addItem(tableErrorDisplay);
        
        // Border overlay.  Note this makes use of calculations based on hat and row label border,
        // so it is important that we add this after them (since floating supplier iterates in order of addition):
        tableBorderOverlay = new TableBorderOverlay();
        supplierFloating.addItem(tableBorderOverlay);
        
        Label title = new Label(table.getId().getOutput());
        GUI.showTooltipWhenAbbrev(title);
        Utility.addStyleClass(title, "table-title");

        mostRecentBounds = new AtomicReference<>();
        updateMostRecentBounds();

        // Must be done as last item:
        @SuppressWarnings("initialization") @Initialized TableDisplay usInit = this;
        this.table.setDisplay(usInit);
    }

    public static @Nullable FXPlatformConsumer<TableId> renameTableSim(Table table)
    {
        @Nullable RenameTable renameTable = table.getOperations().renameTable;
        if (renameTable == null)
            return null;
        @NonNull RenameTable renameTableFinal = renameTable;
        return newName -> {
            Workers.onWorkerThread("Renaming table", Priority.SAVE, () -> renameTableFinal.renameTable(newName));
        };
    }
    
    @RequiresNonNull({"onModify", "parent"})
    private void setupWithRecordSet(@UnknownInitialization(DataDisplay.class) TableDisplay this, TableManager tableManager, Table table, RecordSet recordSet)
    {
        ImmutableList<ColumnDetails> displayColumns = TableDisplayUtility.makeStableViewColumns(recordSet, table.getShowColumns(), c -> null, makeGetDataPosition(), onModify);
        setColumns(displayColumns, table.getOperations(), c -> getColumnActions(tableManager, table, c));
        //TODO restore editability on/off
        //setEditable(getColumns().stream().anyMatch(TableColumn::isEditable));
        //boolean expandable = getColumns().stream().allMatch(TableColumn::isEditable);
        Workers.onWorkerThread("Determining row count", Priority.FETCH, () -> {
            ArrayList<Integer> indexesToAdd = new ArrayList<Integer>();
            watchForError_(() -> {
                for (int i = 0; i < INITIAL_LOAD; i++)
                {
                    if (recordSet.indexValid(i))
                    {
                        indexesToAdd.add(Integer.valueOf(i));
                    }
                    else if (i == 0 || recordSet.indexValid(i - 1))
                    {
                        // This is the first row after.  If all columns are editable,
                        // add a false row which indicates that the data can be expanded:
                        // TODO restore add-row
                        //if (expandable)
                        //indexesToAdd.add(Integer.valueOf(i == 0 ? Integer.MIN_VALUE : -i));
                    }
                }
            });
            // TODO when user causes a row to be shown, load LOAD_CHUNK entries
            // afterwards.
            //Platform.runLater(() -> getItems().addAll(indexesToAdd));
        });


        FXUtility.addChangeListenerPlatformNN(columnDisplay, newDisplay -> {
            setColumns(TableDisplayUtility.makeStableViewColumns(recordSet, newDisplay.mapSecond(blackList -> s -> !blackList.contains(s)), c -> null, makeGetDataPosition(), onModify), table.getOperations(), c -> getColumnActions(tableManager, table, c));
        });

        // Should be done last:
        @SuppressWarnings("initialization") @Initialized TableDisplay usInit = this;
        recordSet.setListener(usInit);
    }

    @OnThread(Tag.Simulation)
    @RequiresNonNull("errorMessage")
    private void watchForError_(@UnknownInitialization(DataDisplay.class) TableDisplay this, SimulationEx action)
    {
        try
        {
            action.run();
        }
        catch (UserException | InternalException e)
        {
            Log.log(e);
            Platform.runLater(() -> errorMessage.set(e));
        }
    }

    //@RequiresNonNull({"parent", "table"})
    //private GridArea makeErrorDisplay(@UnknownInitialization(Object.class) TableDisplay this, StyledString err)
    {
        /* TODO have a floating message with the below
        if (table instanceof TransformationEditable)
            return new VBox(new TextFlow(err.toGUI().toArray(new Node[0])), GUI.button("transformation.edit", () -> parent.editTransform((TransformationEditable)table)));
        else
            return new TextFlow(err.toGUI().toArray(new Node[0]));
            */
    }

    @RequiresNonNull({"mostRecentBounds"})
    private void updateMostRecentBounds(@UnknownInitialization(GridArea.class) TableDisplay this)
    {
        mostRecentBounds.set(getPosition());
    }

    @Override
    protected ContextMenu getTableHeaderContextMenu()
    {
        List<MenuItem> items = new ArrayList<>();

        if (table instanceof Transformation)
        {
            //items.add(GUI.menuItem("tableDisplay.menu.edit", () -> parent.editTransform((TransformationEditable)table)));
        }

        ToggleGroup show = new ToggleGroup();
        Map<Display, RadioMenuItem> showItems = new EnumMap<>(Display.class);

        showItems.put(Display.COLLAPSED, GUI.radioMenuItem("tableDisplay.menu.show.collapse", () -> setDisplay(Display.COLLAPSED, ImmutableList.of())));
        showItems.put(Display.ALL, GUI.radioMenuItem("tableDisplay.menu.show.all", () -> setDisplay(Display.ALL, ImmutableList.of())));
        showItems.put(Display.ALTERED, GUI.radioMenuItem("tableDisplay.menu.show.altered", () -> setDisplay(Display.ALTERED, ImmutableList.of())));
        showItems.put(Display.CUSTOM, GUI.radioMenuItem("tableDisplay.menu.show.custom", () -> editCustomDisplay()));

        items.addAll(Arrays.asList(
            GUI.menu("tableDisplay.menu.showColumns",
                GUI.radioMenuItems(show, showItems.values().toArray(new RadioMenuItem[0]))
            ),
            GUI.menuItem("tableDisplay.menu.copyValues", () -> FXUtility.alertOnErrorFX_("Error copying values", () -> ClipboardUtils.copyValuesToClipboard(parent.getManager().getUnitManager(), parent.getManager().getTypeManager(), Utility.mapListEx(table.getData().getColumns(), c -> new Pair<>(c.getName(), c.getType())), new CompleteRowRangeSupplier(), null))),
            GUI.menuItem("tableDisplay.menu.exportData", () -> {
                ExporterManager.getInstance().chooseAndExportFile(parent, table);
            }),
            GUI.menuItem("tableDisplay.menu.delete", () -> {
                Workers.onWorkerThread("Deleting " + table.getId(), Workers.Priority.SAVE, () ->
                    parent.getManager().remove(table.getId())
                );
            }),
            new SeparatorMenuItem(),
            GUI.menuItemPos("tableDisplay.menu.transform", pos -> {
                parent.createTransform(table, pos);
            })
        ));

        Optional.ofNullable(showItems.get(columnDisplay.get().getFirst())).ifPresent(show::selectToggle);
        
        return new ContextMenu(items.toArray(new MenuItem[0]));
    }

    @RequiresNonNull({"columnDisplay", "table", "parent"})
    private void editCustomDisplay(@UnknownInitialization(Object.class) TableDisplay this)
    {
        ImmutableList<ColumnId> blackList = new CustomColumnDisplayDialog(parent.getManager(), table.getId(), columnDisplay.get().getSecond()).showAndWait().orElse(null);
        // Only switch if they didn't cancel, otherwise use previous view mode:
        if (blackList != null)
            setDisplay(Display.CUSTOM, blackList);
    }

    @RequiresNonNull({"columnDisplay", "table", "parent"})
    private void setDisplay(@UnknownInitialization(Object.class) TableDisplay this, Pair<Display, ImmutableList<ColumnId>> newState)
    {
        setDisplay(newState.getFirst(), newState.getSecond());
    }

    @RequiresNonNull({"columnDisplay", "table", "parent"})
    private void setDisplay(@UnknownInitialization(Object.class) TableDisplay this, Display newState, ImmutableList<ColumnId> blackList)
    {
        this.columnDisplay.set(new Pair<>(newState, blackList));
        table.setShowColumns(newState, blackList);
        parent.modified();
    }

    @Override
    protected void tableDraggedToNewPosition()
    {
        super.tableDraggedToNewPosition();
        parent.modified();
    }
    
    @OnThread(Tag.FXPlatform)
    @Override
    public void loadPosition(CellPosition cellPosition, Pair<Display, ImmutableList<ColumnId>> display)
    {
        this.setPosition(cellPosition);
        this.columnDisplay.set(display);
    }

    @OnThread(Tag.Any)
    @Override
    public CellPosition getMostRecentPosition()
    {
        return mostRecentBounds.get();
    }

    @Override
    public @OnThread(Tag.FXPlatform) void promptForTransformationEdit(int index, Pair<ColumnId, DataType> column, Either<String, @Value Object> value)
    {
        Alert alert = new Alert(AlertType.CONFIRMATION, "Transformation results cannot be directly edited.  Add an edit transformation to allow editing of specific items?", ButtonType.YES, ButtonType.CANCEL);
        alert.setHeaderText("Create edit transformation?");
        alert.setTitle(alert.getHeaderText());
        alert.getDialogPane().lookupButton(ButtonType.YES).getStyleClass().add("yes-button");
        if (alert.showAndWait().equals(Optional.of(ButtonType.YES)))
        {
            CellPosition insertPos = parent.getManager().getNextInsertPosition(getTable().getId());
            Workers.onWorkerThread("Creating edit transformation", Priority.SAVE, () -> FXUtility.alertOnError_("Creating edit", () -> {
                @NonNull ManualEdit manualEdit = (ManualEdit)parent.getManager().edit(null, () -> new ManualEdit(parent.getManager(), new InitialLoadDetails(null, null, insertPos, null), getTable().getId(), null, ImmutableMap.of(column.getFirst(), new ColumnReplacementValues(column.getSecond(), ImmutableList.<Pair<@Value Object, Either<String, @Value Object>>>of(new Pair<@Value Object, Either<String, @Value Object>>(DataTypeUtility.value(index), value))))), null);
                Platform.runLater(() -> TableHat.editManualEdit(parent, manualEdit, true));
            }));
        }
    }

    @RequiresNonNull("parent")
    public ColumnHeaderOps getColumnActions(@UnknownInitialization(DataDisplay.class) TableDisplay this, TableManager tableManager, Table table, ColumnId c)
    {
        ImmutableList.Builder<FXPlatformSupplier<MenuItem>> r = ImmutableList.builder();

        TableOperations operations = table.getOperations();
        @Nullable FXPlatformConsumer<@Nullable ColumnId> addColumn = addColumnOperation(table);
        if (addColumn != null)
        {
            @NonNull FXPlatformConsumer<@Nullable ColumnId> addColumnFinal = addColumn;
            r.add(new ColumnOperation("virtGrid.column.addBefore")
            {
                @Override
                @OnThread(Tag.FXPlatform)
                public void executeFX()
                {
                    addColumnFinal.consume(c);
                }
            }::makeMenuItem);

            try
            {
                @OnThread(Tag.Any) List<Column> tableColumns = table.getData().getColumns();
                OptionalInt ourIndex = Utility.findFirstIndex(tableColumns, otherCol -> otherCol.getName().equals(c));
                if (ourIndex.isPresent())
                {
                    @Nullable ColumnId columnAfter = ourIndex.getAsInt() + 1 < tableColumns.size() ? tableColumns.get(ourIndex.getAsInt() + 1).getName() : null;
                    
                    r.add(new ColumnOperation("virtGrid.column.addAfter")
                    {
                        @Override
                        @OnThread(Tag.FXPlatform)
                        public void executeFX()
                        {
                            addColumnFinal.consume(columnAfter);
                        }
                    }::makeMenuItem);
                }
            }
            catch (UserException | InternalException e)
            {
                // If no data, just don't add this item
                Log.log(e);
            }
        }
        @Nullable DeleteColumn deleteColumn = operations.deleteColumn.apply(c);
        final @Nullable ColumnOperation deleteOp;
        if (deleteColumn != null)
        {
            deleteOp = new ColumnOperation("virtGrid.column.delete")
            {
                @Override
                public @OnThread(Tag.FXPlatform) void executeFX()
                {
                    Workers.onWorkerThread("Removing column", Priority.SAVE, () -> {
                        deleteColumn.deleteColumn(c);
                    });
                }
            };
            r.add(deleteOp::makeMenuItem);
        }
        else
            deleteOp = null;

        DataType type = null;
        try
        {
            DataTypeValue dataTypeValue = table.getData().getColumn(c).getType();
            type = dataTypeValue.getType();
            r.add(() -> new MenuItem("Recipes") {
                {
                    setDisable(true);
                    getStyleClass().add("recipe-header-menu-item");
                }
            });            
            
            if (DataTypeUtility.isNumber(type))
            {
                
                r.add(columnQuickTransform(tableManager, table, "recipe.sum", "Sum", c, (newId, insertPos) -> {
                    return new Aggregate(tableManager, new InitialLoadDetails(null, null, insertPos, null), table.getId(), ImmutableList.of(new Pair<>(newId, new CallExpression(FunctionList.getFunctionLookup(tableManager.getUnitManager()), Sum.NAME, new ColumnReference(c, ColumnReferenceType.WHOLE_COLUMN)))), ImmutableList.of());
                })::makeMenuItem);

                r.add(columnQuickTransform(tableManager, table, "recipe.average", "Average", c, (newId, insertPos) -> {
                    return new Aggregate(tableManager, new InitialLoadDetails(null, null, insertPos, null), table.getId(), ImmutableList.of(new Pair<>(newId, new CallExpression(FunctionList.getFunctionLookup(tableManager.getUnitManager()), Mean.NAME, new ColumnReference(c, ColumnReferenceType.WHOLE_COLUMN)))), ImmutableList.of());
                })::makeMenuItem);
            }
            r.add(columnQuickTransform(tableManager, table, "recipe.frequency", "Frequency", c, (newId, insertPos) -> {
                return new Aggregate(tableManager, new InitialLoadDetails(null, null, insertPos, null), table.getId(), ImmutableList.of(new Pair<>(newId, new IdentExpression(TypeState.GROUP_COUNT))), ImmutableList.of(c));
            })::makeMenuItem);
            r.add(columnQuickTransform(tableManager, table, "recipe.sort", insertPos -> {
                return new Sort(tableManager, new InitialLoadDetails(null, null, insertPos, null), table.getId(), ImmutableList.of(new Pair<>(c, Direction.ASCENDING)));
            })::makeMenuItem);
            r.add(transformType(tableManager, table, c, dataTypeValue, "recipe.transformType")::makeMenuItem);
        }
        catch (InternalException | UserException e)
        {
            Log.log(e);
        }

        @Nullable DataType typeFinal = type;
        ImmutableList<FXPlatformSupplier<MenuItem>> menuItemMakers = r.build();
        return new ColumnHeaderOps()
        {
            @Override
            @OnThread(Tag.FXPlatform)
            public ImmutableList<MenuItem> contextOperations()
            {
                return Utility.mapListI(menuItemMakers, maker -> maker.get());
            }

            @Override
            public @Nullable ColumnOperation getDeleteOperation()
            {
                return deleteOp;
            }

            @Override
            public @Nullable FXPlatformConsumer<EditTarget> getPrimaryEditOperation()
            {
                if (table instanceof Calculate)
                {
                    Calculate calc = (Calculate) table;
                    // Allow editing of any column:
                    return t -> FXUtility.alertOnErrorFX_("Error editing column", () -> {
                        TransformationEdits.editColumn_Calc(FXUtility.mouse(TableDisplay.this).parent, calc, c);
                    });
                }
                else if (table instanceof Aggregate)
                {
                    Aggregate aggregate = (Aggregate) table;
                    for (Pair<ColumnId, Expression> columnExpression : aggregate.getColumnExpressions())
                    {
                        if (columnExpression.getFirst().equals(c))
                        {
                            return t -> FXUtility.alertOnErrorFX_("Error editing column", () -> {
                                TransformationEdits.editColumn_Agg(FXUtility.mouse(TableDisplay.this).parent, aggregate, c);
                            });
                        }
                    }
                    return null;
                }
                else if (table instanceof ImmediateDataSource)
                {
                    ImmediateDataSource data = (ImmediateDataSource) table;
                    return t -> TransformationEdits.editColumn_IDS(FXUtility.mouse(TableDisplay.this).parent, data, c, typeFinal, t == EditTarget.EDIT_NAME ? InitialFocus.FOCUS_COLUMN_NAME : InitialFocus.FOCUS_TYPE);
                }
                
                return null;
            }
        };
    }

    @RequiresNonNull("parent")
    private ColumnOperation transformType(@UnknownInitialization(DataDisplay.class) TableDisplay this, TableManager tableManager, Table table, ColumnId columnId, DataTypeValue dataTypeValue, @LocalizableKey String nameKey)
    {
        return new SimpleColumnOperation(tableManager, table.getId(), nameKey)
        {
            @Override
            public @OnThread(Tag.Simulation) void execute(CellPosition insertPosition)
            {
                FXUtility.alertOnError_("Investigating type transformation", () -> {
                    // Check the type, then hop back to the FX thread for confirmation.
                    ImmutableList<TypeTransform> possibles = dataTypeValue.applyGet(new DataTypeVisitorGet<ImmutableList<TypeTransform>>()
                    {
                        @Override
                        public ImmutableList<TypeTransform> number(GetValue<@Value Number> g, NumberInfo displayInfo) throws InternalException, UserException
                        {
                            return ImmutableList.of(toText());
                        }

                        @Override
                        public ImmutableList<TypeTransform> text(GetValue<@Value String> g) throws InternalException, UserException
                        {
                            return ImmutableList.of(toText());
                        }

                        @Override
                        public ImmutableList<TypeTransform> bool(GetValue<@Value Boolean> g) throws InternalException, UserException
                        {
                            return ImmutableList.of(toText());
                        }

                        @Override
                        public ImmutableList<TypeTransform> date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> g) throws InternalException, UserException
                        {
                            return ImmutableList.of(toText());
                        }

                        @Override
                        public ImmutableList<TypeTransform> tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tagTypes, GetValue<@Value TaggedValue> g) throws InternalException, UserException
                        {
                            return ImmutableList.of(toText());
                        }

                        @Override
                        public ImmutableList<TypeTransform> record(ImmutableMap<@ExpressionIdentifier String, DataType> types, GetValue<@Value Record> g) throws InternalException, UserException
                        {
                            return ImmutableList.of(toText());
                        }

                        @Override
                        public ImmutableList<TypeTransform> array(DataType inner, GetValue<@Value ListEx> g) throws InternalException, UserException
                        {
                            return ImmutableList.of(toText());
                        }
                        
                        @SuppressWarnings("recorded")
                        private TypeTransform toText() throws InternalException
                        {
                            return new TypeTransform(new CallExpression(new StandardFunction(new ToString()), ImmutableList.of(new ColumnReference(columnId, ColumnReferenceType.CORRESPONDING_ROW))), DataType.TEXT);
                        }
                    });
                    
                    FXUtility.runFX(() -> {
                        if (possibles.isEmpty())
                            return;
                        new PickTypeTransformDialog(parent, possibles).showAndWait().ifPresent(tt -> {
                            CellPosition targetPos = tableManager.getNextInsertPosition(table.getId());
                            Workers.onWorkerThread("Creating type transformation", Priority.SAVE, () -> {
                                FXUtility.alertOnError_("Making type transformation", () -> {
                                    Calculate calculate = new Calculate(tableManager, new InitialLoadDetails(targetPos), table.getId(), ImmutableMap.of(columnId, tt.transformed));
                                    tableManager.record(calculate);
                                });
                            });
                        });
                    });
                });
            }
        };
    }

    public @Nullable FXPlatformConsumer<@Nullable ColumnId> addColumnOperation()
    {
        return addColumnOperation(table);
    }

    // If beforeColumn is null, add at end
    @OnThread(Tag.FXPlatform)
    private @Nullable FXPlatformConsumer<@Nullable ColumnId> addColumnOperation(@UnknownInitialization(DataDisplay.class) TableDisplay this, Table table)
    {
        if (table instanceof ImmediateDataSource)
        {
            @NonNull ImmediateDataSource ids = (ImmediateDataSource) table;
            return beforeColumn -> {
                FXUtility.mouse(this).addColumnBefore_IDS(ids, beforeColumn);
            };
        }
        else if (table instanceof Calculate)
        {
            Calculate calc = (Calculate) table;
            return beforeColumn -> {
                addColumnBefore_Calc(FXUtility.mouse(this).parent, calc, beforeColumn, null);
            };
        }
        else if (table instanceof Aggregate)
        {
            Aggregate agg = (Aggregate) table;
            return beforeColumn -> {
                FXUtility.mouse(this).addColumnBefore_Agg(agg, beforeColumn);
            };
        }
        return null;
    }
    
    void addColumnBefore_Calc(@UnknownInitialization(DataDisplay.class) TableDisplay this, View parent, Calculate calc, @Nullable ColumnId beforeColumn, @Nullable @LocalizableKey String topMessageKey)
    {
        EditColumnExpressionDialog<?> dialog = EditColumnExpressionDialog.withoutSidePane(parent, parent.getManager().getSingleTableOrNull(calc.getSrcTableId()), null, null, ed -> new MultipleTableLookup(calc.getId(), parent.getManager(), calc.getSrcTableId(), ed == null ? null : calc.makeEditor(ed)), () -> Calculate.makeTypeState(parent.getManager()), null);
        
        if (topMessageKey != null)
            dialog.addTopMessage(topMessageKey);
        
        dialog.showAndWait().ifPresent(p -> {
            Workers.onWorkerThread("Adding column", Priority.SAVE, () ->
                FXUtility.alertOnError_("Error adding column", () -> {
                    parent.getManager().edit(calc.getId(), () -> new Calculate(parent.getManager(), calc.getDetailsForCopy(),
                        calc.getSrcTableId(), Utility.appendToMap(calc.getCalculatedColumns(), p.columnId, p.expression, null)), null);
                })
            );
        });
    }

    private void addColumnBefore_Agg(Aggregate agg, @Nullable ColumnId beforeColumn)
    {
        EditColumnExpressionDialog<ImmutableList<ColumnId>> dialog = AggregateSplitByPane.editColumn(parent, parent.getManager().getSingleTableOrNull(agg.getSrcTableId()), null, null, _ed -> agg.getColumnLookup(), () -> Aggregate.makeTypeState(parent.getManager()), null, agg.getSplitBy());

        dialog.showAndWait().ifPresent(p -> {
            Workers.onWorkerThread("Adding column", Priority.SAVE, () ->
                    FXUtility.alertOnError_("Error adding column", () -> {
                        parent.getManager().edit(agg.getId(), () -> new Aggregate(parent.getManager(), agg.getDetailsForCopy(),
                                agg.getSrcTableId(), Utility.appendToList(agg.getColumnExpressions(), new Pair<>(p.columnId, p.expression)), p.extra), null);
                    })
            );
        });
    }

    public void addColumnBefore_IDS(ImmediateDataSource ids, @Nullable ColumnId beforeColumn)
    {
        Optional<EditImmediateColumnDialog.ColumnDetails> optInitialDetails = new EditImmediateColumnDialog(parent, parent.getManager(), null, null, false, InitialFocus.FOCUS_COLUMN_NAME).showAndWait();
        optInitialDetails.ifPresent(initialDetails -> Workers.onWorkerThread("Adding column", Priority.SAVE, () ->
            FXUtility.alertOnError_("Error adding column", () ->
                ids.getData().addColumn(beforeColumn, initialDetails.dataType.makeImmediateColumn(initialDetails.columnId, initialDetails.defaultValue))
            )
        ));
    }

    public void _test_collapseTableHat()
    {
        if (tableHat != null)
            tableHat.setCollapsed(true);
    }

    private static interface MakeColumnTransformation
    {
        @OnThread(Tag.Simulation)
        public Transformation makeTransform(ColumnId targetId, CellPosition targetPosition) throws InternalException, UserException;
    }

    private static interface MakeTableTransformation
    {
        @OnThread(Tag.Simulation)
        public Transformation makeTransform(CellPosition targetPosition) throws InternalException, UserException;
    }

    private ColumnOperation columnQuickTransform(@UnknownInitialization(DataDisplay.class) TableDisplay this, TableManager tableManager, Table us, @LocalizableKey String nameKey, String suggestedPrefix, ColumnId srcColumn, MakeColumnTransformation makeTransform) throws InternalException, UserException
    {
        @ExpressionIdentifier String stem = IdentifierUtility.fixExpressionIdentifier(suggestedPrefix + " " + srcColumn.getRaw(), srcColumn.getRaw());
        @ExpressionIdentifier String nextId = stem;
        for (int i = 1; i <= 1000; i++)
        {
            if (!us.getData().getColumnIds().contains(new ColumnId(nextId)))
                break;
            nextId = IdentifierUtility.identNum(stem, i);
        }
        // If we reach 999, just go with that and let user fix it
        ColumnId newColumnId = new ColumnId(nextId);
        
        return new SimpleColumnOperation(tableManager, us.getId(), nameKey)
        {
            @Override
            @OnThread(Tag.Simulation)
            public void execute(CellPosition insertPosition)
            {
                FXUtility.alertOnError_("Error adding column", () -> {
                    Transformation t = makeTransform.makeTransform(newColumnId, insertPosition);
                    tableManager.record(t);
                });
            }
        };
    }

    private ColumnOperation columnQuickTransform(@UnknownInitialization(DataDisplay.class) TableDisplay this, TableManager tableManager, Table us, @LocalizableKey String nameKey, MakeTableTransformation makeTransform)
    {
        return new SimpleColumnOperation(tableManager, us.getId(), nameKey)
        {
            @Override
            @OnThread(Tag.Simulation)
            public void execute(CellPosition insertPosition)
            {
                FXUtility.alertOnError_("Error adding column", () -> {
                    Transformation t = makeTransform.makeTransform(insertPosition);
                    tableManager.record(t);
                });
            }
        };
    }

    @Override
    public String toString()
    {
        return super.toString() + "[" + getTable().getId() + "]";
    }

    @Override
    public ContextMenu makeRowContextMenu(@TableDataRowIndex int row)
    {
        ContextMenu contextMenu = new ContextMenu();
        @Nullable InsertRows insertRows = table.getOperations().insertRows;
        if (insertRows != null)
        {
            @NonNull InsertRows insertRowsFinal = insertRows;
            @SuppressWarnings("units")
            @TableDataRowIndex final int ONE = 1;
            contextMenu.getItems().addAll(
                GUI.menuItem("virtGrid.row.insertBefore", () -> Workers.onWorkerThread("Inserting row", Priority.SAVE, () -> insertRowsFinal.insertRows(row, 1))),
                GUI.menuItem("virtGrid.row.insertAfter", () -> Workers.onWorkerThread("Inserting row", Priority.SAVE, () -> insertRowsFinal.insertRows(row + ONE, 1)))
            );
        }
        @Nullable DeleteRows deleteRows = table.getOperations().deleteRows;
        if (deleteRows != null)
        {
            @NonNull DeleteRows deleteRowsFinal = deleteRows;
            contextMenu.getItems().add(
                GUI.menuItem("virtGrid.row.delete", () -> Workers.onWorkerThread("Deleting row", Priority.SAVE, () -> deleteRowsFinal.deleteRows(row, 1)))
            );
        }
        return contextMenu;
    }

    @Override
    protected ImmutableList<String> getExtraTitleStyleClasses()
    {
        return table instanceof Transformation ? ImmutableList.of("transformation-table-title") : ImmutableList.of("immediate-table-title");
    }

    public int _test_getRowCount()
    {
        return currentKnownRows;
    }

    public void editAfterCreation()
    {
        if (table instanceof Calculate)
        {
            addColumnBefore_Calc(parent, (Calculate)table, null, "transform.calculate.addInitial");
        }
        else if (table instanceof Filter)
        {
            Filter filter = (Filter)table;
            new EditExpressionDialog(parent, 
                parent.getManager().getSingleTableOrNull(filter.getSrcTableId()),
                filter.getFilterExpression(), true,
                new MultipleTableLookup(filter.getId(), parent.getManager(), filter.getSrcTableId(), null),
                    () -> Filter.makeTypeState(parent.getManager().getTypeManager()),
                DataType.BOOLEAN, "filter.header").showAndWait().ifPresent(newExp -> Workers.onWorkerThread("Editing filter", Priority.SAVE, () ->  FXUtility.alertOnError_("Error editing filter", () -> 
            {
                
                    parent.getManager().edit(table.getId(), () -> new Filter(parent.getManager(),
                        table.getDetailsForCopy(), filter.getSrcTableId(), newExp), null);
            })));    
        }
        else if (table instanceof Aggregate)
        {
            Aggregate aggregate = (Aggregate)table;
            Optional<EditColumnExpressionDialog<ImmutableList<ColumnId>>.Result> result = AggregateSplitByPane.editColumn(parent, parent.getManager().getSingleTableOrNull(aggregate.getSrcTableId()), null, null, _ed -> aggregate.getColumnLookup(), () -> Aggregate.makeTypeState(parent.getManager()), null, aggregate.getSplitBy()).showAndWait();
            if (result.isPresent())
            {
                Workers.onWorkerThread("Adding column", Priority.SAVE, () -> {
                    try
                    {
                        parent.getManager().<Aggregate>edit(aggregate.getId(), () -> {
                            return new Aggregate(parent.getManager(), aggregate.getDetailsForCopy(), aggregate.getSrcTableId(), Utility.appendToList(aggregate.getColumnExpressions(), new Pair<>(result.get().columnId, result.get().expression)), result.get().extra);
                        }, null);
                    }
                    catch (InternalException e)
                    {
                        Log.log(e);
                    }
                });
            }
            else
            {
                Workers.onWorkerThread("Cancelling aggregate", Priority.SAVE, () -> {
                    parent.getManager().remove(aggregate.getId());
                });
            }
        }
        else if (table instanceof Sort)
        {
            TableHat.editSort(null, parent, (Sort)table);
        }
        else if (table instanceof ManualEdit)
        {
            TableHat.editManualEdit(parent, (ManualEdit)table, true);
        }
        else if (table instanceof Concatenate)
        {
            TableHat.editConcatenate(new Point2D(0, 0), parent, (Concatenate)table);
        }
        else if (table instanceof Join)
        {
            TableHat.editJoin(parent, (Join)table);
        }
        // For other tables, do nothing
    }

    private class CompleteRowRangeSupplier implements SimulationSupplier<RowRange>
    {
        @SuppressWarnings("units")
        @OnThread(Tag.Simulation)
        @Override
        public RowRange get() throws InternalException, UserException
        {
            return new RowRange(0, recordSet == null ? 0 :recordSet.getLength() - 1);
        }
    }

    private StyledString fixExpressionLink(EditableExpression fixer, @Nullable @LocalizableKey String headerKey)
    {
        return StyledString.styled("Edit expression", new Clickable() {
            @Override
            @OnThread(Tag.FXPlatform)
            protected void onClick(MouseButton mouseButton, Point2D screenPoint)
            {
                if (mouseButton == MouseButton.PRIMARY)
                {
                    new EditExpressionDialog(parent, fixer.srcTableId == null ? null : parent.getManager().getSingleTableOrNull(fixer.srcTableId), fixer.current, false, fixer.columnLookup, fixer.makeTypeState, fixer.expectedType, headerKey)
                            .showAndWait().ifPresent(newExp -> {
                        Workers.onWorkerThread("Editing table", Priority.SAVE, () ->
                                FXUtility.alertOnError_("Error applying fix", () ->
                                        parent.getManager().edit(table.getId(), () -> fixer.replaceExpression(newExp), null)
                                )
                        );
                    });
                }
            }
        });
    }
    
    public ImmutableList<?> getColumns()
    {
        return recordSet == null ? ImmutableList.of() : ImmutableList.copyOf(recordSet.getColumns());
    }
    
    @Override
    public String getSortKey()
    {
        // At least makes it consistent when ordering jumbled up tables during tests:
        return curTableId.getRaw();
    }

    @Override
    protected void deleteValue(ColumnId columnId, @TableDataRowIndex int rowIndex)
    {
        if (recordSet == null)
            return;
        
        try
        {
            Column column = recordSet.getColumn(columnId);
            @TableDataColIndex int colIndex = Utility.findFirstIndex(displayColumns, d -> d.getColumnId().equals(columnId)).orElse(-1) * TableDataColIndex.ONE;
            if (column != null && colIndex != -1)
            {
                @Value Object defaultValue = column.getDefaultValue();
                Workers.onWorkerThread("Getting default value", Priority.FETCH, () -> {
                    try
                    {
                        if (defaultValue != null)
                        {
                            @OnThread(Tag.Simulation) String string = DataTypeUtility.valueToString(column.getType().getType(), defaultValue, null);
                            Platform.runLater(() -> {
                                DataCellSupplier.@Nullable VersionedSTF cell = parent.getDataCellSupplier().getItemAt(getDataPosition(rowIndex, colIndex));
                                if (cell != null)
                                {
                                    cell.replaceAll(string, true);
                                }
                            });
                        }
                    }
                    catch (InternalException | UserException e)
                    {
                        Log.log(e);
                    }
                });
            }
        }
        catch (UserException e)
        {
            Log.log(e);
        }
    }

    @OnThread(Tag.FXPlatform)
    private class TableErrorDisplay extends FloatingItem<Pane>
    {
        private @Nullable BorderPane container;
        private @Nullable TextFlow textFlow;
        
        protected TableErrorDisplay()
        {
            super(ViewOrder.POPUP);
            FXUtility.addChangeListenerPlatform(errorMessage, err -> {
               withParent_(g -> g.positionOrAreaChanged()); 
            });
        }

        @Override
        protected Optional<BoundingBox> calculatePosition(VisibleBounds visibleBounds)
        {
            @Nullable ExceptionWithStyle err = errorMessage.get();
            if (err != null)
            {
                // We need a cell to do the calculation:
                if (textFlow == null || container == null)
                    makeCell(visibleBounds);
                final Pane containerFinal = container;
                final TextFlow textFlowFinal = textFlow;
                StyledString message = err.getStyledMessage();
                if (err instanceof ExpressionErrorException)
                {
                    ExpressionErrorException eee = (ExpressionErrorException) err;
                    message = StyledString.concat(message, StyledString.s("\n"), fixExpressionLink(eee.editableExpression, null));
                }
                textFlowFinal.getChildren().setAll(message.toGUI());
                containerFinal.applyCss();
                double x = 20 + visibleBounds.getXCoord(getPosition().columnIndex);
                double endX = -20 + visibleBounds.getXCoordAfter(getBottomRightIncl().columnIndex);
                double y = visibleBounds.getYCoord(getPosition().rowIndex + CellPosition.row(getHeaderRowCount()));
                double width = endX - x;
                double height = containerFinal.prefHeight(width);
                return Optional.of(new BoundingBox(x, y, width, height));
            }
            else
            {
                return Optional.empty();
            }
        }

        @Override
        @EnsuresNonNull({"container", "textFlow"})
        protected Pane makeCell(VisibleBounds visibleBounds)
        {
            TextFlow textFlow = new TextFlow();
            textFlow.getStyleClass().add("table-error-message-text-flow");
            BorderPane container = new BorderPane(textFlow);
            container.getStyleClass().add("table-error-message-container");
            this.container = container;
            this.textFlow = textFlow;
            return this.container;
        }

        @Override
        public @Nullable Pair<ItemState, @Nullable StyledString> getItemState(CellPosition cellPosition, Point2D screenPos)
        {
            if (container == null)
                return null;
            Bounds screenBounds = container.localToScreen(container.getBoundsInLocal());
            return screenBounds.contains(screenPos) ? new Pair<>(ItemState.DIRECTLY_CLICKABLE, null) : null;
        }

        @Override
        public void keyboardActivate(CellPosition cellPosition)
        {
            // Nothing to activate
        }
    }
}
