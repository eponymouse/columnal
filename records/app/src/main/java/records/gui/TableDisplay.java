package records.gui;

import com.google.common.collect.ImmutableList;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Dimension2D;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import org.checkerframework.checker.guieffect.qual.UIEffect;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.checkerframework.dataflow.qual.Pure;
import records.data.Column;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.Table;
import records.data.Table.Display;
import records.data.Table.TableDisplayBase;
import records.data.TableId;
import records.data.TableManager;
import records.data.Transformation;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import records.gui.stf.TableDisplayUtility;
import records.importers.ClipboardUtils;
import records.transformations.HideColumnsPanel;
import records.transformations.TransformationEditable;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformRunnable;
import utility.Pair;
import utility.Utility;
import utility.Workers;
import utility.gui.FXUtility;
import records.gui.stable.StableView;
import utility.gui.GUI;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A pane which displays a table.  This includes the faux title bar
 * of the table which you can use to drag it around, the buttons in
 * the title bar for manipulation, and the display of the data itself.
 *
 * Primarily, this class is responsible for the moving and resizing within
 * the display.  The data is handled by the inner class TableDataDisplay.
 */
@OnThread(Tag.FXPlatform)
public class TableDisplay extends BorderPane implements TableDisplayBase
{
    private static final int INITIAL_LOAD = 100;
    private static final int LOAD_CHUNK = 100;
    private final Either<String, RecordSet> recordSetOrError;
    private final Table table;
    private @MonotonicNonNull TableDataDisplay tableDataDisplay;
    private boolean resizing;
    // In parent coordinates:
    private @Nullable Bounds originalSize;
    // In local coordinates:
    private @Nullable Point2D offsetDrag;
    private boolean resizeLeft;
    private boolean resizeRight;
    private boolean resizeTop;
    private boolean resizeBottom;
    @OnThread(Tag.Any)
    private final AtomicReference<Bounds> mostRecentBounds;
    private final HBox header;
    private final ObjectProperty<Pair<Display, ImmutableList<ColumnId>>> columnDisplay = new SimpleObjectProperty<>(new Pair<>(Display.ALL, ImmutableList.of()));

    // If true, automatically resizes to fit content.  Manual resizing will set this to false,
    // but a double-click will return to automatic resizing.
    private final BooleanProperty sizedToFitHorizontal = new SimpleBooleanProperty(false);
    private final BooleanProperty sizedToFitVertical = new SimpleBooleanProperty(false);

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

    @OnThread(Tag.Any)
    public Table getTable()
    {
        return table;
    }

    @OnThread(Tag.FXPlatform)
    private class TableDataDisplay extends StableView implements RecordSet.RecordSetListener
    {
        private final FXPlatformRunnable onModify;
        private final RecordSet recordSet;

