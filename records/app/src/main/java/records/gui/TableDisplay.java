package records.gui;

import annotation.units.GridAreaRowIndex;
import annotation.units.TableDataColIndex;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.DoubleExpression;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;
import log.Log;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import records.data.CellPosition;
import records.data.Column;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.RecordSet.RecordSetListener;
import records.data.Table.InitialLoadDetails;
import records.data.Table;
import records.data.Table.Display;
import records.data.Table.TableDisplayBase;
import records.data.TableId;
import records.data.TableManager;
import records.data.TableOperations;
import records.data.TableOperations.AddColumn;
import records.data.TableOperations.DeleteRows;
import records.data.TableOperations.InsertRows;
import records.data.TableOperations.RenameColumn;
import records.data.TableOperations.RenameTable;
import records.data.Transformation;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.error.ExceptionWithStyle;
import records.error.InternalException;
import records.error.UserException;
import records.errors.ExpressionErrorException;
import records.errors.ExpressionErrorException.EditableExpression;
import records.gui.DataCellSupplier.CellStyle;
import records.gui.grid.CellSelection;
import records.gui.grid.GridArea;
import records.gui.grid.GridAreaCellPosition;
import records.gui.grid.RectangleBounds;
import records.gui.grid.RectangleOverlayItem;
import records.gui.grid.RectangularTableCellSelection;
import records.gui.grid.VirtualGrid.ListenerOutcome;
import records.gui.grid.VirtualGridSupplier.ItemState;
import records.gui.grid.VirtualGridSupplier.ViewOrder;
import records.gui.grid.VirtualGridSupplier.VisibleBounds;
import records.gui.grid.VirtualGridSupplierFloating;
import records.gui.grid.VirtualGridSupplierFloating.FloatingItem;
import records.gui.grid.VirtualGridSupplierIndividual.GridCellInfo;
import records.gui.stable.ColumnOperation;
import records.gui.stable.ColumnDetails;
import records.gui.stf.EditorKitSimpleLabel;
import records.gui.stf.StructuredTextField;
import records.gui.stf.StructuredTextField.EditorKit;
import records.gui.stf.TableDisplayUtility;
import records.importers.ClipboardUtils;
import records.importers.ClipboardUtils.RowRange;
import records.transformations.Filter;
import records.transformations.HideColumnsPanel;
import records.transformations.SummaryStatistics;
import records.transformations.TransformationEditable;
import records.transformations.expression.CallExpression;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.Expression;
import styled.StyledString;
import styled.StyledString.Style;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.*;
import utility.Workers.Priority;
import utility.gui.FXUtility;
import utility.gui.GUI;
import utility.gui.ResizableRectangle;
import utility.gui.TranslationUtility;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
    private final ObjectProperty<Pair<Display, ImmutableList<ColumnId>>> columnDisplay = new SimpleObjectProperty<>(new Pair<>(Display.ALL, ImmutableList.of()));
    private final TableBorderOverlay tableBorderOverlay;
    private final TableRowLabelBorder rowLabelBorder;
    private final TableHat tableHat;
    private final TableErrorDisplay tableErrorDisplay;
    private boolean currentKnownRowsIsFinal = false;

    private final FXPlatformRunnable onModify;
    private final DoubleProperty slideOutProportion = new SimpleDoubleProperty(1.0);

    @OnThread(Tag.Any)
    public Table getTable()
    {
        return table;
    }

    public GridCellInfo<StructuredTextField, CellStyle> getDataGridCellInfo()
    {
        return new GridCellInfo<StructuredTextField, CellStyle>()
        {
            @Override
            public @Nullable GridAreaCellPosition cellAt(CellPosition cellPosition)
            {
                int tableDataRow = cellPosition.rowIndex - (getPosition().rowIndex + HEADER_ROWS);
                int tableDataColumn = cellPosition.columnIndex - getPosition().columnIndex;
                if (0 <= tableDataRow && tableDataRow < currentKnownRows &&
                    0 <= tableDataColumn && tableDataColumn < displayColumns.size())
                {
                    return GridAreaCellPosition.relativeFrom(cellPosition, getPosition());
                }
                else
                {
                    return null;
                }
            }

            @Override
            public void fetchFor(GridAreaCellPosition cellPosition, FXPlatformFunction<CellPosition, @Nullable StructuredTextField> getCell)
            {
                // Blank then queue fetch:
                StructuredTextField orig = getCell.apply(cellPosition.from(getPosition()));
                if (orig != null)
                    orig.resetContent(new EditorKitSimpleLabel<>(TranslationUtility.getString("data.loading")));
                @SuppressWarnings("units")
                @TableDataColIndex int columnIndexWithinTable = cellPosition.columnIndex;
                @SuppressWarnings("units")
                @TableDataRowIndex int rowIndexWithinTable = getRowIndexWithinTable(cellPosition.rowIndex);
                if (displayColumns != null && columnIndexWithinTable < displayColumns.size())
                {
                    displayColumns.get(columnIndexWithinTable).getColumnHandler().fetchValue(
                        rowIndexWithinTable,
                        b -> {},
                        c -> parent.getGrid().select(new RectangularTableCellSelection(c.rowIndex, c.columnIndex, dataSelectionLimits)),
                        (rowIndex, colIndex, editorKit) -> {
                            // The rowIndex and colIndex are in table data terms, so we must translate:
                            @Nullable StructuredTextField cell = getCell.apply(cellPosition.from(getPosition()));
                            if (cell != null)// && cell == orig)
                                cell.resetContent(editorKit);
                        }
                    );
                }
            }

            @Override
            public boolean checkCellUpToDate(GridAreaCellPosition cellPosition, StructuredTextField cellFirst)
            {
                // If we are for the right position, we haven't been scrolled out of view.
                // If the table is re-run, we'll get reset separately, so we are always up to date:
                return true;
            }

            @Override
            public ObjectExpression<? extends Collection<CellStyle>> styleForAllCells()
            {
                return cellStyles;
            }
        };
    }

    @Override
    public void cleanupFloatingItems()
    {
        super.cleanupFloatingItems();
        floatingItems.removeItem(rowLabelBorder);
        floatingItems.removeItem(tableHat);
        floatingItems.removeItem(tableErrorDisplay);
        floatingItems.removeItem(tableBorderOverlay);
    }

    @Override
    public @OnThread(Tag.FXPlatform) void updateKnownRows(@GridAreaRowIndex int checkUpToRowInclGrid, FXPlatformRunnable updateSizeAndPositions)
    {
        @TableDataRowIndex int checkUpToRowIncl = getRowIndexWithinTable(checkUpToRowInclGrid);
        if (!currentKnownRowsIsFinal && currentKnownRows < checkUpToRowIncl && recordSet != null)
        {
            final @NonNull RecordSet recordSetFinal = recordSet;
            Workers.onWorkerThread("Fetching row size", Priority.FETCH, () -> {
                try
                {
                    // Short-cut: check if the last index we are interested in has a row.  If so, can return early:
                    boolean lastRowValid = recordSetFinal.indexValid(checkUpToRowIncl);
                    if (lastRowValid)
                    {
                        Platform.runLater(() -> {
                            currentKnownRows = checkUpToRowIncl;
                            currentKnownRowsIsFinal = false;
                            updateSizeAndPositions.run();
                        });
                    } else
                    {
                        // Just a matter of working out where it ends.  Since we know end is close,
                        // just force with getLength:
                        @SuppressWarnings("units")
                        @TableDataRowIndex int length = recordSetFinal.getLength();
                        Platform.runLater(() -> {
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
        boolean includedInNew = newSelection == null ? false : newSelection.includes(this);
        boolean includedInOld = oldSelection == null ? false : oldSelection.includes(this);
        if (includedInNew != includedInOld)
        {
            slideOutProportion.set(includedInOld ? 1.0 : 0.0);
            new Timeline(new KeyFrame(Duration.millis(250), new KeyValue(slideOutProportion, 1.0 - slideOutProportion.get()))).playFromStart();
        }
        
        return new Pair<>(outcome, tableBorderOverlay::updateClip);
    }

    private CellPosition getDataPosition(@UnknownInitialization(GridArea.class) TableDisplay this, @TableDataRowIndex int rowIndex, @TableDataColIndex int columnIndex)
    {
        return getPosition().offsetByRowCols(getDataDisplayTopLeftIncl().rowIndex + rowIndex, getDataDisplayTopLeftIncl().columnIndex + columnIndex);
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
            setColumns(TableDisplayUtility.makeStableViewColumns(recordSet, table.getShowColumns(), this::renameColumn, this::getDataPosition, onModify), table.getOperations(), c -> getColumnActions(parent.getManager(), getTable(), c));
    }

    private @Nullable FXPlatformConsumer<ColumnId> renameColumn(ColumnId columnId)
    {
        return renameColumnForTable(getTable(), columnId);
    }
    
    private static @Nullable FXPlatformConsumer<ColumnId> renameColumnForTable(Table table, ColumnId columnId)
    {
        RenameColumn renameColumn = table.getOperations().renameColumn.apply(columnId);
        if (renameColumn == null)
            return null;
        final @NonNull RenameColumn renameColumnFinal = renameColumn;
        return newColumnId -> Workers.onWorkerThread("Renaming column", Priority.SAVE_ENTRY, () -> renameColumnFinal.renameColumn(newColumnId));
    }

    @Override
    public @OnThread(Tag.FXPlatform) void removedColumn(ColumnId oldColumnId)
    {
        if (recordSet != null) 
            setColumns(TableDisplayUtility.makeStableViewColumns(recordSet, table.getShowColumns(), this::renameColumn, this::getDataPosition, onModify), table.getOperations(), c -> getColumnActions(parent.getManager(), getTable(), c));
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
                        setDisplay(Display.CUSTOM, Utility.consList(columnId, data.getColumns().stream().filter(c -> c.isAltered()).map(c -> c.getName()).collect(Collectors.toList())));
                    }
                    catch (UserException | InternalException e)
                    {
                        FXUtility.showError(e);
                    }
                    break;
                case CUSTOM:
                    // Just tack this one on the blacklist:
                    setDisplay(Display.CUSTOM, Utility.consList(columnId, columnDisplay.get().getSecond()));
                    break;
            }
        };
    }

    private int internal_getCurrentKnownRows(@UnknownInitialization(DataDisplay.class) TableDisplay this, Table table)
    {
        return currentKnownRows + HEADER_ROWS + (table.getOperations().appendRows != null ? 1 : 0);
    }

    private int internal_getColumnCount(@UnknownInitialization(GridArea.class) TableDisplay this, Table table)
    {
        return (displayColumns == null ? 0 : displayColumns.size()) + (table.getOperations().addColumn != null ? 1 : 0);
    }

    @Override
    protected CellPosition recalculateBottomRightIncl()
    {
        return getPosition().offsetByRowCols(internal_getCurrentKnownRows(table) - 1, internal_getColumnCount(table) - 1);
    }

    @Override
    protected void doCopy(@Nullable RectangleBounds bounds)
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
            @TableDataRowIndex int firstRowIncl = Math.max(0, bounds.topLeftIncl.rowIndex - (getPosition().rowIndex + HEADER_ROWS));
            @SuppressWarnings("units")
            @TableDataRowIndex int lastRowIncl = Math.min(getBottomRightIncl().rowIndex, bounds.bottomRightIncl.rowIndex) - (getPosition().rowIndex + HEADER_ROWS);
            calcRowRange = () -> new RowRange(firstRowIncl, lastRowIncl);
        }
        
        try
        {
            List<Pair<ColumnId, DataTypeValue>> columns = Utility.mapListEx(displayColumns.subList(firstColumn, lastColumnIncl + 1), c -> new Pair<>(c.getColumnId(), c.getColumnType().fromCollapsed((i, prog) -> c.getColumnHandler().getValue(i))));

            ClipboardUtils.copyValuesToClipboard(parent.getManager().getUnitManager(), parent.getManager().getTypeManager(), columns, calcRowRange);
        }
        catch (UserException | InternalException e)
        {
            Log.log(e);
        }
    }

    @Override
    public void setPosition(CellPosition cellPosition)
    {
        super.setPosition(cellPosition);
        mostRecentBounds.set(cellPosition);
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

    @OnThread(Tag.FXPlatform)
    public TableDisplay(View parent, VirtualGridSupplierFloating supplierFloating, Table table)
    {
        super(parent.getManager(), table.getId(), table.getDisplayMessageWhenEmpty(), renameTableSim(table), supplierFloating);
        this.parent = parent;
        this.table = table;
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
        this.recordSet = recordSet;
        if (recordSet != null)
            setupWithRecordSet(parent.getManager(), table, recordSet);
        
        // Row label border:
        rowLabelBorder = new TableRowLabelBorder();
        supplierFloating.addItem(rowLabelBorder);
        // Hat:
        tableHat = new TableHat(table);
        supplierFloating.addItem(tableHat);
        // Error
        tableErrorDisplay = new TableErrorDisplay();
        supplierFloating.addItem(tableErrorDisplay);
        
        // Border overlay.  Note this makes use of calculations based on hat and row label border,
        // so it is important that we add this after them (since floating supplier iterates in order of addition):
        tableBorderOverlay = new TableBorderOverlay();
        supplierFloating.addItem(tableBorderOverlay);
        
        this.onModify = () -> {
            parent.modified();
            Workers.onWorkerThread("Updating dependents", Workers.Priority.FETCH, () -> FXUtility.alertOnError_(() -> parent.getManager().edit(table.getId(), null, null)));
        };
        
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
            Workers.onWorkerThread("Renaming table", Priority.SAVE_ENTRY, () -> renameTableFinal.renameTable(newName));
        };
    }

    private void setupWithRecordSet(@UnknownInitialization(DataDisplay.class) TableDisplay this, TableManager tableManager, Table table, RecordSet recordSet)
    {
        ImmutableList<ColumnDetails> displayColumns = TableDisplayUtility.makeStableViewColumns(recordSet, table.getShowColumns(), c -> renameColumnForTable(table, c), this::getDataPosition, onModify);
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
            setColumns(TableDisplayUtility.makeStableViewColumns(recordSet, newDisplay.mapSecond(blackList -> s -> !blackList.contains(s)), c -> renameColumnForTable(table, c), this::getDataPosition, onModify), table.getOperations(), c -> getColumnActions(tableManager, table, c));
        });

        // Should be done last:
        @SuppressWarnings("initialization") @Initialized TableDisplay usInit = this;
        recordSet.setListener(usInit);
    }

    @OnThread(Tag.Simulation)
    @RequiresNonNull("errorMessage")
    private void watchForError_(@UnknownInitialization(DataDisplay.class) TableDisplay this, SimulationRunnable action)
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
    private void updateMostRecentBounds(@UnknownInitialization(DataDisplay.class) TableDisplay this)
    {
        mostRecentBounds.set(getPosition());
    }

    protected ContextMenu getTableHeaderContextMenu()
    {
        List<MenuItem> items = new ArrayList<>();

        if (table instanceof TransformationEditable)
        {
            //items.add(GUI.menuItem("tableDisplay.menu.edit", () -> parent.editTransform((TransformationEditable)table)));
        }

        ToggleGroup show = new ToggleGroup();
        Map<Display, RadioMenuItem> showItems = new EnumMap<>(Display.class);

        showItems.put(Display.COLLAPSED, GUI.radioMenuItem("tableDisplay.menu.show.collapse", () -> setDisplay(Display.COLLAPSED, ImmutableList.of())));
        showItems.put(Display.ALL, GUI.radioMenuItem("tableDisplay.menu.show.all", () -> setDisplay(Display.ALL, ImmutableList.of())));
        showItems.put(Display.ALTERED, GUI.radioMenuItem("tableDisplay.menu.show.altered", () -> setDisplay(Display.ALTERED, ImmutableList.of())));
        showItems.put(Display.CUSTOM, GUI.radioMenuItem("tableDisplay.menu.show.custom", () -> editCustomDisplay()));

        Optional.ofNullable(showItems.get(columnDisplay.get().getFirst())).ifPresent(show::selectToggle);

        items.addAll(Arrays.asList(
            GUI.menu("tableDisplay.menu.showColumns",
                GUI.radioMenuItems(show, showItems.values().toArray(new RadioMenuItem[0]))
            ),
            GUI.menuItem("tableDisplay.menu.copyValues", () -> FXUtility.alertOnErrorFX_(() -> ClipboardUtils.copyValuesToClipboard(parent.getManager().getUnitManager(), parent.getManager().getTypeManager(), Utility.mapListEx(table.getData().getColumns(), c -> new Pair<>(c.getName(), c.getType())), new CompleteRowRangeSupplier()))),
            GUI.menuItem("tableDisplay.menu.exportToCSV", () -> {
                File file = FXUtility.getFileSaveAs(parent);
                if (file != null)
                {
                    final File fileNonNull = file;
                    Workers.onWorkerThread("Export to CSV", Workers.Priority.SAVE_TO_DISK, () -> FXUtility.alertOnError_(() -> exportToCSV(table, fileNonNull)));
                }
            }),
            GUI.menuItem("tableDisplay.menu.delete", () -> {
                Workers.onWorkerThread("Deleting " + table.getId(), Workers.Priority.SAVE_ENTRY, () ->
                    parent.getManager().remove(table.getId())
                );
            })
        ));
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
    private void setDisplay(@UnknownInitialization(Object.class) TableDisplay this, Display newState, ImmutableList<ColumnId> blackList)
    {
        this.columnDisplay.set(new Pair<>(newState, blackList));
        table.setShowColumns(newState, blackList);
        parent.modified();
    }

    @OnThread(Tag.Simulation)
    private static void exportToCSV(Table src, File dest) throws InternalException, UserException
    {
        // Write column names:
        try (BufferedWriter out = new BufferedWriter(new FileWriter(dest)))
        {
            @OnThread(Tag.Any) RecordSet data = src.getData();
            @OnThread(Tag.Any) List<Column> columns = data.getColumns();
            for (int i = 0; i < columns.size(); i++)
            {
                Column column = columns.get(i);
                out.write(quoteCSV(column.getName().getRaw()));
                if (i < columns.size() - 1)
                    out.write(",");
            }
            out.write("\n");
            for (int row = 0; data.indexValid(row); row += 1)
            {
                for (int i = 0; i < columns.size(); i++)
                {
                    out.write(quoteCSV(DataTypeUtility.valueToString(columns.get(i).getType(), columns.get(i).getType().getCollapsed(row), null)));
                    if (i < columns.size() - 1)
                        out.write(",");
                }
                out.write("\n");
            }
        }
        catch (IOException e)
        {
            throw new UserException("Problem writing to file: " + dest.getAbsolutePath(), e);
        }
    }

    @OnThread(Tag.Any)
    private static String quoteCSV(String original)
    {
        return "\"" + original.replace("\"", "\"\"\"") + "\"";
    }
    
    @OnThread(Tag.FXPlatform)
    @Override
    public void loadPosition(CellPosition cellPosition, Pair<Display, ImmutableList<ColumnId>> display)
    {
        mostRecentBounds.set(cellPosition);
        this.setPosition(cellPosition);
        this.columnDisplay.set(display);
    }

    @OnThread(Tag.Any)
    @Override
    public CellPosition getMostRecentPosition()
    {
        return mostRecentBounds.get();
    }

    public ImmutableList<records.gui.stable.ColumnOperation> getColumnActions(@UnknownInitialization(DataDisplay.class) TableDisplay this, TableManager tableManager, Table table, ColumnId c)
    {
        ImmutableList.Builder<ColumnOperation> r = ImmutableList.builder();

        TableOperations operations = table.getOperations();
        if (operations.addColumn != null)
        {
            @NonNull AddColumn addColumnFinal = operations.addColumn;
            r.add(new ColumnOperation("virtGrid.column.addBefore")
            {
                @Override
                @OnThread(Tag.Simulation)
                public void execute()
                {
                    addColumnFinal.addColumn(c, null, DataType.toInfer(), DataTypeUtility.value(""));
                }
            });

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
                        @OnThread(Tag.Simulation)
                        public void execute()
                        {
                            addColumnFinal.addColumn(columnAfter, null, DataType.toInfer(), DataTypeUtility.value(""));
                        }
                    });
                }
            }
            catch (UserException | InternalException e)
            {
                // If no data, just don't add this item
                Log.log(e);
            }
        }
        
        
        try
        {
            DataType type = table.getData().getColumn(c).getType();
            if (type.isNumber())
            {
                r.add(columnQuickTransform(tableManager, table, "recipe.sum", "sum", c, newId -> {
                    return new SummaryStatistics(tableManager, new InitialLoadDetails(null, null/*TODO*/, null), table.getId(), ImmutableList.of(new Pair<>(newId, new CallExpression(tableManager.getUnitManager(), "sum", new ColumnReference(c, ColumnReferenceType.WHOLE_COLUMN)))), ImmutableList.of());
                }));

                r.add(columnQuickTransform(tableManager, table, "recipe.average", "average", c, newId -> {
                    return new SummaryStatistics(tableManager, new InitialLoadDetails(null, null/*TODO*/, null), table.getId(), ImmutableList.of(new Pair<>(newId, new CallExpression(tableManager.getUnitManager(), "average", new ColumnReference(c, ColumnReferenceType.WHOLE_COLUMN)))), ImmutableList.of());
                }));
            }
        }
        catch (InternalException | UserException e)
        {
            Log.log(e);
        }
        return r.build();
    }
    
    private ColumnOperation columnQuickTransform(@UnknownInitialization(DataDisplay.class) TableDisplay this, TableManager tableManager, Table us, @LocalizableKey String nameKey, String suggestedPrefix, ColumnId srcColumn, SimulationFunction<ColumnId, Transformation> makeTransform) throws InternalException, UserException
    {
        String stem = suggestedPrefix + "." + srcColumn.getRaw();
        String nextId = stem;
        for (int i = 1; i <= 1000; i++)
        {
            if (!us.getData().getColumnIds().contains(new ColumnId(nextId)))
                break;
            nextId = stem + i;
        }
        // If we reach 999, just go with that and let user fix it
        ColumnId newColumnId = new ColumnId(nextId);
        
        return new ColumnOperation(nameKey)
        {
            @Override
            public @OnThread(Tag.Simulation) void execute()
            {
                FXUtility.alertOnError_(() -> {
                    Transformation t = makeTransform.apply(newColumnId);
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
                GUI.menuItem("virtGrid.row.insertBefore", () -> Workers.onWorkerThread("Inserting row", Priority.SAVE_ENTRY, () -> insertRowsFinal.insertRows(row, 1))),
                GUI.menuItem("virtGrid.row.insertAfter", () -> Workers.onWorkerThread("Inserting row", Priority.SAVE_ENTRY, () -> insertRowsFinal.insertRows(row + ONE, 1)))
            );
        }
        @Nullable DeleteRows deleteRows = table.getOperations().deleteRows;
        if (deleteRows != null)
        {
            @NonNull DeleteRows deleteRowsFinal = deleteRows;
            contextMenu.getItems().add(
                GUI.menuItem("virtGrid.row.delete", () -> Workers.onWorkerThread("Deleting row", Priority.SAVE_ENTRY, () -> deleteRowsFinal.deleteRows(row, 1)))
            );
        }
        return contextMenu;
    }

    public void setRowLabelBounds(Optional<BoundingBox> bounds)
    {
        rowLabelBorder.currentRowLabelBounds = bounds;
        rowLabelBorder.updateClip();
    }

    public DoubleExpression slideOutProperty()
    {
        return slideOutProportion;
    }

    @OnThread(Tag.FXPlatform)
    private static class CustomColumnDisplayDialog extends Dialog<ImmutableList<ColumnId>>
    {
        private final HideColumnsPanel hideColumnsPanel;

        public CustomColumnDisplayDialog(TableManager mgr, TableId tableId, ImmutableList<ColumnId> initialHidden)
        {
            this.hideColumnsPanel = new HideColumnsPanel(mgr, new ReadOnlyObjectWrapper<>(tableId), initialHidden);
            getDialogPane().getStylesheets().addAll(FXUtility.getSceneStylesheets());
            getDialogPane().setContent(hideColumnsPanel.getNode());
            getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
            setResultConverter(bt -> {
                if (bt == ButtonType.OK)
                    return hideColumnsPanel.getHiddenColumns();
                else
                    return null;
            });
        }
    }

    public static interface ValueLoadSave
    {
        @OnThread(Tag.FXPlatform)
        void fetchEditorKit(CellPosition cellPosition, FXPlatformConsumer<CellPosition> relinquishFocus, EditorKitCallback setEditorKit);
    }

    public interface EditorKitCallback
    {
        @OnThread(Tag.FXPlatform)
        public void loadedValue(int rowIndex, int colIndex, EditorKit<?> editorKit);
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
    
    private static class Clickable extends Style<Clickable>
    {
        private final FXPlatformConsumer<Point2D> onClick;
        
        public Clickable(FXPlatformConsumer<Point2D> onClick)
        {
            super(Clickable.class);
            this.onClick = onClick;
        }


        @Override
        @OnThread(Tag.FXPlatform)
        protected void style(Text t)
        {
            t.getStyleClass().add("styled-text-clickable");
            t.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY)
                    onClick.consume(new Point2D(e.getScreenX(), e.getScreenY()));
            });
            Tooltip tooltip = new Tooltip(TranslationUtility.getString("click.to.change"));
            Tooltip.install(t, tooltip);
        }

        @Override
        protected Clickable combine(Clickable with)
        {
            // Cannot combine, so make arbitrary choice:
            return this;
        }

        @Override
        protected boolean equalsStyle(Clickable item)
        {
            return false;
        }
    }
    
    private StyledString editSourceLink(TableId srcTableId, SimulationConsumer<TableId> changeSrcTableId)
    {
        return srcTableId.toStyledString().withStyle(new Clickable(p -> {
            new PickTableDialog(parent, p).showAndWait().ifPresent(t -> {
                Workers.onWorkerThread("Editing table source", Priority.SAVE_ENTRY, () -> FXUtility.alertOnError_(() -> changeSrcTableId.consume(t.getId())));
            });
        }));
    }

    private StyledString editExpressionLink(Expression curExpression, @Nullable Table srcTable, boolean perRow, @Nullable DataType expectedType, SimulationConsumer<Expression> changeExpression)
    {
        return curExpression.toStyledString().withStyle(new Clickable(p -> {
            new EditExpressionDialog(parent, srcTable, curExpression, perRow, expectedType).showAndWait().ifPresent(newExp -> {
                Workers.onWorkerThread("Editing table source", Priority.SAVE_ENTRY, () -> FXUtility.alertOnError_(() -> changeExpression.consume(newExp)));
            });
        }));
    }
    
    private StyledString fixExpressionLink(EditableExpression fixer)
    {
        return StyledString.styled("Edit expression", new Clickable(p -> {
            new EditExpressionDialog(parent, parent.getManager().getSingleTableOrNull(fixer.srcTableId), fixer.current, fixer.perRow, fixer.expectedType)
                .showAndWait().ifPresent(newExp -> {
                    Workers.onWorkerThread("Editing table", Priority.SAVE_ENTRY, () -> 
                        FXUtility.alertOnError_(() ->
                            parent.getManager().edit(table.getId(), () -> fixer.replaceExpression(newExp), null)
                        )
                    );
            });
                    
        }));
    }

    @OnThread(Tag.FXPlatform)
    private class TableHat extends FloatingItem<Node>
    {
        private final StyledString content;
        private BoundingBox latestBounds = new BoundingBox(0, 0, 0, 0);

        public TableHat(Table table)
        {
            super(ViewOrder.POPUP);
            if (table instanceof Filter)
            {
                Filter filter = (Filter)table;
                content = StyledString.concat(
                    StyledString.s("Filter "),
                    editSourceLink(filter.getSources().get(0), newSource -> 
                        parent.getManager().edit(table.getId(), () -> new Filter(parent.getManager(), 
                            table.getDetailsForCopy(), newSource, filter.getFilterExpression()), null)),
                    StyledString.s(", keeping rows where: "),
                    editExpressionLink(filter.getFilterExpression(), parent.getManager().getSingleTableOrNull(filter.getSources().get(0)), true, DataType.BOOLEAN, newExp ->
                        parent.getManager().edit(table.getId(), () -> new Filter(parent.getManager(),
                            table.getDetailsForCopy(), filter.getSources().get(0), newExp), null))
                );
            }
            else
            {
                content = StyledString.s("");
            }
        }

        @Override
        public Optional<BoundingBox> calculatePosition(VisibleBounds visibleBounds)
        {
            // The first time we are ever added, we will make a cell here and discard it,
            // but there's no good way round this:
            Node item = getNode() != null ? getNode() : makeCell(visibleBounds);
            
            double x = visibleBounds.getXCoord(getPosition().columnIndex);
            double width = visibleBounds.getXCoordAfter(getBottomRightIncl().columnIndex) - x;
            x += 30;
            width -= 40;

            double prefHeight = item.prefHeight(width);
            double y = Math.max(visibleBounds.getYCoord(getPosition().rowIndex) - 10 - prefHeight, visibleBounds.getYCoord(CellPosition.row(0)) + 10);
            latestBounds = new BoundingBox(
                x,
                y,
                width,
                prefHeight
            );
            return Optional.of(latestBounds);
        }

        @Override
        public Node makeCell(VisibleBounds visibleBounds)
        {
            TextFlow textFlow = new TextFlow(content.toGUI().toArray(new Node[0]));
            textFlow.getStyleClass().add("table-hat-text-flow");
            BorderPane borderPane = new BorderPane(textFlow, null, null, null, null);
            borderPane.getStyleClass().add("table-hat");
            return borderPane;
        }

        @Override
        public @Nullable ItemState getItemState(CellPosition cellPosition, Point2D screenPos)
        {
            Node node = getNode();
            if (node != null)
            {
                Bounds screenBounds = node.localToScreen(node.getBoundsInLocal());
                return screenBounds.contains(screenPos) ? ItemState.DIRECTLY_CLICKABLE : null;
            }
            return null;
        }
    }

    /**
     * The table border overlay.  It is a floating overlay because otherwise the drop shadow
     * doesn't work properly.
     */
    @OnThread(Tag.FXPlatform)
    private class TableBorderOverlay extends RectangleOverlayItem
    {
        public TableBorderOverlay()
        {
            super(ViewOrder.TABLE_BORDER);
        }

        @Override
        protected Optional<RectangleBounds> calculateBounds(VisibleBounds visibleBounds)
        {
            return visibleBounds.clampVisible(new RectangleBounds(
                    getPosition(),
                    getPosition().offsetByRowCols(internal_getCurrentKnownRows(table) - 1, internal_getColumnCount(table) - 1)
            ));
        }

        @Override
        protected void styleNewRectangle(Rectangle r, VisibleBounds visibleBounds)
        {
            r.getStyleClass().add("table-border-overlay");
            calcClip(r, visibleBounds);
        }

        @Override
        protected void sizesOrPositionsChanged(VisibleBounds visibleBounds)
        {
            updateClip(visibleBounds);
        }

        private void updateClip(VisibleBounds visibleBounds)
        {
            if (getNode() != null)
                calcClip(getNode(), visibleBounds);
        }

        @OnThread(Tag.FXPlatform)
        private void calcClip(Rectangle r, VisibleBounds visibleBounds)
        {
            Shape originalClip = Shape.subtract(
                new Rectangle(-20, -20, r.getWidth() + 40, r.getHeight() + 40),
                new Rectangle(0, 0, r.getWidth(), r.getHeight())
            );
            // We adjust clip if we have tables touching us:
            Shape clip = withParent(p -> {
                Shape curClip = originalClip;
                for (BoundingBox neighbour : p.getTouchingRectangles(TableDisplay.this))
                {
                    curClip = Shape.subtract(curClip, new Rectangle(neighbour.getMinX(), neighbour.getMinY(), neighbour.getWidth(), neighbour.getHeight()));
                }
                return curClip;
            }).orElse(originalClip);
            boolean rowLabelsShowing = withParent(p -> p.selectionIncludes(TableDisplay.this)).orElse(false);
            if (rowLabelsShowing)
            {
                @SuppressWarnings("units")
                @TableDataRowIndex int rowZero = 0;
                @SuppressWarnings("units")
                @TableDataColIndex int columnZero = 0;
                // We know where row labels will be, so easy to construct the rectangle:
                double rowStartY = visibleBounds.getYCoord(getDataPosition(rowZero, columnZero).rowIndex);
                double rowEndY = visibleBounds.getYCoord(getDataPosition(currentKnownRows, columnZero).rowIndex);
                double ourTopY = visibleBounds.getYCoord(getPosition().rowIndex);
                
                Rectangle rowLabelBounds = new Rectangle(-20, rowStartY - ourTopY, 20, rowEndY - rowStartY);
                clip = Shape.subtract(clip, rowLabelBounds);
            }

            r.setClip(clip);
            /* // Debug code to help see what clip looks like:
            if (getPosition().columnIndex == CellPosition.col(7))
            {
                // Hack to clone shape:
                Shape s = Shape.union(clip, clip);
                Scene scene = new Scene(new Group(s));
                ClipboardContent clipboardContent = new ClipboardContent();
                clipboardContent.putImage(s.snapshot(null, null));
                Clipboard.getSystemClipboard().setContent(clipboardContent);
            }
            */
        }
    }

    @OnThread(Tag.FXPlatform)
    private class TableRowLabelBorder extends FloatingItem<ResizableRectangle>
    {
        private Optional<BoundingBox> currentRowLabelBounds = Optional.empty();

        public TableRowLabelBorder()
        {
            super(ViewOrder.TABLE_BORDER);
            FXUtility.addChangeListenerPlatformNN(slideOutProportion, f -> {
                if (getNode() != null)
                {
                    double clippedWidth = (1.0 - f.doubleValue()) * getNode().getWidth();
                    getNode().translateXProperty().set(clippedWidth);
                    updateClip();
                }
            });
        }

        @Override
        protected Optional<BoundingBox> calculatePosition(VisibleBounds visibleBounds)
        {
            return currentRowLabelBounds;
        }

        @Override
        protected ResizableRectangle makeCell(VisibleBounds visibleBounds)
        {
            ResizableRectangle r = new ResizableRectangle();
            r.setMouseTransparent(true);
            r.getStyleClass().add("table-row-label-border");
            return r;
        }

        @Override
        public @Nullable ItemState getItemState(CellPosition cellPosition, Point2D screenPos)
        {
            return null;
        }

        @Override
        protected void sizesOrPositionsChanged(VisibleBounds visibleBounds)
        {
            updateClip();
        }

        public void updateClip(@UnknownInitialization(FloatingItem.class) TableRowLabelBorder this)
        {
            ResizableRectangle cur = getNode();
            if (cur != null && currentRowLabelBounds != null && currentRowLabelBounds.isPresent())
            {
                BoundingBox r = currentRowLabelBounds.get();
                double clippedWidth = slideOutProportion.get() * r.getWidth();
                // Outer (excluding right hand side) minus central inner:
                Shape clip = Shape.subtract(
                    new Rectangle(-20, -20, clippedWidth + 20, r.getHeight() + 40),
                    new Rectangle(0, 0, clippedWidth, r.getHeight())
                );
                cur.setClip(clip);
            }
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
                    message = StyledString.concat(message, StyledString.s("\n"), fixExpressionLink(eee.editableExpression));
                }
                textFlowFinal.getChildren().setAll(message.toGUI());
                containerFinal.applyCss();
                double x = 20 + visibleBounds.getXCoord(getPosition().columnIndex);
                double endX = -20 + visibleBounds.getXCoordAfter(getBottomRightIncl().columnIndex);
                double y = visibleBounds.getYCoord(getPosition().rowIndex + CellPosition.row(HEADER_ROWS));
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
        public @Nullable ItemState getItemState(CellPosition cellPosition, Point2D screenPos)
        {
            if (container == null)
                return null;
            Bounds screenBounds = container.localToScreen(container.getBoundsInLocal());
            return screenBounds.contains(screenPos) ? ItemState.DIRECTLY_CLICKABLE : null;
        }
    }
}
