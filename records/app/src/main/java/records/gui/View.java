package records.gui;

import javafx.application.Platform;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableObjectValue;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.QuadCurve;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.stage.Window;
import org.apache.commons.io.FileUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.DataSource;
import records.data.Table;
import records.data.Table.FullSaver;
import records.data.TableId;
import records.data.TableManager;
import records.data.Transformation;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.TransformationEditable;
import records.transformations.TransformationEditor;
import records.transformations.TransformationManager;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.*;
import utility.gui.FXUtility;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by neil on 18/10/2016.
 */
@OnThread(Tag.FXPlatform)
public class View extends StackPane implements TableManager.TableManagerListener
{
    private static final double DEFAULT_SPACE = 150.0;

    private final ObservableMap<Transformation, Overlays> overlays;
    private final TableManager tableManager;
    private final Pane mainPane;
    // We want a display that dims everything except the hovered-over table
    // But that requires clipping which messes up the mouse selection.  So we
    // use two panes: one which is invisible for the mouse events, and one for the
    // display:
    private final Pane pickPaneMouse;
    private final Pane pickPaneDisplay;
    private final ObjectProperty<@Nullable Table> currentPick;
    private @Nullable FXPlatformConsumer<@Nullable Table> onPick;
    @OnThread(Tag.FXPlatform)
    private final ObjectProperty<File> diskFile;
    // Null means modified since last save
    private final ObjectProperty<@Nullable Instant> lastSaveTime = new SimpleObjectProperty<>(null);