        @SuppressWarnings("initialization")
        @UIEffect
        public TableDataDisplay(RecordSet recordSet, FXPlatformRunnable onModify)
        {
            super();
            this.recordSet = recordSet;
            this.onModify = onModify;
            recordSet.setListener(this);
            setColumnsAndRows(TableDisplayUtility.makeStableViewColumns(recordSet, table.getShowColumns(), onModify), table.getOperations(), recordSet::indexValid);
            //TODO restore editability
            //setEditable(getColumns().stream().anyMatch(TableColumn::isEditable));
            //boolean expandable = getColumns().stream().allMatch(TableColumn::isEditable);
            Workers.onWorkerThread("Determining row count", Workers.Priority.FETCH, () -> {
                ArrayList<Integer> indexesToAdd = new ArrayList<Integer>();
                Utility.alertOnError_(() -> {
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
                setColumnsAndRows(TableDisplayUtility.makeStableViewColumns(recordSet, newDisplay.mapSecond(blackList -> s -> !blackList.contains(s)), onModify), table.getOperations(), recordSet::indexValid);
            });
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
            // TODO make table redisplay

            onModify.run();
        }

        @Override
        public @OnThread(Tag.FXPlatform) void addedColumn(Column newColumn)
        {
            setColumnsAndRows(TableDisplayUtility.makeStableViewColumns(recordSet, table.getShowColumns(), onModify), table.getOperations(), recordSet::indexValid);
        }
    }

    @OnThread(Tag.FXPlatform)
    public TableDisplay(View parent, Table table)
    {
        this.table = table;
        Either<String, RecordSet> recordSetOrError;
        try
        {
            recordSetOrError = Either.right(table.getData());
        }
        catch (UserException | InternalException e)
        {
            recordSetOrError = Either.left(e.getLocalizedMessage());
        }
        this.recordSetOrError = recordSetOrError;
        StackPane body = new StackPane(this.recordSetOrError.<Node>either(err -> new Label(err),
            recordSet -> (tableDataDisplay = new TableDataDisplay(recordSet, () -> {
                parent.modified();
                Workers.onWorkerThread("Updating dependents", Workers.Priority.FETCH,() -> Utility.alertOnError_(() -> parent.getManager().edit(table.getId(), null)));
            })).getNode()));
        Utility.addStyleClass(body, "table-body");
        setCenter(body);
        Utility.addStyleClass(this, "table-wrapper", "tableDisplay", table instanceof Transformation ? "tableDisplay-transformation" : "tableDisplay-source");
        setPickOnBounds(true);
        Pane spacer = new Pane();
        spacer.setVisible(false);
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button actionsButton = GUI.buttonMenu("tableDisplay.menu.button", () -> makeTableContextMenu(parent));

        Button addButton = GUI.button("tableDisplay.addTransformation", () -> {
            parent.newTransformFromSrc(table);
        });

        Label title = new Label(table.getId().getOutput());
        Utility.addStyleClass(title, "table-title");
        header = new HBox(actionsButton, title, spacer);
        if (table.showAddColumnButton())
        {
            Button addColumnButton = GUI.button("tableDisplay.addColumn", () -> Utility.alertOnErrorFX_(() -> {
                // Show a dialog to prompt for the name and type:
                NewColumnDialog dialog = new NewColumnDialog(parent.getManager());
                Optional<NewColumnDialog.NewColumnDetails> choice = dialog.showAndWait();
                if (choice.isPresent())
                {
                    Workers.onWorkerThread("Adding column", Workers.Priority.SAVE_ENTRY, () ->
                    {
                        Utility.alertOnError_(() ->
                        {
                            table.addColumn(choice.get().name, choice.get().type, choice.get().defaultValueUnparsed);
                            Platform.runLater(() -> parent.modified());
                        });
                    });
                }
            }), "add-column");
            header.getChildren().add(addColumnButton);
        }
        header.getChildren().add(addButton);
        Utility.addStyleClass(header, "table-header");
        setTop(header);

        EventHandler<MouseEvent> onPressed = e ->
        {
            offsetDrag = sceneToLocal(e.getSceneX(), e.getSceneY());
            resizing = resizeLeft || resizeTop || resizeRight || resizeBottom;
            if (resizing)
                originalSize = getBoundsInParent();
        };
        header.setOnMousePressed(onPressed);
        header.setOnMouseDragged(e -> {
            double sceneX = e.getSceneX();
            double sceneY = e.getSceneY();
            // If it is a resize, that will be taken care of in the called method.  Otherwise, we do a drag-move:
            if (offsetDrag != null && !FXUtility.mouse(this).dragResize(parent, sceneX, sceneY))
            {
                Point2D pos = localToParent(sceneToLocal(sceneX, sceneY));
                double newX = Math.max(0, pos.getX() - offsetDrag.getX());
                double newY = Math.max(0, pos.getY() - offsetDrag.getY());

                @SuppressWarnings("initialization") // Due to passing this
                Point2D snapped = parent.snapTableDisplayPositionWhileDragging(this, e.isShiftDown(), new Point2D(newX, newY), new Dimension2D(getBoundsInLocal().getWidth(), getBoundsInLocal().getHeight()));
                setLayoutX(snapped.getX());
                setLayoutY(snapped.getY());
            }
        });
        header.setOnMouseReleased(e -> {
            parent.tableDragEnded();
            parent.tableMovedOrResized(this);
        });

        setOnMouseMoved(e -> {
            if (resizing)
                return;
            double padding = body.getPadding().getBottom();
            Point2D p = sceneToLocal(e.getSceneX(), e.getSceneY());
            double paddingResizeToleranceFactor = 2.5;
            resizeLeft = p.getX() < padding * paddingResizeToleranceFactor;
            resizeRight = p.getX() > getBoundsInLocal().getMaxX() - padding * paddingResizeToleranceFactor;
            // Top deliberately doesn't use extra tolerance, as more likely want to drag to move:
            resizeTop = p.getY() < padding;
            resizeBottom = p.getY() > getBoundsInLocal().getMaxY() - padding * paddingResizeToleranceFactor;
            if (resizeLeft)
            {
                if (resizeTop)
                    setCursor(Cursor.NW_RESIZE);
                else if (resizeBottom)
                    setCursor(Cursor.SW_RESIZE);
                else
                    setCursor(Cursor.W_RESIZE);
            }
            else if (resizeRight)
            {
                if (resizeTop)
                    setCursor(Cursor.NE_RESIZE);
                else if (resizeBottom)
                    setCursor(Cursor.SE_RESIZE);
                else
                    setCursor(Cursor.E_RESIZE);
            }
            else if (resizeTop || resizeBottom)
            {
                setCursor(resizeTop ? Cursor.N_RESIZE : Cursor.S_RESIZE);
            }
            else
            {
                setCursor(null);
            }
        });
        setOnMousePressed(onPressed);
        setOnMouseDragged(e -> FXUtility.mouse(this).dragResize(parent, e.getSceneX(), e.getSceneY()));
        setOnMouseReleased(e -> {
            resizing = false;
        });
        setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY)
            {
                if (resizeRight)
                {
                    sizedToFitHorizontal.set(true);
                }
                // Not an else -- both can fire if over bottom right:
                if (resizeBottom)
                {
                    sizedToFitVertical.set(true);
                }
            }
        });

