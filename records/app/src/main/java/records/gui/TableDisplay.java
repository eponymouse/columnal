package records.gui;

import com.google.common.collect.ImmutableList;
import javafx.application.Platform;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeType;
import log.Log;
import org.checkerframework.checker.guieffect.qual.UIEffect;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import records.data.CellPosition;
import records.data.Column;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.RecordSet.RecordSetListener;
import records.data.Table;
import records.data.Table.Display;
import records.data.Table.MessageWhenEmpty;
import records.data.Table.TableDisplayBase;
import records.data.TableId;
import records.data.TableManager;
import records.data.TableOperations.AppendColumn;
import records.data.Transformation;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import records.gui.DataCellSupplier.CellStyle;
import records.gui.grid.GridArea;
import records.gui.grid.RectangularTableCellSelection;
import records.gui.grid.RectangularTableCellSelection.TableSelectionLimits;
import records.gui.grid.VirtualGrid;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * A pane which displays a table.  This includes the faux title bar
 * of the table which you can use to drag it around, the buttons in
 * the title bar for manipulation, and the display of the data itself.
 *
 * Primarily, this class is responsible for the moving and resizing within
 * the display.  The data is handled by the inner class TableDataDisplay.
 */
@OnThread(Tag.FXPlatform)
public class TableDisplay implements TableDisplayBase
{
    private static final int INITIAL_LOAD = 100;
    private static final int LOAD_CHUNK = 100;
    private final Either<StyledString, RecordSet> recordSetOrError;
    private final Table table;
    private final View parent;
    private final TableDataDisplay tableDataDisplay;
    @OnThread(Tag.Any)
    private final AtomicReference<CellPosition> mostRecentBounds;
    private final HBox header;
    private final ObjectProperty<Pair<Display, ImmutableList<ColumnId>>> columnDisplay = new SimpleObjectProperty<>(new Pair<>(Display.ALL, ImmutableList.of()));
    private int currentKnownRows = 0;
    private boolean currentKnownRowsIsFinal = false;// Table title, column title, column type
    

    /**
     * Finds the closest point on the edge of this rectangle to the given point.
     * 
     * If the point is inside our rectangular bounds, still locates a point on our edge.
     */
    /*TODO
    @OnThread(Tag.FX)
    public Point2D closestPointTo(double parentX, double parentY)
    {
        Bounds boundsInParent = getBoundsInParent();
        Point2D middle = Utility.middle(boundsInParent);
        double angle = Math.atan2(parentY - middle.getY(), parentX - middle.getX());
        // The line outwards may hit the horizontal edges, or the vertical edges
        // Rather than work out which it is hitting, we just calculate both
        // possibilities and take the minimum.

        // Hitting the horizontal edge is tan(angle) * halfWidth
        // Hitting vertical edge is

        double halfWidth = boundsInParent.getWidth() * 0.5;
        double halfHeight = boundsInParent.getHeight() * 0.5;
        double deg90 = Math.toRadians(90);

        return new Point2D(middle.getX() + Math.max(-halfWidth, Math.min(1.0/Math.tan(angle) * ((angle >= 0) ? halfHeight : -halfHeight), halfWidth)),
            middle.getY() + Math.max(-halfHeight, Math.min(Math.tan(angle) * ((angle >= deg90 || angle <= -deg90) ? -halfWidth : halfWidth), halfHeight)));
    }
    */

