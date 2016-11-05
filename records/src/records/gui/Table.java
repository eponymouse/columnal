package records.gui;

import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import org.checkerframework.checker.guieffect.qual.UIEffect;
import records.data.RecordSet;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;
import utility.Workers;

import java.util.ArrayList;

/**
 * Created by neil on 18/10/2016.
 */
@OnThread(Tag.FXPlatform)
public class Table extends BorderPane
{
    private static final int INITIAL_LOAD = 100;
    private static final int LOAD_CHUNK = 100;
    private boolean resizing;
    // In parent coordinates:
    private Bounds originalSize;
    // In local coordinates:
    private Point2D offsetDrag;
    private boolean resizeLeft;
    private boolean resizeRight;
    private boolean resizeTop;
    private boolean resizeBottom;

    @OnThread(Tag.FXPlatform)
    private static class TableDisplay extends TableView<Integer>
    {
        @SuppressWarnings("initialization")
        @UIEffect
        public TableDisplay(RecordSet recordSet)
        {
            super();
            getColumns().setAll(recordSet.getDisplayColumns());
            Workers.onWorkerThread("Determining row count for " + recordSet.getTitle(), () -> {
                ArrayList<Integer> indexesToAdd = new ArrayList<Integer>();
                Utility.alertOnError_(() -> {
                    for (int i = 0; i < INITIAL_LOAD; i++)
                        if (recordSet.indexValid(i))
                            indexesToAdd.add(Integer.valueOf(i));

                });
                // TODO when user causes a row to be shown, load LOAD_CHUNK entries
                // afterwards.
                Platform.runLater(() -> getItems().addAll(indexesToAdd));
            });

        }
    }

    @SuppressWarnings("initialization")
    public Table(View parent, RecordSet rs)
    {
        StackPane body = new StackPane(new TableDisplay(rs));
        Utility.addStyleClass(body, "table-body");
        setCenter(body);
        Utility.addStyleClass(this, "table-wrapper");
        setPickOnBounds(true);
        Pane spacer = new Pane();
        spacer.setVisible(false);
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button addButton = new Button("+");
        addButton.setOnAction(e -> {
            /*
            try
            {
                //SummaryStatistics.withGUICreate(rs, r -> parent.add(new Table(parent, r.getResult())));
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
                // TODO tell user
            }
            */
            new NewTransformationDialog(getScene().getWindow(), parent, rs).show(optNewTable -> optNewTable.ifPresent(t -> parent.add(t, Table.this)));
        });

        Label title = new Label(rs.getTitle());
        Utility.addStyleClass(title, "table-title");
        HBox header = new HBox(title, spacer, addButton);
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
}
