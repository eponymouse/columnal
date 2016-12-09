package records.gui;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.geometry.Point2D;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.shape.QuadCurve;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.DataSource;
import records.data.Table;
import records.data.TableId;
import records.data.TableManager;
import records.data.Transformation;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.TransformationEditor;
import records.transformations.TransformationManager;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.FXPlatformRunnable;
import utility.GraphUtility;
import utility.Utility;
import utility.Workers;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by neil on 18/10/2016.
 */
@OnThread(Tag.FXPlatform)
public class View extends Pane
{
    private static final double DEFAULT_SPACE = 150.0;

    private final ObservableMap<Transformation, Overlays> overlays;
    @OnThread(value = Tag.Any,requireSynchronized = true)
    private final List<DataSource> sources = new ArrayList<>();
    @OnThread(value = Tag.Any,requireSynchronized = true)
    private final List<Transformation> transformations = new ArrayList<>();
    private final TableManager tableManager;

    // Does not write to that destination, just uses it for relative paths
    public void save(@Nullable File destination, FXPlatformConsumer<String> then)
    {
        class Fetcher implements FXPlatformConsumer<String>
        {
            private final List<String> all = new ArrayList<>();
            private final Iterator<Table> it;

            public Fetcher(List<Table> allTables)
            {
                it = allTables.iterator();
            }

            @Override
            public @OnThread(Tag.FXPlatform) void consume(String s)
            {
                all.add(s);
                getNext();
            }

            @OnThread(Tag.FXPlatform)
            private void getNext()
            {
                if (it.hasNext())
                {
                    it.next().save(destination, this);
                }
                else
                    then.consume(all.stream().collect(Collectors.joining("\n\n")));
            }
        };
        new Fetcher(getAllTables()).getNext();
    }

    @OnThread(Tag.Any)
    @NonNull
    private synchronized List<Table> getAllTables()
    {
        List<Table> all = new ArrayList<>();
        all.addAll(sources);
        all.addAll(transformations);
        return all;
    }

