package records.gui;

import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import org.checkerframework.checker.guieffect.qual.UIEffect;
import records.data.RecordSet;
import records.data.Table;
import records.data.Table.TableDisplayBase;
import records.data.TableOperations;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformRunnable;
import utility.Utility;
import utility.Workers;
import utility.gui.FXUtility;
import records.gui.stable.StableView;
import utility.gui.GUI;

import java.util.ArrayList;
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
    private final RecordSet recordSet;
    private final Table table;
    private final String error;
    private boolean resizing;
    // In parent coordinates:
    private Bounds originalSize;
    // In local coordinates:
    private Point2D offsetDrag;
    private boolean resizeLeft;
    private boolean resizeRight;
    private boolean resizeTop;
    private boolean resizeBottom;
    @OnThread(Tag.Any)
    private final AtomicReference<Bounds> mostRecentBounds;

    @OnThread(Tag.Any)
    public RecordSet getRecordSet()
    {
        return recordSet;
    }

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
    private static class TableDataDisplay extends StableView implements RecordSet.RecordSetListener
    {
        private final FXPlatformRunnable onModify;

        @SuppressWarnings("initialization")
        @UIEffect
        public TableDataDisplay(RecordSet recordSet, TableOperations operations, FXPlatformRunnable onModify)
        {
            super();
            this.onModify = onModify;
            recordSet.setListener(this);
            setColumns(TableDisplayUtility.makeStableViewColumns(recordSet), operations);
            setRows(recordSet::indexValid);
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
            if (removedRowsCount > 0)
            {
                items.remove(startRowIncl, startRowIncl + removedRowsCount + 1);
            }
            if (addedRowsCount > 0)
            {
                // TODO this isn't very efficient:
                for (int i = 0; i < addedRowsCount; i++)
                {
                    items.add(startRowIncl + i, null);
                }
            }

            onModify.run();
        }
    }

    @SuppressWarnings("initialization")
    @OnThread(Tag.FXPlatform)
    public TableDisplay(View parent, Table table)
    {
        this.table = table;
        String error;
        RecordSet recordSet;
        try
        {
            recordSet = table.getData();
            error = null;
        }
        catch (UserException | InternalException e)
        {
            error = e.getLocalizedMessage();
            recordSet = null;
        }
        this.error = error;
        this.recordSet = recordSet;
        StackPane body = new StackPane(new TableDataDisplay(recordSet, table.getOperations(), parent::modified).getNode());
        Utility.addStyleClass(body, "table-body");
        setCenter(body);
        Utility.addStyleClass(this, "table-wrapper");
        setPickOnBounds(true);
        Pane spacer = new Pane();
        spacer.setVisible(false);
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button addButton = new Button("+");
        addButton.setOnAction(e -> {
            parent.edit(getTable());
        });

        Label title = new Label(table.getId().getOutput());
        Utility.addStyleClass(title, "table-title");
        HBox header = new HBox(title, spacer);
        if (table.showAddColumnButton())
        {
            Button addColumnButton = GUI.button("tableDisplay.addColumn", () -> {
                // Show a dialog to prompt for the name and type:
                NewColumnDialog dialog = new NewColumnDialog(parent.getManager());
                Optional<NewColumnDialog.NewColumnDetails> choice = dialog.showAndWait();
                if (choice.isPresent())
                {
                    Workers.onWorkerThread("Adding column", Workers.Priority.SAVE_ENTRY, () ->
                    {
                        Utility.alertOnError_(() ->
                        {
                            Table newTable = table.addColumn(choice.get().name, choice.get().type, choice.get().defaultValue);
                            parent.getManager().edit(table.getId(), newTable);
                        });
                    });
                }
            }, "add-column");
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
            if (!dragResize(e.getSceneX(), e.getSceneY()))
            {
                Point2D pos = localToParent(sceneToLocal(e.getSceneX(), e.getSceneY()));
                setLayoutX(Math.max(0, pos.getX() - offsetDrag.getX()));
                setLayoutY(Math.max(0, pos.getY() - offsetDrag.getY()));
            }
        });

        setOnMouseMoved(e -> {
            if (resizing)
                return;
            double padding = body.getPadding().getBottom();
            Point2D p = sceneToLocal(e.getSceneX(), e.getSceneY());
            resizeLeft = p.getX() < padding;
            resizeRight = p.getX() > getBoundsInLocal().getMaxX() - padding;
            resizeTop = p.getY() < padding;
            resizeBottom = p.getY() > getBoundsInLocal().getMaxY() - padding;
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
        setOnMouseDragged(e -> dragResize(e.getSceneX(), e.getSceneY()));
        setOnMouseReleased(e -> { resizing = false; });

        mostRecentBounds = new AtomicReference<>(getBoundsInParent());
        FXUtility.addChangeListenerPlatformNN(boundsInParentProperty(), mostRecentBounds::set);

        // Must be last line:
        this.table.setDisplay(this);
    }

    private boolean dragResize(double sceneX, double sceneY)
    {
        if (resizing)
        {
            Point2D p = sceneToLocal(sceneX, sceneY);
            if (resizeBottom)
            {
                setMinHeight(p.getY() + (originalSize.getHeight() - offsetDrag.getY()));
            }
            if (resizeRight)
            {
                setMinWidth(p.getX() + (originalSize.getWidth() - offsetDrag.getX()));
            }
            if (resizeLeft)
            {
                setLayoutX(localToParent(p.getX() - offsetDrag.getX(), getLayoutY()).getX());
                setMinWidth(originalSize.getMaxX() - getLayoutX());
            }
            if (resizeTop)
            {
                setLayoutY(localToParent(getLayoutX(), p.getY() - offsetDrag.getY()).getY());
                setMinHeight(originalSize.getMaxY() - getLayoutY());
            }
            return true;
        }
        return false;
    }

    @OnThread(Tag.Any)
    @Override
    public Bounds getPosition()
    {
        return mostRecentBounds.get();
    }
}
