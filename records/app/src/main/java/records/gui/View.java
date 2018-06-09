package records.gui;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import javafx.application.Platform;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableObjectValue;
import javafx.geometry.Point2D;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Window;
import org.apache.commons.io.FileUtils;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import records.data.CellPosition;
import records.data.ColumnId;
import records.data.DataSource;
import records.data.EditableRecordSet;
import records.data.ImmediateDataSource;
import records.data.Table.InitialLoadDetails;
import records.data.TableAndColumnRenames;
import records.data.Table;
import records.data.Table.FullSaver;
import records.data.TableId;
import records.data.TableManager;
import records.data.TableManager.TableManagerListener;
import records.data.Transformation;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.gui.DataOrTransformChoice.DataOrTransform;
import records.gui.EditImmediateColumnDialog.ColumnDetails;
import records.gui.grid.RectangleBounds;
import records.gui.grid.VirtualGrid;
import records.gui.grid.VirtualGrid.Picker;
import records.gui.grid.VirtualGridLineSupplier;
import records.gui.grid.VirtualGridSupplierFloating;
import records.importers.manager.ImporterManager;
import records.transformations.Check;
import records.transformations.TransformationManager;
import records.transformations.expression.IdentExpression;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.*;
import utility.Workers.Priority;
import utility.gui.FXUtility;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by neil on 18/10/2016.
 */
@OnThread(Tag.FXPlatform)
public class View extends StackPane
{
    private static final double DEFAULT_SPACE = 150.0;

    //private final ObservableMap<Transformation, Overlays> overlays;
    private final TableManager tableManager;
    // The pane which actually holds the TableDisplay items:
    private final VirtualGrid mainPane;
    private final Pane overlayPane;
    private final Pane snapGuidePane;
    // The STF supplier for the main pane:
    private final DataCellSupplier dataCellSupplier = new DataCellSupplier();
    // The supplier for buttons to add rows and columns:
    private final ExpandTableArrowSupplier expandTableArrowSupplier;
    // The supplier for row labels:
    private final RowLabelSupplier rowLabelSupplier = new RowLabelSupplier();
    
    // This is only put into our children while we are doing special mouse capture, but it is always non-null.
    private @Nullable Pane pickPaneMouse;
    @OnThread(Tag.FXPlatform)
    private final ObjectProperty<File> diskFile;
    // Null means modified since last save
    private final ObjectProperty<@Nullable Instant> lastSaveTime = new SimpleObjectProperty<>(null);
    // Cancels a delayed save operation:
    private @Nullable FXPlatformRunnable cancelDelayedSave;

    // Pass true when empty
    private final FXPlatformConsumer<Boolean> emptyListener;

    // We start in readOnly mode, and enable writing later if everything goes well:
    private boolean readOnly = true;
    
    private void save()
    {
        if (readOnly)
            return;
        
        File dest = diskFile.get();
        class Fetcher extends FullSaver
        {
            private final Iterator<Table> it;

            public Fetcher(List<Table> allTables)
            {
                it = allTables.iterator();
            }

            @Override
            @OnThread(Tag.Simulation)
            public void saveTable(String s)
            {
                super.saveTable(s);
                getNext();
            }

            @OnThread(Tag.Simulation)
            private void getNext()
            {
                if (it.hasNext())
                {
                    it.next().save(dest, this, TableAndColumnRenames.EMPTY);
                }
                else
                {
                    String completeFile = getCompleteFile();
                    try
                    {
                        FileUtils.writeStringToFile(dest, completeFile, "UTF-8");
                        Instant now = Instant.now();
                        Platform.runLater(() -> lastSaveTime.setValue(now));
                    }
                    catch (IOException ex)
                    {
                        FXUtility.logAndShowError("save.error", ex);
                    }
                }
            }
        };
        //Exception e = new Exception();
        Workers.onWorkerThread("Saving", Priority.SAVE_TO_DISK, () ->
        {
            List<Table> allTablesUnordered = getAllTables();

            Map<TableId, Table> getById = new HashMap<>();
            Map<TableId, Collection<TableId>> edges = new HashMap<>();
            HashSet<TableId> allIds = new HashSet<>();
            for (Table t : allTablesUnordered)
            {
                allIds.add(t.getId());
                getById.put(t.getId(), t);
                if (t instanceof Transformation)
                {
                    edges.put(t.getId(), ((Transformation)t).getSources());
                }
            }
            List<TableId> linearised = GraphUtility.lineariseDAG(allIds, edges, Collections.emptyList());

            @SuppressWarnings("nullness")
            List<@NonNull Table> linearTables = Utility.mapList(linearised, id -> getById.get(id));
            new Fetcher(linearTables).getNext();

        });
    }