    private void save()
    {
        File dest = diskFile.get();
        class Fetcher extends FullSaver
        {
            private final Iterator<Table> it;

            public Fetcher(List<Table> allTables)
            {
                it = allTables.iterator();
            }

            @Override
            public @OnThread(Tag.Simulation) void saveTable(String s)
            {
                super.saveTable(s);
                getNext();
            }

            @OnThread(Tag.Simulation)
            private void getNext()
            {
                if (it.hasNext())
                {
                    it.next().save(dest, this);
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
        Workers.onWorkerThread("Saving", () ->
        {
            new Fetcher(getAllTables()).getNext();
        });
    }

    @OnThread(Tag.Any)
    @NonNull
    private synchronized List<Table> getAllTables()
    {
        return tableManager.getAllTables();
    }

    @OnThread(Tag.Any)
    public TableManager getManager()
    {
        return tableManager;
    }

    @Override
    @OnThread(Tag.Simulation)
    public void removeTable(Table t)
    {
        Platform.runLater(() ->
        {
            save();
            overlays.remove(t); // Listener removes them from display
            mainPane.getChildren().remove(t.getDisplay());
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

    @OnThread(Tag.FXPlatform)
    private static class Overlays
    {
        private final QuadCurve arrowFrom;
        private final HBox name;
        private final QuadCurve arrowTo;

        public Overlays(List<TableDisplay> sources, String text, TableDisplay dest, FXPlatformRunnable edit)
        {
            Button button = new Button("Edit");
            button.setOnAction(e -> edit.run());
            name = new HBox(new Label(text), button);
            arrowFrom = new QuadCurve();
            arrowTo = new QuadCurve();
            Utility.addStyleClass(arrowFrom, "transformation-arrow");
            Utility.addStyleClass(arrowTo, "transformation-arrow");
            TableDisplay source = sources.get(0);
            // Find midpoint:
            ChangeListener<Object> recalculate = (a, b, c) -> {
                // ((minXA + maxXA)/2 + (minXB + maxXB)/2)/2
                // = (minXA + maxXA + minXB + maxXB)/4
                double midX = 0.25 * (
                    source.getBoundsInParent().getMinX() +
                    source.getBoundsInParent().getMaxX() +
                    dest.getBoundsInParent().getMinX() +
                    dest.getBoundsInParent().getMaxX());
                double midY = 0.25 * (
                    source.getBoundsInParent().getMinY() +
                        source.getBoundsInParent().getMaxY() +
                        dest.getBoundsInParent().getMinY() +
                        dest.getBoundsInParent().getMaxY());
                // TODO should be midpoint between edges really, not centres:
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
                arrowTo.setLayoutX(midX);
                arrowTo.setLayoutY(midY + 50);
                arrowTo.setControlX(midX - arrowTo.getLayoutX());
                arrowTo.setControlY(midY + 100 - arrowTo.getLayoutY());
                arrowTo.setEndX(to.getX() - arrowTo.getLayoutX());
                arrowTo.setEndY(to.getY() - arrowTo.getLayoutY());

            };
            source.layoutXProperty().addListener(recalculate);
            source.layoutYProperty().addListener(recalculate);
            source.widthProperty().addListener(recalculate);
            source.heightProperty().addListener(recalculate);

            dest.layoutXProperty().addListener(recalculate);
            dest.layoutYProperty().addListener(recalculate);
            dest.widthProperty().addListener(recalculate);
            dest.heightProperty().addListener(recalculate);

            recalculate.changed(new ReadOnlyBooleanWrapper(false), false, false);
        }
    }

    // The type of the listener really throws off the checkers so suppress them all:
    @SuppressWarnings({"initialization", "keyfor", "interning", "userindex", "valuetype", "helpfile"})
    public View(File location) throws InternalException, UserException
    {
        diskFile = new SimpleObjectProperty<>(location);
        tableManager = new TableManager(TransformationManager.getInstance(), this);
        mainPane = new Pane();
        pickPaneMouse = new Pane();
        pickPaneDisplay = new Pane();
        pickPaneDisplay.getStyleClass().add("view-pick-pane");
        getChildren().add(mainPane);
        currentPick = new SimpleObjectProperty<>(null);
        // Needs to pick up mouse events on mouse pane, not display pane:
        pickPaneMouse.setMouseTransparent(false);
        pickPaneDisplay.setMouseTransparent(true);
        FXUtility.addChangeListenerPlatform(currentPick, t -> {
            if (t != null)
            {
                Bounds b = ((TableDisplay)t.getDisplay()).getBoundsInParent();
                pickPaneDisplay.setClip(Shape.subtract(new Rectangle(mainPane.getWidth(), mainPane.getHeight()), new Rectangle(b.getMinX(), b.getMinY(), b.getWidth(), b.getHeight())));
                pickPaneMouse.setCursor(Cursor.HAND);
            }
            else
            {
                pickPaneDisplay.setClip(null);
                pickPaneMouse.setCursor(null);
            }
        });

        pickPaneMouse.setOnMouseMoved(e -> {
            @Nullable Table table = pickAt(e.getX(), e.getY());
            currentPick.setValue(table);
        });
        pickPaneMouse.setOnMouseClicked(e -> {
            @Nullable Table cur = currentPick.get();
            if (cur != null)
            {
                if (onPick != null)
                    onPick.consume(cur);
                getChildren().removeAll(pickPaneMouse, pickPaneDisplay);
            }
        });
        //TODO let escape cancel


        overlays = FXCollections.observableHashMap();
        overlays.addListener((MapChangeListener<? super Transformation, ? super Overlays>) c -> {
            Overlays removed = c.getValueRemoved();
            if (removed != null)
            {
                mainPane.getChildren().removeAll(removed.arrowFrom, removed.name, removed.arrowTo);
            }
            Overlays added = c.getValueAdded();
            if (added != null)
            {
                mainPane.getChildren().addAll(added.arrowFrom, added.name, added.arrowTo);
            }
        });
    }

    private @Nullable Table pickAt(double x, double y)
    {
        Point2D sceneLocation = localToScene(x, y);
        @Nullable Table picked = null;
        // This is paint order, so later nodes are drawn on top
        // Thus we just need to pick the last node in the list:
        for (Node node : mainPane.getChildren())
        {
            if (node instanceof TableDisplay && node.isVisible() && node.contains(node.sceneToLocal(sceneLocation)))
            {
                picked = ((TableDisplay)node).getTable();
            }
        }
        return picked;
    }

    @OnThread(Tag.Any)
    @Override
    public void addSource(DataSource data)
    {
        Platform.runLater(() -> {
            addDisplay(new TableDisplay(this, data), null);
            save();
        });
    }

    @OnThread(Tag.Any)
    @Override
    public void addTransformation(Transformation transformation)
    {
        Platform.runLater(() ->
        {
            TableDisplay tableDisplay = new TableDisplay(this, transformation);
            addDisplay(tableDisplay, getTableDisplayOrNull(transformation.getSources().get(0)));

            List<TableDisplay> sourceDisplays = new ArrayList<>();
            for (TableId t : transformation.getSources())
            {
                TableDisplay td = getTableDisplayOrNull(t);
                if (td != null)
                    sourceDisplays.add(td);
            }
            overlays.put(transformation, new Overlays(sourceDisplays, transformation.getTransformationLabel(), tableDisplay, () ->
            {
                View.this.edit(transformation.getId(), ((TransformationEditable)transformation).edit(View.this));
            }));

            save();
        });
    }

    private void addDisplay(TableDisplay tableDisplay, @Nullable TableDisplay alignToRightOf)
    {
        mainPane.getChildren().add(tableDisplay);
        if (alignToRightOf != null)
        {
            tableDisplay.setLayoutX(alignToRightOf.getLayoutX() + alignToRightOf.getWidth() + DEFAULT_SPACE);
            tableDisplay.setLayoutY(alignToRightOf.getLayoutY());
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

    public void edit(TableId existingTableId, TransformationEditor selectedEditor)
    {
        EditTransformationDialog dialog = new EditTransformationDialog(getWindow(), this, selectedEditor);
        showEditDialog(dialog, existingTableId);
    }

    @SuppressWarnings("nullness") // Can't be a View without an actual window
    private Window getWindow()
    {
        return getScene().getWindow();
    }

    public void edit(Table src)
    {
        EditTransformationDialog dialog = new EditTransformationDialog(getWindow(), this, src.getId());
        showEditDialog(dialog, null);
    }

    private void showEditDialog(EditTransformationDialog dialog, @Nullable TableId replaceOnOK)
    {
        // add will re-run any dependencies:
        dialog.show(optNewTable -> optNewTable.ifPresent(t -> Workers.onWorkerThread("Updating tables", () -> Utility.alertOnError_(() -> tableManager.edit(replaceOnOK, t)))));
    }

    @Override
    @OnThread(Tag.FX)
    public void requestFocus()
    {
        // Don't allow focus
    }

    @OnThread(Tag.FXPlatform)
    public void pickTable(FXPlatformConsumer<@Nullable Table> whenPicked)
    {
        // Reset the clip:
        pickPaneDisplay.setClip(null);
        currentPick.setValue(null);

        getChildren().addAll(pickPaneDisplay, pickPaneMouse);
        onPick = whenPicked;
    }

    public ObjectExpression<String> titleProperty()
    {
        return FXUtility.mapBindingLazy(diskFile, f -> f.getName() + " [" + f.getParent() + "]");
    }
}

