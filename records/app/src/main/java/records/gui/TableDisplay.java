package records.gui;

import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import org.checkerframework.checker.guieffect.qual.UIEffect;
import records.data.RecordSet;
import records.data.Table;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;
import utility.Workers;

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
public class TableDisplay extends BorderPane
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
    private static class TableDataDisplay extends TableView<Integer> implements RecordSet.RecordSetListener
    {
        @SuppressWarnings("initialization")
        @UIEffect
        public TableDataDisplay(RecordSet recordSet)
        {
            super();
            recordSet.setListener(this);
            try
            {
                getColumns().setAll(recordSet.getDisplayColumns());
            }
            catch (InternalException | UserException e)
            {
                setPlaceholder(new Label(e.getLocalizedMessage()));
            }
            setEditable(getColumns().stream().anyMatch(TableColumn::isEditable));
            boolean expandable = getColumns().stream().allMatch(TableColumn::isEditable);
            Workers.onWorkerThread("Determining row count", () -> {
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
                            if (expandable)
                                indexesToAdd.add(Integer.valueOf(i == 0 ? Integer.MIN_VALUE : -i));
                        }
                    }
                });
                // TODO when user causes a row to be shown, load LOAD_CHUNK entries
                // afterwards.
                Platform.runLater(() -> getItems().addAll(indexesToAdd));
            });

        }

        @Override
        @OnThread(Tag.FXPlatform)
        public void rowAddedAtEnd()
        {
            // If our table has 3 real rows and is editable, it will have integers:
            // 0, 1, 2, -3
            // What we want to do is insert item 3 at position 3, to get:
            // 0, 1, 2, 3, -4
            getItems().set(getItems().size() - 1, Integer.valueOf(getItems().size() - 1));
            getItems().add(Integer.valueOf(- getItems().size()));

        }
    }

    @SuppressWarnings("initialization")
    @OnThread(Tag.FXPlatform)
    public TableDisplay(View parent, Table table)
    {
        this.table = table;
        this.table.setDisplay(this);
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
        StackPane body = new StackPane(new TableDataDisplay(recordSet));
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
            Button addColumnButton = new Button("Add Column");
            addColumnButton.setOnAction(e -> {
                // TODO show a dialog to prompt for the name and type:
                NewColumnDialog dialog = new NewColumnDialog();
                Optional<NewColumnDialog.NewColumnDetails> choice = dialog.showAndWait();
                if (choice.isPresent())
                {
                    Workers.onWorkerThread("Adding column", () ->
                    {
                        Utility.alertOnError_(() ->
                        {
                            Table newTable = table.addColumn(choice.get().name, choice.get().type, choice.get().defaultValue);
                            parent.getManager().edit(table.getId(), newTable);
                        });
                    });
                }
            });
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
        Utility.addChangeListenerPlatformNN(boundsInParentProperty(), mostRecentBounds::set);
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
    public Bounds getPosition()
    {
        return mostRecentBounds.get();
    }
}