        mostRecentBounds = new AtomicReference<>(getBoundsInParent());
        FXUtility.addChangeListenerPlatformNN(boundsInParentProperty(), b -> mostRecentBounds.set(b));

        if (tableDataDisplay != null)
        {
            FXUtility.addChangeListenerPlatformNN(sizedToFitHorizontal, b -> FXUtility.mouse(this).updateSnappedFitWidth());
            FXUtility.addChangeListenerPlatformNN(sizedToFitVertical, b -> FXUtility.mouse(this).updateSnappedFitHeight());
            FXUtility.addChangeListenerPlatformNN(tableDataDisplay.widthEstimateProperty(), w -> FXUtility.mouse(this).updateSnappedFitWidth());
            FXUtility.addChangeListenerPlatformNN(tableDataDisplay.heightEstimateProperty(), h -> FXUtility.mouse(this).updateSnappedFitHeight());
        }

        setMaxWidth(USE_PREF_SIZE);
        setMaxHeight(USE_PREF_SIZE);
        setMinWidth(USE_PREF_SIZE);
        setMinHeight(USE_PREF_SIZE);

        // Must be done as last item:
        @SuppressWarnings("initialization") @Initialized TableDisplay usInit = this;
        this.table.setDisplay(usInit);
    }

    private void updateSnappedFitWidth()
    {
        if (sizedToFitHorizontal.get() && tableDataDisplay != null)
        {
            setPrefWidth(tableDataDisplay.widthEstimateProperty().getValue() +
                ((Pane)getCenter()).getInsets().getLeft() + ((Pane)getCenter()).getInsets().getRight()
                + 2 // Fudge factor: possibly overall border?
            );
        }
    }

    private void updateSnappedFitHeight()
    {
        if (sizedToFitVertical.get() && tableDataDisplay != null)
        {
            setPrefHeight(
                tableDataDisplay.heightEstimateProperty().getValue() +
                header.getHeight() +
                ((Pane)getCenter()).getInsets().getTop() + ((Pane)getCenter()).getInsets().getBottom()
                + 2 // Fudge factor: possibly overall border?
            );
        }
    }

    @RequiresNonNull({"columnDisplay", "table"})
    private ContextMenu makeTableContextMenu(@UnknownInitialization(Object.class) TableDisplay this, View parent)
    {
        List<MenuItem> items = new ArrayList<>();

        if (table instanceof TransformationEditable)
        {
            items.add(GUI.menuItem("tableDisplay.menu.edit", () -> parent.editTransform((TransformationEditable)table)));
        }

        ToggleGroup show = new ToggleGroup();
        Map<Display, RadioMenuItem> showItems = new EnumMap<>(Display.class);

        showItems.put(Display.COLLAPSED, GUI.radioMenuItem("tableDisplay.menu.show.collapse", () -> setDisplay(Display.COLLAPSED, ImmutableList.of(), parent)));
        showItems.put(Display.ALL, GUI.radioMenuItem("tableDisplay.menu.show.all", () -> setDisplay(Display.ALL, ImmutableList.of(), parent)));
        showItems.put(Display.ALTERED, GUI.radioMenuItem("tableDisplay.menu.show.altered", () -> setDisplay(Display.ALTERED, ImmutableList.of(), parent)));
        showItems.put(Display.CUSTOM, GUI.radioMenuItem("tableDisplay.menu.show.custom", () -> editCustomDisplay(parent)));

        items.addAll(Arrays.asList(
            GUI.menu("tableDisplay.menu.showColumns",
                GUI.radioMenuItems(show, showItems.values().toArray(new RadioMenuItem[0]))
            ),
            GUI.menuItem("tableDisplay.menu.addTransformation", () -> parent.newTransformFromSrc(table)),
            GUI.menuItem("tableDisplay.menu.copyValues", () -> Utility.alertOnErrorFX_(() -> ClipboardUtils.copyValuesToClipboard(parent.getManager().getUnitManager(), parent.getManager().getTypeManager(), table.getData().getColumns()))),
            GUI.menuItem("tableDisplay.menu.exportToCSV", () -> {
                File file = FXUtility.getFileSaveAs(parent);
                if (file != null)
                {
                    final File fileNonNull = file;
                    Workers.onWorkerThread("Export to CSV", Workers.Priority.SAVE_TO_DISK, () -> Utility.alertOnError_(() -> exportToCSV(table, fileNonNull)));
                }
            })
        ));
        Optional.ofNullable(showItems.get(columnDisplay.get().getFirst())).ifPresent(show::selectToggle);
        return new ContextMenu(items.toArray(new MenuItem[0]));
    }

    @RequiresNonNull({"columnDisplay", "table"})
    private void editCustomDisplay(@UnknownInitialization(Object.class) TableDisplay this, View parent)
    {
        ImmutableList<ColumnId> blackList = new CustomColumnDisplayDialog(parent.getManager(), table.getId(), columnDisplay.get().getSecond()).showAndWait().orElse(null);
        // Only switch if they didn't cancel, otherwise use previous view mode:
        if (blackList != null)
            setDisplay(Display.CUSTOM, blackList, parent);
    }

    @RequiresNonNull({"columnDisplay", "table"})
    private void setDisplay(@UnknownInitialization(Object.class) TableDisplay this, Display newState, ImmutableList<ColumnId> blackList, View parent)
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

    @Pure // It does changes position, but doesn't alter fields which is what matters
    private boolean dragResize(View parent, double sceneX, double sceneY)
    {
        if (resizing && originalSize != null && offsetDrag != null)
        {
            Point2D p = sceneToLocal(sceneX, sceneY);
            final double offsetDragX = offsetDrag.getX();
            final double offsetDragY = offsetDrag.getY();
            final double originalSizeWidth = originalSize.getWidth();
            final double originalSizeHeight = originalSize.getHeight();
            final double originalSizeMaxX = originalSize.getMaxX();
            final double originalSizeMaxY = originalSize.getMaxY();

            boolean changed = false;
            if (resizeBottom)
            {
                setPrefHeight(p.getY() + (originalSizeHeight - offsetDragY));
                sizedToFitVertical.set(false);
                changed = true;
            }
            if (resizeRight)
            {
                setPrefWidth(p.getX() + (originalSizeWidth - offsetDragX));
                sizedToFitHorizontal.set(false);
                changed = true;
            }
            if (resizeLeft)
            {
                setLayoutX(localToParent(p.getX() - offsetDragX, getLayoutY()).getX());
                setPrefWidth(originalSizeMaxX - getLayoutX());
                sizedToFitHorizontal.set(false);
                changed = true;
            }
            if (resizeTop)
            {
                setLayoutY(localToParent(getLayoutX(), p.getY() - offsetDragY).getY());
                setPrefHeight(originalSizeMaxY - getLayoutY());
                sizedToFitVertical.set(false);
                changed = true;
            }
            if (changed)
            {
                parent.tableMovedOrResized(this);
            }

            return true;
        }
        return false;
    }

    @Override
    public @OnThread(Tag.Any) void loadPosition(Bounds bounds, Pair<Display, ImmutableList<ColumnId>> display)
    {
        Platform.runLater(() -> {
            setLayoutX(bounds.getMinX());
            setLayoutY(bounds.getMinY());
            setPrefWidth(bounds.getWidth());
            setPrefHeight(bounds.getHeight());
            this.columnDisplay.set(display);
        });
    }

    @OnThread(Tag.Any)
    @Override
    public Bounds getPosition()
    {
        return mostRecentBounds.get();
    }

    @Override
    public @OnThread(Tag.FXPlatform) Bounds getHeaderBoundsInParent()
    {
        // Header in parent is our local coords, so localToParent that to get our parent:
        return localToParent(header.getBoundsInParent());
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
}