    @OnThread(Tag.Any)
    public TableManager getManager()
    {
        return tableManager;
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

    @SuppressWarnings({"initialization", "keyfor", "interning"})
    public View() throws InternalException, UserException
    {
        tableManager = new TableManager();
        overlays = FXCollections.observableHashMap();
        overlays.addListener((MapChangeListener<? super Transformation, ? super Overlays>) c -> {
            Overlays removed = c.getValueRemoved();
            if (removed != null)
            {
                getChildren().removeAll(removed.arrowFrom, removed.name, removed.arrowTo);
            }
            Overlays added = c.getValueAdded();
            if (added != null)
            {
                getChildren().addAll(added.arrowFrom, added.name, added.arrowTo);
            }
        });
    }

    public void addSource(DataSource data)
    {
        synchronized (this)
        {
            sources.add(data);
        }
        addDisplay(new TableDisplay(this, data), null);
    }

    public TableDisplay addTransformation(Transformation transformation)
    {
        synchronized (this)
        {
            transformations.add(transformation);
        }
        TableDisplay tableDisplay = new TableDisplay(this, transformation);
        addDisplay(tableDisplay, getTableDisplayOrNull(transformation.getSources().get(0)));

        List<TableDisplay> sourceDisplays = new ArrayList<>();
        for (TableId t : transformation.getSources())
        {
            TableDisplay td = getTableDisplayOrNull(t);
            if (td != null)
                sourceDisplays.add(td);
        }
        overlays.put(transformation, new Overlays(sourceDisplays, transformation.getTransformationLabel(), tableDisplay, () -> {
            View.this.edit(transformation.getId(), transformation.edit());
        }));

        return tableDisplay;
    }

    private void addDisplay(TableDisplay tableDisplay, @Nullable TableDisplay alignToRightOf)
    {
        getChildren().add(tableDisplay);
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
            return table.getDisplay();
    }

    public void add(@Nullable TableId replaceTableId, Transformation transformation) throws InternalException, UserException
    {
        Map<TableId, List<TableId>> edges = new HashMap<>();
        HashSet<TableId> affected = new HashSet<>();
        affected.add(transformation.getId());
        if (replaceTableId != null)
            affected.add(replaceTableId);
        HashSet<TableId> allIds = new HashSet<>();
        synchronized (this)
        {
            for (Table t : sources)
                allIds.add(t.getId());
            for (Transformation t : transformations)
            {
                allIds.add(t.getId());
                edges.put(t.getId(), t.getSources());
            }
        }
        allIds.addAll(affected);
        List<TableId> linearised = GraphUtility.lineariseDAG(allIds, edges, affected);

        // Find first affected:
        int processFrom = affected.stream().mapToInt(linearised::indexOf).min().orElse(-1);
        // If it's not in affected itself, serialise it:
        List<String> reRun = new ArrayList<>();
        AtomicInteger toSave = new AtomicInteger(1); // Keep one extra until we've lined up all jobs
        CompletableFuture<List<String>> savedToReRun = new CompletableFuture<>();
        for (int i = processFrom; i < linearised.size(); i++)
        {
            if (!affected.contains(linearised.get(i)))
            {
                // Add job:
                toSave.incrementAndGet();
                removeAndSerialise(linearised.get(i), script -> {
                    reRun.add(script);
                    if (toSave.decrementAndGet() == 0)
                    {
                        // Saved all of them
                        savedToReRun.complete(reRun);
                    }
                });
            }
        }
        if (toSave.decrementAndGet() == 0) // Remove extra; can complete now when hits zero
        {
            savedToReRun.complete(reRun);
        }

        synchronized (this)
        {
            if (replaceTableId != null)
                removeAndSerialise(replaceTableId, s -> {});
        }
        addTransformation(transformation);

        savedToReRun.thenAccept(ss -> {
            Utility.alertOnErrorFX_(() -> reAddAll(ss));
        });
    }

    private void reAddAll(List<String> scripts) throws UserException, InternalException
    {
        for (String script : scripts)
        {
            Workers.onWorkerThread("Re-running affected table", () -> Utility.alertOnError_(() -> {
                Transformation transformation = TransformationManager.getInstance().loadOne(tableManager, script);
                Platform.runLater(() -> addTransformation(transformation));
            }));

        }
    }

    private void removeAndSerialise(TableId tableId, FXPlatformConsumer<String> then)
    {
        Table removed = null;
        synchronized (this)
        {
            for (Iterator<DataSource> iterator = sources.iterator(); iterator.hasNext(); )
            {
                Table t = iterator.next();
                if (t.getId().equals(tableId))
                {
                    iterator.remove();
                    removed = t;
                    break;
                }
            }
            if (removed == null)
                for (Iterator<Transformation> iterator = transformations.iterator(); iterator.hasNext(); )
                {
                    Transformation t = iterator.next();
                    if (t.getId().equals(tableId))
                    {
                        iterator.remove();
                        overlays.remove(t); // Listener removes them from display
                        removed = t;
                        break;
                    }
                }
        }
        if (removed != null)
        {
            getChildren().remove(removed.getDisplay());
            removed.save(null, then);
        }
    }

    public void edit(TableId existingTableId, TransformationEditor selectedEditor)
    {
        EditTransformationDialog dialog = new EditTransformationDialog(getScene().getWindow(), this, selectedEditor);
        showEditDialog(dialog, existingTableId);
    }

    public void edit(Table src)
    {
        EditTransformationDialog dialog = new EditTransformationDialog(getScene().getWindow(), this, src);
        showEditDialog(dialog, null);
    }

    private void showEditDialog(EditTransformationDialog dialog, @Nullable TableId replaceOnOK)
    {
        // add will re-run any dependencies:
        dialog.show(optNewTable -> optNewTable.ifPresent(t -> Utility.alertOnErrorFX_(() -> add(replaceOnOK, t))));
    }

    @Override
    @OnThread(Tag.FX)
    public void requestFocus()
    {
        // Don't allow focus
    }
}