    /**
     * Treating the given bounds as a simple rectangle, finds the point
     * on the edge of our bounds and the edge of their bounds
     * that give the minimum distance between the two points. 
     * 
     * @return The pair of (point-on-our-bounds, point-on-their-bounds)
     * that give shortest bounds between the two.
     */
    /*TODO
    @OnThread(Tag.FXPlatform)
    public Pair<Point2D, Point2D> closestPointTo(Bounds them)
    {
        // If we overlap in horizontal or vertical line, then
        // the minimum distance is automatically along that line
        // or shortest of the two.  Otherwise the shortest line
        // connects two of the corners.

        Bounds us = getBoundsInParent();
        // Pairs distance with the points:
        List<Pair<Double, Pair<Point2D, Point2D>>> candidates = new ArrayList<>(); 
        if (us.getMinX() <= them.getMaxX() && them.getMinX() <= us.getMaxX())
        {
            // Overlap horizontally
            double midOverlapX = 0.5 * (Math.max(us.getMinX(), them.getMinX()) + Math.min(us.getMaxX(), them.getMaxX()));
            
            // Four options: our top/bottom, combined with their top/bottom
            for (double theirY : Arrays.asList(them.getMinY(), them.getMaxY()))
            {
                for (double usY : Arrays.asList(us.getMinY(), us.getMaxY()))
                {
                    candidates.add(new Pair<>(Math.abs(usY - theirY), new Pair<>(
                        new Point2D(midOverlapX, usY), new Point2D(midOverlapX, theirY)
                    )));
                }
            }
        }
        
        //vertical overlap:
        if (us.getMinY() <= them.getMaxY() && them.getMinY() <= us.getMaxY())
        {
            double midOverlapY = 0.5 * (Math.max(us.getMinY(), them.getMinY()) + Math.min(us.getMaxY(), them.getMaxY()));
            
            // Four options: our left/right, combined with their left/right
            for (double theirX : Arrays.asList(them.getMinX(), them.getMaxX()))
            {
                for (double usX : Arrays.asList(us.getMinX(), us.getMaxX()))
                {
                    candidates.add(new Pair<>(Math.abs(usX - theirX), new Pair<>(
                            new Point2D(usX, midOverlapY), new Point2D(theirX, midOverlapY)
                    )));
                }

            }
        }
        
        if (candidates.isEmpty())
        {
            // No overlap in either dimension, do corners:
            for (double theirY : Arrays.asList(them.getMinY(), them.getMaxY()))
            {
                for (double usY : Arrays.asList(us.getMinY(), us.getMaxY()))
                {
                    for (double theirX : Arrays.asList(them.getMinX(), them.getMaxX()))
                    {
                        for (double usX : Arrays.asList(us.getMinX(), us.getMaxX()))
                        {
                            candidates.add(new Pair<>(Math.hypot(theirX - usX, theirY - usY), new Pair<>(new Point2D(usX, usY), new Point2D(theirX, theirY))));
                        }

                    }
                }
            }
        }

        return candidates.stream().min(Pair.<Double, Pair<Point2D, Point2D>>comparatorFirst()).orElseThrow(() -> new RuntimeException("Impossible!")).getSecond();
    }
    */

    @OnThread(Tag.Any)
    public Table getTable()
    {
        return table;
    }

    public VirtualGrid _test_getGrid()
    {
        if (tableDataDisplay != null)
            return tableDataDisplay._test_getParent();
        else
            throw new RuntimeException("Table "+ getTable().getId() + " is empty");
    }

    public GridArea getGridArea()
    {
        return tableDataDisplay;
    }

    public GridCellInfo<StructuredTextField, CellStyle> getDataGridCellInfo()
    {
        return tableDataDisplay.getDataGridCellInfo();
    }

    @OnThread(Tag.FXPlatform)
    public int getFirstDataDisplayRowIncl()
    {
        return tableDataDisplay.getPosition().rowIndex + DataDisplay.HEADER_ROWS;
    }

    @OnThread(Tag.FXPlatform)
    public int getLastDataDisplayRowIncl()
    {
        return tableDataDisplay.getPosition().rowIndex + DataDisplay.HEADER_ROWS + currentKnownRows - 1;
    }

    @OnThread(Tag.FXPlatform)
    public int getFirstDataDisplayColumnIncl()
    {
        return tableDataDisplay.getPosition().columnIndex;
    }

    @OnThread(Tag.FXPlatform)
    public int getLastDataDisplayColumnIncl()
    {
        return tableDataDisplay.getPosition().columnIndex + tableDataDisplay.displayColumns.size() - 1;
    }

    public void addCellStyle(CellStyle cellStyle)
    {
        tableDataDisplay.addCellStyle(cellStyle);
    }

    public void removeCellStyle(CellStyle cellStyle)
    {
        tableDataDisplay.removeCellStyle(cellStyle);
    }

    @OnThread(Tag.FXPlatform)
    private class TableDataDisplay extends DataDisplay implements RecordSetListener
    {
        private final FXPlatformRunnable onModify;
        private final RecordSet recordSet;
        private final RectangularTableCellSelection.TableSelectionLimits dataSelectionLimits;