    @OnThread(Tag.Any)
    @NonNull
    private synchronized List<Table> getAllTables()
    {
        return tableManager.getAllTables();
    }

    @OnThread(Tag.Any)
    @NonNull
    private synchronized Stream<Table> streamAllTables()
    {
        return tableManager.streamAllTables();
    }

    @OnThread(Tag.Any)
    public TableManager getManager()
    {
        return tableManager;
    }
    
    @OnThread(Tag.Simulation)
    private void removeTable(Table t, int remainingCount)
    {
        FXUtility.runFX(() ->
        {
            save();
            //overlays.remove(t); // Listener removes them from display
            TableDisplay display = (TableDisplay) t.getDisplay();
            if (display != null)
            {
                // Call this first so that the nodes are actually removed when we call removeGridArea:
                display.cleanupFloatingItems();
                dataCellSupplier.removeGrid(display);
                mainPane.removeSelectionListener(display);
                expandTableArrowSupplier.removeGrid(display);
                rowLabelSupplier.removeGrid(display);
                // This goes last because it will redo layout:
                mainPane.removeGridArea(display);
            }
            emptyListener.consume(remainingCount == 0);
        });
    }

    public void setDiskFileAndSave(File newDest)
    {
        diskFile.set(newDest);
        save();
        Utility.usedFile(newDest);
    }

    public ObservableObjectValue<@Nullable Instant> lastSaveTime()
    {
        return lastSaveTime;
    }

    public void ensureSaved()
    {
        //TODO
    }

    public void modified()
    {
        lastSaveTime.set(null);
        // TODO use a timer rather than saving instantly every time?
        save();
    }

    @OnThread(Tag.Any)
    public VirtualGrid getGrid()
    {
        return mainPane;
    }

    public void disablePickingMode()
    {
        if (pickPaneMouse != null)
        {
            mainPane.stopHighlightingGridArea();
            getChildren().remove(pickPaneMouse);
            pickPaneMouse = null;
        }
    }
    
