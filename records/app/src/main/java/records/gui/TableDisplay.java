package records.gui;

import annotation.units.GridAreaRowIndex;
import annotation.units.TableDataColIndex;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import javafx.application.Platform;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeType;
import log.Log;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import records.data.CellPosition;
import records.data.Column;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.RecordSet.RecordSetListener;
import records.data.Table.InitialLoadDetails;
import records.data.TableAndColumnRenames;
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
import records.error.InternalException;
import records.error.UserException;
import records.gui.DataCellSupplier.CellStyle;
import records.gui.grid.GridArea;
import records.gui.grid.GridAreaCellPosition;
import records.gui.grid.RectangleBounds;
import records.gui.grid.RectangleOverlayItem;
import records.gui.grid.RectangularTableCellSelection;
import records.gui.grid.VirtualGridSupplier.ViewOrder;
import records.gui.grid.VirtualGridSupplier.VisibleDetails;
import records.gui.grid.VirtualGridSupplierFloating;
import records.gui.grid.VirtualGridSupplierIndividual.GridCellInfo;
import records.gui.stable.ColumnOperation;
import records.gui.stable.ColumnDetails;
import records.gui.stf.EditorKitSimpleLabel;
import records.gui.stf.StructuredTextField;
import records.gui.stf.StructuredTextField.EditorKit;
import records.gui.stf.TableDisplayUtility;
import records.importers.ClipboardUtils;
import records.importers.ClipboardUtils.RowRange;
import records.transformations.HideColumnsPanel;
import records.transformations.SummaryStatistics;
import records.transformations.TransformationEditable;
import records.transformations.expression.CallExpression;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.FXPlatformFunction;
import utility.FXPlatformRunnable;
import utility.Pair;
import utility.SimulationFunction;
import utility.SimulationSupplier;
import utility.Utility;
import utility.Workers;
import utility.Workers.Priority;
import utility.gui.FXUtility;
import utility.gui.GUI;
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
    @OnThread(Tag.Any)
    private final Either<StyledString, RecordSet> recordSetOrError;
    private final Table table;
    private final View parent;
    @OnThread(Tag.Any)
    private final AtomicReference<CellPosition> mostRecentBounds;
    private final ObjectProperty<Pair<Display, ImmutableList<ColumnId>>> columnDisplay = new SimpleObjectProperty<>(new Pair<>(Display.ALL, ImmutableList.of()));
    private boolean currentKnownRowsIsFinal = false;

    private final FXPlatformRunnable onModify;
    

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
    public @OnThread(Tag.FXPlatform) void updateKnownRows(@GridAreaRowIndex int checkUpToRowInclGrid, FXPlatformRunnable updateSizeAndPositions)
    {
        @TableDataRowIndex int checkUpToRowIncl = getRowIndexWithinTable(checkUpToRowInclGrid);
        if (!currentKnownRowsIsFinal && currentKnownRows < checkUpToRowIncl && recordSetOrError.isRight())
        {
            Workers.onWorkerThread("Fetching row size", Priority.FETCH, () -> {
                try
                {
                    // Short-cut: check if the last index we are interested in has a row.  If so, can return early:
                    boolean lastRowValid = recordSetOrError.getRight().indexValid(checkUpToRowIncl);
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
                        @TableDataRowIndex int length = recordSetOrError.getRight().getLength();
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
        recordSetOrError.ifRight(recordSet ->
            setColumns(TableDisplayUtility.makeStableViewColumns(recordSet, table.getShowColumns(), this::renameColumn, this::getDataPosition, onModify), table.getOperations(), c -> getColumnActions(parent.getManager(), getTable(), c))
        );
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
        recordSetOrError.ifRight(recordSet -> 
            setColumns(TableDisplayUtility.makeStableViewColumns(recordSet, table.getShowColumns(), this::renameColumn, this::getDataPosition, onModify), table.getOperations(), c -> getColumnActions(parent.getManager(), getTable(), c))
        );
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
    public TableDisplay(View parent, VirtualGridSupplierFloating columnHeaderSupplier, Table table)
    {
        super(parent.getManager(), table.getId(), table.getDisplayMessageWhenEmpty(), renameTableSim(table), columnHeaderSupplier);
        this.parent = parent;
        this.table = table;
        Either<StyledString, RecordSet> recordSetOrError;
        try
        {
            recordSetOrError = Either.right(table.getData());
        }
        catch (UserException | InternalException e)
        {
            recordSetOrError = Either.left(e.getStyledMessage());
        }
        this.recordSetOrError = recordSetOrError;
        recordSetOrError.ifRight(rs -> setupWithRecordSet(parent.getManager(), table, rs));
        
        // Border overlay:
        columnHeaderSupplier.addItem(new RectangleOverlayItem(ViewOrder.OVERLAY_PASSIVE)
        {
            @Override
            protected Optional<RectangleBounds> calculateBounds(VisibleDetails rowBounds, VisibleDetails columnBounds)
            {
                return Optional.of(new RectangleBounds(
                        getPosition(),
                        getPosition().offsetByRowCols(internal_getCurrentKnownRows(table) - 1, internal_getColumnCount(table) - 1)
                ));
            }

            @Override
            protected void style(Rectangle r)
            {
                r.getStyleClass().add("table-border-overlay");
                Rectangle clip = new Rectangle();
                clip.setStrokeType(StrokeType.OUTSIDE);
                clip.setStrokeWidth(20.0);
                clip.setFill(Color.TRANSPARENT);
                clip.setStroke(Color.BLACK);
                clip.widthProperty().bind(r.widthProperty());
                clip.heightProperty().bind(r.heightProperty());
                r.clipProperty().set(clip);
                // TODO we should adjust clip if there are tables touching us
            }
        });
        this.onModify = () -> {
            parent.modified();
            Workers.onWorkerThread("Updating dependents", Workers.Priority.FETCH, () -> FXUtility.alertOnError_(() -> parent.getManager().edit(table.getId(), null, TableAndColumnRenames.EMPTY)));
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
            FXUtility.alertOnError_(() -> {
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
            items.add(GUI.menuItem("tableDisplay.menu.edit", () -> parent.editTransform((TransformationEditable)table)));
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
            GUI.menuItem("tableDisplay.menu.addTransformation", () -> parent.newTransformFromSrc(table)),
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
            return new RowRange(0, recordSetOrError.<Integer>eitherEx(err -> 0, rs -> rs.getLength()));
        }
    }
}