        @SuppressWarnings("initialization")
        @UIEffect
        public TableDataDisplay(TableManager tableManager, Pair<@Nullable RecordSet, MessageWhenEmpty> recordSetMessageWhenEmptyPair, VirtualGridSupplierFloating columnHeaderSupplier, FXPlatformRunnable onModify)
        {
            super(tableManager, getTable().getId(), recordSetMessageWhenEmptyPair.getSecond(), columnHeaderSupplier);
            // Border overlay:
            columnHeaderSupplier.addItem(new RectangleOverlayItem()
            {
                @Override
                protected Optional<RectangleBounds> calculateBounds(VisibleDetails rowBounds, VisibleDetails columnBounds)
                {
                    return Optional.of(new RectangleBounds(
                        getPosition(),
                        getPosition().offsetByRowCols(getCurrentKnownRows() - 1, getColumnCount() - 1)    
                    ));
                }

                @Override
                protected void style(Rectangle r)
                {
                    r.getStyleClass().add("table-border-overlay");
                    Rectangle clip = new Rectangle();
                    clip.setStrokeType(StrokeType.OUTSIDE);
                    clip.setStrokeWidth(20.0);
                    clip.setFill(null);
                    clip.setStroke(Color.BLACK);
                    clip.widthProperty().bind(r.widthProperty());
                    clip.heightProperty().bind(r.heightProperty());
                    r.clipProperty().set(clip);
                    // TODO we should adjust clip if there are tables touching us
                }
            });
            this.recordSet = recordSetMessageWhenEmptyPair.getFirst();
            this.onModify = onModify;
            recordSet.setListener(this);
            ImmutableList<ColumnDetails> displayColumns = TableDisplayUtility.makeStableViewColumns(recordSet, table.getShowColumns(), onModify);
            setColumnsAndRows(displayColumns, table.getOperations(), c -> getExtraColumnActions(c));
            //TODO restore editability
            //setEditable(getColumns().stream().anyMatch(TableColumn::isEditable));
            //boolean expandable = getColumns().stream().allMatch(TableColumn::isEditable);
            Workers.onWorkerThread("Determining row count", Workers.Priority.FETCH, () -> {
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
                setColumnsAndRows(TableDisplayUtility.makeStableViewColumns(recordSet, newDisplay.mapSecond(blackList -> s -> !blackList.contains(s)), onModify), table.getOperations(), c -> getExtraColumnActions(c));
            });
            
            this.dataSelectionLimits = new TableSelectionLimits()
            {
                //TODO
                @Override
                public int getFirstPossibleRowIncl()
                {
                    return 0;
                }

                @Override
                public int getLastPossibleRowIncl()
                {
                    return 0;
                }

                @Override
                public int getFirstPossibleColumnIncl()
                {
                    return 0;
                }

                @Override
                public int getLastPossibleColumnIncl()
                {
                    return 0;
                }
            };
        }


        public GridCellInfo<StructuredTextField, CellStyle> getDataGridCellInfo()
        {
            return new GridCellInfo<StructuredTextField, CellStyle>()
            {
                @Override
                public boolean hasCellAt(CellPosition cellPosition)
                {
                    return cellPosition.columnIndex >= getPosition().columnIndex
                        && cellPosition.columnIndex < getPosition().columnIndex + displayColumns.size()
                        && cellPosition.rowIndex >= getPosition().rowIndex + HEADER_ROWS
                        && cellPosition.rowIndex < getPosition().rowIndex + HEADER_ROWS + currentKnownRows;
                }

                @Override
                public void fetchFor(CellPosition cellPosition, FXPlatformFunction<CellPosition, @Nullable StructuredTextField> getCell)
                {
                    // Blank then queue fetch:
                    StructuredTextField orig = getCell.apply(cellPosition);
                    if (orig != null)
                        orig.resetContent(new EditorKitSimpleLabel<>(TranslationUtility.getString("data.loading")));
                    int columnIndexWithinTable = cellPosition.columnIndex - getPosition().columnIndex;
                    int rowIndexWithinTable = cellPosition.rowIndex - (getPosition().rowIndex + HEADER_ROWS);
                    if (displayColumns != null && columnIndexWithinTable < displayColumns.size())
                    {
                        displayColumns.get(columnIndexWithinTable).getColumnHandler().fetchValue(
                            rowIndexWithinTable,
                            b -> {},
                            c -> parent.getGrid().select(new RectangularTableCellSelection(c.rowIndex, c.columnIndex, dataSelectionLimits)),
                            (rowIndex, colIndex, editorKit) -> {
                                // The rowIndex and colIndex are in table data terms, so we must translate:
                                @Nullable StructuredTextField cell = getCell.apply(new CellPosition(getPosition().rowIndex + HEADER_ROWS + rowIndex, getPosition().columnIndex + colIndex));
                                if (cell != null)
                                    cell.resetContent(editorKit);
                            }
                        );
                    }
                }

                @Override
                public ObjectExpression<? extends Collection<CellStyle>> styleForAllCells()
                {
                    return cellStyles;
                }
            };
        }