    public void enableTablePickingMode(Point2D screenPos, ImmutableSet<Table> excludeTables, FXPlatformConsumer<Table> onPick)
    {
        if (pickPaneMouse != null)
            disablePickingMode();
        
        final @NonNull Pane pickPaneMouseFinal = pickPaneMouse = new Pane();
        
        pickPaneMouseFinal.setPickOnBounds(true);
        @Nullable Table[] picked = new @Nullable Table[1];
        Picker<Table> validPick = (g, cell) -> {
            if (g instanceof TableDisplay && !excludeTables.contains(((TableDisplay) g).getTable()))
                return new Pair<>(new RectangleBounds(g.getPosition(), g.getBottomRightIncl()), ((TableDisplay) g).getTable());
            else
                return null;
        };
        pickPaneMouseFinal.setOnMouseMoved(e -> {
            picked[0] = mainPane.highlightGridAreaAtScreenPos(
                new Point2D(e.getScreenX(), e.getScreenY()),
                validPick,
                pickPaneMouseFinal::setCursor);
            e.consume();
        });
        pickPaneMouseFinal.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && picked[0] != null)
                onPick.consume(picked[0]);
        });
        getChildren().add(pickPaneMouseFinal);
        // Highlight immediately:
        mainPane.highlightGridAreaAtScreenPos(screenPos, validPick, pickPaneMouseFinal::setCursor);
    }

    public void enableColumnPickingMode(Point2D screenPos, Predicate<Pair<Table, ColumnId>> includeColumn, FXPlatformConsumer<Pair<Table, ColumnId>> onPick)
    {
        if (pickPaneMouse != null)
            disablePickingMode();

        final @NonNull Pane pickPaneMouseFinal = pickPaneMouse = new Pane();

        pickPaneMouseFinal.setPickOnBounds(true);
        class Ref
        {
            @Nullable Pair<Table, ColumnId> pick = null;
        }
        Ref picked = new Ref();
        Picker<Pair<Table, ColumnId>> validPick = (g, cell) -> {
            if (!(g instanceof TableDisplay))
                return null;
            TableDisplay tableDisplay = (TableDisplay) g;
            @Nullable Pair<ColumnId, RectangleBounds> c = tableDisplay.getColumnAt(cell);
            if (c != null && includeColumn.test(new Pair<>(tableDisplay.getTable(), c.getFirst())))
            {
                return new Pair<>(c.getSecond(), new Pair<>(tableDisplay.getTable(), c.getFirst()));
            }
            return null;
        };
        pickPaneMouseFinal.setOnMouseMoved(e -> {
            picked.pick = mainPane.highlightGridAreaAtScreenPos(
                new Point2D(e.getScreenX(), e.getScreenY()),
                validPick,
                pickPaneMouseFinal::setCursor);
            e.consume();
        });
        pickPaneMouseFinal.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && picked.pick != null)
                onPick.consume(picked.pick);
        });
        getChildren().add(pickPaneMouseFinal);
        // Highlight immediately:
        mainPane.highlightGridAreaAtScreenPos(screenPos, validPick, pickPaneMouseFinal::setCursor);
    }
    
    // If any sources are invalid, they are skipped
    private ImmutableList<Table> getSources(Table table)
    {
        if (table instanceof Transformation)
        {
            Collection<TableId> sourceIds = ((Transformation) table).getSources();
            return sourceIds.stream().flatMap(id -> Utility.streamNullable(tableManager.getSingleTableOrNull(id))).collect(ImmutableList.toImmutableList());
        }
        else
            return ImmutableList.of();
    }

    // Doesn't really need to be generic in both, but better type safety checking this way:
    private static <A,B> Pair<@Nullable A, @Nullable B> findFirstLeftAndFirstRight(Stream<Either<A, B>> stream)
    {
        // Atomic is a bit overkill, but creating arrays of generic types is a pain:
        AtomicReference<@Nullable A> left = new AtomicReference<>(null);
        AtomicReference<@Nullable B> right = new AtomicReference<>(null);
        for (Either<A, B> v : Utility.iterableStream(stream))
        {
            // Overwrite null only; leave other values intact:
            v.either_(x -> left.compareAndSet(null, x), x -> right.compareAndSet(null, x));

            // No point continuing if we've already found both:
            if (left.get() != null && right.get() != null)
                break;
        }

        return new Pair<>(left.get(), right.get());
    }

    /* TODO
    private boolean overlapsAnyExcept(List<TableDisplay> except, double x, double y)
    {
        List<Bounds> exceptHeaders = Utility.mapList(except, e -> new BoundingBox(x, y, e.getHeaderBoundsInParent().getWidth(), e.getHeaderBoundsInParent().getHeight()));
        return tableManager.getAllTables().stream()
                .flatMap(t -> Utility.streamNullable(t.getDisplay()))
                .filter(d -> !except.contains(d))
                .map(t -> t.getHeaderBoundsInParent())
                .anyMatch(r -> exceptHeaders.stream().anyMatch(e -> r.intersects(e)));
    }
    */

    /* TODO
    @OnThread(Tag.FXPlatform)
    private class Overlays
    {
        private final QuadCurve arrowFrom;
        private final HBox name;
        private final QuadCurve arrowTo;
        private final TableDisplay dest;
        private @MonotonicNonNull TableDisplay source;

        @OnThread(Tag.FXPlatform)
        public Overlays(List<TableDisplay> sources, String text, TableDisplay dest, FXPlatformRunnable edit)
        {
            this.dest = dest;
            Button button = new Button(text);
            button.setOnAction(e -> edit.run());
            name = new HBox(button);
            arrowFrom = new QuadCurve();
            arrowTo = new QuadCurve();
            Utility.addStyleClass(arrowFrom, "transformation-arrow");
            Utility.addStyleClass(arrowTo, "transformation-arrow");

            ChangeListener<Object> recalculate = new RecalcListener();
            
            if (!sources.isEmpty())
            {
                this.source = sources.get(0);  
                source.layoutXProperty().addListener(recalculate);
                source.layoutYProperty().addListener(recalculate);
                source.widthProperty().addListener(recalculate);
                source.heightProperty().addListener(recalculate);
            }
            
            dest.layoutXProperty().addListener(recalculate);
            dest.layoutYProperty().addListener(recalculate);
            dest.widthProperty().addListener(recalculate);
            dest.heightProperty().addListener(recalculate);

            recalculate.changed(new ReadOnlyBooleanWrapper(false), false, false);
        }

        @OnThread(Tag.FXPlatform)
        private void recalculatePosition()
        {
            double namePrefWidth = name.prefWidth(Double.MAX_VALUE);
            double namePrefHeight = name.prefHeight(Double.MAX_VALUE);
            
            if (source != null)
            {
                Pair<Point2D, Point2D> closestSrcDest = source.closestPointTo(dest.getBoundsInParent());
                double midX = 0.5 * (closestSrcDest.getFirst().getX() + closestSrcDest.getSecond().getX());
                double midY = 0.5 * (closestSrcDest.getFirst().getY() + closestSrcDest.getSecond().getY());
                
                Bounds predictedBounds = new BoundingBox(midX - namePrefWidth * 0.5, midY - namePrefHeight  * 0.5, namePrefWidth, namePrefHeight);
                
                if (!streamAllTables().<@Nullable TableDisplayBase>map(t -> t.getDisplay()).anyMatch(d -> d != null && d.getBoundsInParent().intersects(predictedBounds)))
                {
                    
                    name.layoutXProperty().unbind();
                    name.layoutXProperty().bind(name.widthProperty().multiply(-0.5).add(midX));
                    name.layoutYProperty().unbind();
                    name.layoutYProperty().bind(name.heightProperty().multiply(-0.5).add(midY));

                    Point2D from = source.closestPointTo(midX, midY - 100);
                    Point2D to = dest.closestPointTo(midX, midY + 100);

                    // Should use nearest point, not top-left:
                    arrowFrom.setLayoutX(from.getX());
                    arrowFrom.setLayoutY(from.getY());
                    arrowFrom.setControlX(midX - arrowFrom.getLayoutX());
                    arrowFrom.setControlY(midY - 100 - arrowFrom.getLayoutY());
                    arrowFrom.setEndX(midX - arrowFrom.getLayoutX());
                    arrowFrom.setEndY(midY - 50 - arrowFrom.getLayoutY());
                    arrowFrom.setVisible(true);
                    arrowTo.setLayoutX(midX);
                    arrowTo.setLayoutY(midY + 50);
                    arrowTo.setControlX(midX - arrowTo.getLayoutX());
                    arrowTo.setControlY(midY + 100 - arrowTo.getLayoutY());
                    arrowTo.setEndX(to.getX() - arrowTo.getLayoutX());
                    arrowTo.setEndY(to.getY() - arrowTo.getLayoutY());
                    arrowTo.setVisible(true);

                    return;
                }
            }
            
            // if we reach here, snap to top of table:
            double midX = dest.getBoundsInParent().getMinX() + namePrefWidth / 2.0;
            double midY = dest.getBoundsInParent().getMinY() - namePrefHeight * 0.7;
            
            
            arrowTo.setVisible(false);
            if (source != null)
            {
                Point2D from = source.closestPointTo(midX, midY - 100);
                arrowFrom.setLayoutX(from.getX());
                arrowFrom.setLayoutY(from.getY());
                arrowFrom.setControlX(midX - arrowFrom.getLayoutX());
                arrowFrom.setControlY(midY - 100 - arrowFrom.getLayoutY());
                arrowFrom.setEndX(midX - arrowFrom.getLayoutX());
                arrowFrom.setEndY(midY - 50 - arrowFrom.getLayoutY());
            }
            else
            {
                arrowFrom.setVisible(false);
            }
                
            name.layoutXProperty().unbind();
            name.layoutXProperty().bind(name.widthProperty().multiply(-0.5).add(midX));
            name.layoutYProperty().unbind();
            name.layoutYProperty().bind(name.heightProperty().multiply(-0.5).add(midY));
        }

        @OnThread(Tag.FXPlatform)
        private class RecalcListener implements ChangeListener<Object>
        {
            @Override
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public void changed(ObservableValue<?> a, Object b, Object c)
            {
                Overlays.this.recalculatePosition();
            }
        }
    }
    */

    // The type of the listener really throws off the checkers so suppress them all:
    @SuppressWarnings({"keyfor", "interning", "userindex", "valuetype", "helpfile"})
    public View(File location, FXPlatformConsumer<Boolean> emptyListener) throws InternalException, UserException
    {
        this.emptyListener = emptyListener;
        diskFile = new SimpleObjectProperty<>(location);
        tableManager = new TableManager(TransformationManager.getInstance(), new TableManagerListener()
        {
            // No-one will add tables after the constructor, so this is okay:
            @SuppressWarnings("initialization")
            private final View thisView = View.this;

            @Override
            public void removeTable(Table t, int tablesRemaining)
            {
                thisView.removeTable(t, tablesRemaining);
            }

            @Override
            public void addSource(DataSource dataSource)
            {
                FXUtility.runFX(() -> {
                    thisView.emptyListener.consume(false);
                    VirtualGridSupplierFloating floatingSupplier = FXUtility.mouse(View.this).getGrid().getFloatingSupplier();
                    thisView.addDisplay(new TableDisplay(thisView, floatingSupplier, dataSource));
                    thisView.save();
                });
            }

            @Override
            public void addTransformation(Transformation transformation)
            {
                FXUtility.runFX(() ->
                {
                    thisView.emptyListener.consume(false);
                    VirtualGridSupplierFloating floatingSupplier = FXUtility.mouse(View.this).getGrid().getFloatingSupplier();
                    TableDisplay tableDisplay = new TableDisplay(thisView, floatingSupplier, transformation);
                    thisView.addDisplay(tableDisplay);

                    List<TableDisplay> sourceDisplays = new ArrayList<>();
                    for (TableId t : transformation.getSources())
                    {
                        TableDisplay td = thisView.getTableDisplayOrNull(t);
                        if (td != null)
                            sourceDisplays.add(td);
                    }
                    /*overlays.put(transformation, new Overlays(sourceDisplays, transformation.getTransformationLabel(), tableDisplay, () ->
                    {
                        View.this.editTransform((TransformationEditable)transformation);
                    }));*/

                    thisView.save();
                });
            }
        });
        
        mainPane = new VirtualGrid(// Data table if there are none, or if we ask and they say data
// Ask what they want
            (cellPos, mousePos, grid) -> createTable(FXUtility.mouse(this), tableManager, cellPos, mousePos, grid),
            10, 20, "main-view-grid");
        expandTableArrowSupplier = new ExpandTableArrowSupplier(Utility.later(this));
        mainPane.addNodeSupplier(new VirtualGridLineSupplier());
        mainPane.addNodeSupplier(dataCellSupplier);
        mainPane.addNodeSupplier(expandTableArrowSupplier);
        mainPane.addNodeSupplier(rowLabelSupplier);
        overlayPane = new Pane();
        overlayPane.setPickOnBounds(false);
        snapGuidePane = new Pane();
        snapGuidePane.setMouseTransparent(true);
        pickPaneMouse = new Pane();
        getChildren().addAll(mainPane.getNode(), overlayPane, snapGuidePane);
        getStyleClass().add("view");
    }

    private void addDisplay(TableDisplay tableDisplay)
    {
        dataCellSupplier.addGrid(tableDisplay, tableDisplay.getDataGridCellInfo());
        mainPane.addGridAreas(ImmutableList.of(tableDisplay));
        mainPane.addSelectionListener(tableDisplay);
        rowLabelSupplier.addTable(mainPane, tableDisplay, false);
        @Nullable FXPlatformConsumer<@Nullable ColumnId> addColumn = tableDisplay.addColumnOperation();
        boolean addRows = tableDisplay.getTable().getOperations().appendRows != null;
        if (addColumn != null || addRows)
        {
            @Nullable FXPlatformRunnable addColumnAtEnd = null;
            if (addColumn != null)
            {
                @NonNull FXPlatformConsumer<@Nullable ColumnId> addColumnFinal = addColumn;
                addColumnAtEnd = () -> addColumnFinal.consume(null);
            }
            expandTableArrowSupplier.addTable(tableDisplay, addColumnAtEnd, addRows);
        }
    }

    private @Nullable TableDisplay getTableDisplayOrNull(TableId tableId)
    {
        @Nullable Table table = tableManager.getSingleTableOrNull(tableId);
        if (table == null)
            return null;
        else
            return (TableDisplay)table.getDisplay();
    }

    @SuppressWarnings("nullness") // Can't be a View without an actual window
    Window getWindow()
    {
        return getScene().getWindow();
    }

    @Override
    @OnThread(Tag.FX)
    public void requestFocus()
    {
        // Don't allow focus
    }

    public ObjectExpression<String> titleProperty()
    {
        return FXUtility.mapBindingLazy(diskFile, f -> f.getName() + " [" + f.getParent() + "]");
    }
    
    public DataCellSupplier _test_getDataCellSupplier()
    {
        return dataCellSupplier;
    }

    private static void createTable(View thisView, TableManager tableManager, CellPosition cellPosition, Point2D mouseScreenPos, VirtualGrid virtualGrid)
    {
        // Ask what they want
        GaussianBlur blur = new GaussianBlur(4.0);
        blur.setInput(new ColorAdjust(0.0, 0.0, -0.2, 0.0));
        virtualGrid.setEffectOnNonOverlays(blur);
        Window window = thisView.getWindow();
        Optional<Pair<Point2D, DataOrTransform>> choice = new DataOrTransformChoice(window, !tableManager.getAllTables().isEmpty()).showAndWaitCentredOn(mouseScreenPos);

        if (choice.isPresent())
        {
            InitialLoadDetails initialLoadDetails = new InitialLoadDetails(null, cellPosition, null);
            switch (choice.get().getSecond())
            {
                case DATA:
                    Optional<ColumnDetails> optInitialDetails = new EditImmediateColumnDialog(window, thisView.getManager(), new ColumnId("A")).showAndWait();
                    optInitialDetails.ifPresent(initialDetails -> {
                        Workers.onWorkerThread("Creating table", Priority.SAVE_ENTRY, () -> {
                            FXUtility.alertOnError_(() -> {
                                ImmediateDataSource data = new ImmediateDataSource(tableManager, initialLoadDetails, EditableRecordSet.newRecordSetSingleColumn(initialDetails.columnId, initialDetails.dataType, initialDetails.defaultValue));
                                tableManager.record(data);
                            });
                        });
                    });
                    break;
                case IMPORT_FILE:
                    ImporterManager.getInstance().chooseAndImportFile(window, tableManager, cellPosition, tableManager::record);
                    break;
                case IMPORT_URL:
                    ImporterManager.getInstance().chooseAndImportURL(window, tableManager, cellPosition, tableManager::record);
                    break;
                case TRANSFORM:
                    new PickTransformationDialog(window).showAndWaitCentredOn(mouseScreenPos).ifPresent(createTrans -> {
                        @Nullable SimulationSupplier<Transformation> makeTrans = createTrans.getSecond().make(thisView, thisView.getManager(), cellPosition, () ->
                            new PickTableDialog(thisView, null, createTrans.getFirst()).showAndWait());
                        if (makeTrans != null)
                        {
                            @NonNull SimulationSupplier<Transformation> makeTransFinal = makeTrans;
                            Workers.onWorkerThread("Creating transformation", Priority.SAVE_ENTRY, () -> {
                                FXUtility.alertOnError_(() -> {
                                    @OnThread(Tag.Simulation) Transformation transformation = makeTransFinal.get();
                                    tableManager.record(transformation);
                                    Platform.runLater(() -> {
                                        TableDisplay display = (TableDisplay) transformation.getDisplay();
                                        if (display != null)
                                            display.editAfterCreation();
                                    });
                                });
                            });
                        }
                    });
                    break;
                case CHECK:
                    new PickTableDialog(thisView, null, mouseScreenPos).showAndWait().ifPresent(srcTable -> {
                        new EditExpressionDialog(thisView, srcTable, new IdentExpression(""), false, DataType.BOOLEAN).showAndWait().ifPresent(checkExpression -> {
                            Workers.onWorkerThread("Creating check", Priority.SAVE_ENTRY, () -> FXUtility.alertOnError_(() -> {
                                Check check = new Check(thisView.getManager(), new InitialLoadDetails(null, cellPosition, null), srcTable.getId(), checkExpression);
                                tableManager.record(check);
                            }));
                        });
                    });
                    break;
            }
        }
        virtualGrid.setEffectOnNonOverlays(null);
    }

    public void enableWriting()
    {
        readOnly = false;
        save();
    }

    @OnThread(Tag.FXPlatform)
    public class FindEverywhereDialog extends Dialog<Void>
    {

        private final ListView<Result> results;

        private class Result
        {
            // TODO separate show text from find text
            private final String text;
            private final FXPlatformRunnable onPick;

            public Result(String text, FXPlatformRunnable onPick)
            {
                this.text = text;
                this.onPick = onPick;
            }

            public boolean matches(String findString)
            {
                return text.toLowerCase().contains(findString.toLowerCase());
            }

            @Override
            public String toString()
            {
                return text;
            }
        }

        public FindEverywhereDialog()
        {
            TextField findField = new TextField();
            results = new ListView<>();

            List<Result> allPossibleResults = findAllPossibleResults();
            results.getItems().setAll(allPossibleResults);

            FXUtility.addChangeListenerPlatformNN(findField.textProperty(), text -> {
                results.getItems().setAll(allPossibleResults.stream().filter(r -> r.matches(text)).collect(Collectors.toList()));
                results.getSelectionModel().selectFirst();
            });
            findField.setOnAction(e -> selectResult());

            BorderPane content = new BorderPane();
            content.setTop(findField);
            content.setCenter(results);
            getDialogPane().setContent(content);
            getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);

            setOnShown(e -> findField.requestFocus());
        }

        @RequiresNonNull("results")
        @SuppressWarnings("initialization")
        private void selectResult(@UnknownInitialization(Object.class) FindEverywhereDialog this)
        {
            @Nullable Result result = results.getSelectionModel().getSelectedItem();
            if (result != null)
            {
                result.onPick.run();
            }
            close();
        }

        private List<Result> findAllPossibleResults(@UnknownInitialization(Object.class) FindEverywhereDialog this)
        {
            List<Result> r = new ArrayList<>();
            // Tables:
            r.addAll(Utility.mapList(getAllTables(), t -> new Result(t.getId().getRaw(), () -> {
                TableDisplay tableDisplay = (TableDisplay) t.getDisplay();
            })));

            return r;
        }
    }


}