        @Override
        public @OnThread(Tag.FXPlatform) void updateKnownRows(int checkUpToOverallRowIncl, FXPlatformRunnable updateSizeAndPositions)
        {
            final int checkUpToRowIncl = checkUpToOverallRowIncl - getPosition().rowIndex;
            if (!currentKnownRowsIsFinal && currentKnownRows < checkUpToRowIncl)
            {
                Workers.onWorkerThread("Fetching row size", Priority.FETCH, () -> {
                    try
                    {
                        // Short-cut: check if the last index we are interested in has a row.  If so, can return early:
                        boolean lastRowValid = recordSet.indexValid(checkUpToRowIncl);
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
                            int length = recordSet.getLength();
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
        @OnThread(Tag.FXPlatform)
        public void modifiedDataItems(int startRowIncl, int endRowIncl)
        {
            onModify.run();
        }

        //TODO @Override
        protected void withNewColumnDetails(AppendColumn appendColumn)
        {
            FXUtility.alertOnErrorFX_(() -> {
                // Show a dialog to prompt for the name and type:
                NewColumnDialog dialog = new NewColumnDialog(parent.getManager());
                Optional<NewColumnDialog.NewColumnDetails> choice = dialog.showAndWait();
                if (choice.isPresent())
                {
                    Workers.onWorkerThread("Adding column", Workers.Priority.SAVE_ENTRY, () ->
                    {
                        FXUtility.alertOnError_(() ->
                        {
                            appendColumn.appendColumn(choice.get().name, choice.get().type, choice.get().defaultValue);
                            Platform.runLater(() -> parent.modified());
                        });
                    });
                }
            });
        }

        @Override
        public void removedAddedRows(int startRowIncl, int removedRowsCount, int addedRowsCount)
        {
            if (startRowIncl < currentKnownRows)
            {
                currentKnownRows += addedRowsCount - removedRowsCount;
            }
            currentKnownRowsIsFinal = false;
            updateParent();
            onModify.run();
        }

        @Override
        public @OnThread(Tag.FXPlatform) void addedColumn(Column newColumn)
        {
            setColumnsAndRows(TableDisplayUtility.makeStableViewColumns(recordSet, table.getShowColumns(), onModify), table.getOperations(), c -> getExtraColumnActions(c));
        }

        @Override
        public @OnThread(Tag.FXPlatform) void removedColumn(ColumnId oldColumnId)
        {
            setColumnsAndRows(TableDisplayUtility.makeStableViewColumns(recordSet, table.getShowColumns(), onModify), table.getOperations(), c -> getExtraColumnActions(c));
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

        @Override
        public int getCurrentKnownRows()
        {
            return currentKnownRows + HEADER_ROWS + (getTable().getOperations().appendRows != null ? 1 : 0);
        }

        @Override
        public int getColumnCount(@UnknownInitialization(GridArea.class) TableDataDisplay this)
        {
            return (displayColumns == null ? 0 : displayColumns.size()) + (getTable().getOperations().appendColumn != null ? 1 : 0);
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
    }

    @OnThread(Tag.FXPlatform)
    public TableDisplay(View parent, VirtualGridSupplierFloating columnHeaderSupplier, Table table)
    {
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
        tableDataDisplay = new TableDataDisplay(parent.getManager(), this.recordSetOrError.<Pair<@Nullable RecordSet, MessageWhenEmpty>>either(err -> new Pair<@Nullable RecordSet, MessageWhenEmpty>(null, new MessageWhenEmpty(err)),
                recordSet -> new Pair<>(recordSet, table.getDisplayMessageWhenEmpty())), columnHeaderSupplier, () -> {
            parent.modified();
            Workers.onWorkerThread("Updating dependents", Workers.Priority.FETCH, () -> FXUtility.alertOnError_(() -> parent.getManager().edit(table.getId(), null)));
        });
        Button actionsButton = GUI.buttonMenu("tableDisplay.menu.button", () -> makeTableContextMenu());

        Button addButton = GUI.button("tableDisplay.addTransformation", () -> {
            parent.newTransformFromSrc(table);
        });

        Label title = new Label(table.getId().getOutput());
        GUI.showTooltipWhenAbbrev(title);
        Utility.addStyleClass(title, "table-title");
        header = new HBox(actionsButton, title);
        header.getChildren().add(addButton);
        Utility.addStyleClass(header, "table-header");

        mostRecentBounds = new AtomicReference<>();
        updateMostRecentBounds();

        // Must be done as last item:
        @SuppressWarnings("initialization") @Initialized TableDisplay usInit = this;
        this.table.setDisplay(usInit);
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

    @RequiresNonNull({"mostRecentBounds", "tableDataDisplay"})
    private void updateMostRecentBounds(@UnknownInitialization(Object.class) TableDisplay this)
    {
        mostRecentBounds.set(tableDataDisplay.getPosition());
    }

    @RequiresNonNull({"columnDisplay", "table", "parent"})
    private ContextMenu makeTableContextMenu(@UnknownInitialization(Object.class) TableDisplay this)
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
            GUI.menuItem("tableDisplay.menu.copyValues", () -> FXUtility.alertOnErrorFX_(() -> ClipboardUtils.copyValuesToClipboard(parent.getManager().getUnitManager(), parent.getManager().getTypeManager(), table.getData().getColumns()))),
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
    
    @OnThread(Tag.Any)
    @Override
    public void loadPosition(CellPosition cellPosition, Pair<Display, ImmutableList<ColumnId>> display)
    {
        // Important we do this now, not in runLater, as if we then save,
        // it will be valid:
        mostRecentBounds.set(cellPosition);
        
        Platform.runLater(() -> {
            this.tableDataDisplay.setPosition(cellPosition);
            this.columnDisplay.set(display);
        });
    }

    @OnThread(Tag.Any)
    @Override
    public CellPosition getMostRecentPosition()
    {
        return mostRecentBounds.get();
    }

    public ImmutableList<records.gui.stable.ColumnOperation> getExtraColumnActions(ColumnId c)
    {
        ImmutableList.Builder<ColumnOperation> r = ImmutableList.builder();
        try
        {
            DataType type = getTable().getData().getColumn(c).getType();
            if (type.isNumber())
            {
                r.add(columnQuickTransform("recipe.sum", "sum", c, newId -> {
                    return new SummaryStatistics(parent.getManager(), null, table.getId(), ImmutableList.of(new Pair<>(newId, new CallExpression(parent.getManager().getUnitManager(), "sum", new ColumnReference(c, ColumnReferenceType.WHOLE_COLUMN)))), ImmutableList.of());
                }));

                r.add(columnQuickTransform("recipe.average", "average", c, newId -> {
                    return new SummaryStatistics(parent.getManager(), null, table.getId(), ImmutableList.of(new Pair<>(newId, new CallExpression(parent.getManager().getUnitManager(), "average", new ColumnReference(c, ColumnReferenceType.WHOLE_COLUMN)))), ImmutableList.of());
                }));
            }
        }
        catch (InternalException | UserException e)
        {
            Log.log(e);
        }
        return r.build();
    }
    
    private ColumnOperation columnQuickTransform(@LocalizableKey String nameKey, String suggestedPrefix, ColumnId srcColumn, SimulationFunction<ColumnId, Transformation> makeTransform) throws InternalException, UserException
    {
        String stem = suggestedPrefix + "." + srcColumn.getRaw();
        String nextId = stem;
        for (int i = 1; i <= 1000; i++)
        {
            if (!table.getData().getColumnIds().contains(new ColumnId(nextId)))
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
                    parent.getManager().record(t);
                });
            }
        };
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
}
