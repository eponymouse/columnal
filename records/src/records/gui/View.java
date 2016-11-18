package records.gui;

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
import records.data.TableManager;
import records.data.Transformation;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.TransformationInfo.TransformationEditor;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.FXPlatformRunnable;
import utility.Utility;

import java.io.File;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
    private final Map<Table, TableDisplay> tableDisplays = new IdentityHashMap<>();
    private final TableManager tableManager = new TableManager();

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

        public Overlays(TableDisplay source, String text, TableDisplay dest, FXPlatformRunnable edit)
        {
            Button button = new Button("Edit");
            button.setOnAction(e -> edit.run());
            name = new HBox(new Label(text), button);
            arrowFrom = new QuadCurve();
            arrowTo = new QuadCurve();
            Utility.addStyleClass(arrowFrom, "transformation-arrow");
            Utility.addStyleClass(arrowTo, "transformation-arrow");
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
    public View()
    {
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

    public void add(DataSource data, @Nullable Table alignToRightOf) throws InternalException
    {
        synchronized (this)
        {
            sources.add(data);
        }
        add(new TableDisplay(this, data), alignToRightOf);
    }

    private void add(TableDisplay tableDisplay, @Nullable Table alignToRightOf) throws InternalException
    {
        tableDisplays.put(tableDisplay.getTable(), tableDisplay);
        getChildren().add(tableDisplay);
        if (alignToRightOf != null)
        {
            TableDisplay alignToRightOfDisplay = getTableDisplay(alignToRightOf);
            tableDisplay.setLayoutX(alignToRightOfDisplay.getLayoutX() + alignToRightOfDisplay.getWidth() + DEFAULT_SPACE);
            tableDisplay.setLayoutY(alignToRightOfDisplay.getLayoutY());
        }
    }

    private TableDisplay getTableDisplay(Table table) throws InternalException
    {
        TableDisplay display = tableDisplays.get(table);
        if (display == null)
            throw new InternalException("Could not find display for table: \"" + table.getId() + "\"");
        return display;
    }

    public void add(Transformation transformation) throws InternalException
    {
        synchronized (this)
        {
            transformations.add(transformation);
        }
        TableDisplay tableDisplay = new TableDisplay(this, transformation);
        add(tableDisplay, transformation);
        try
        {
            overlays.put(transformation, new Overlays(getTableDisplay(transformation.getSource()), transformation.getTransformationLabel(), tableDisplay, () -> {
                View.this.edit(transformation.edit());
            }));
        }
        catch (UserException e)
        {
            // We just don't add the overlays if there is a problem finding the source
            // Don't show the error as the user has probably already seen it.
        }


    }

    public void edit(TransformationEditor selectedEditor)
    {
        EditTransformationDialog dialog = new EditTransformationDialog(getScene().getWindow(), this, selectedEditor);
        showEditDialog(dialog);
    }

    public void edit(Table src)
    {
        EditTransformationDialog dialog = new EditTransformationDialog(getScene().getWindow(), this, src);
        showEditDialog(dialog);
    }

    private void showEditDialog(EditTransformationDialog dialog)
    {
        // TODO re-run any dependencies
        dialog.show(optNewTable -> optNewTable.ifPresent(t -> Utility.alertOnErrorFX_(() -> add(t))));
    }

    @Override
    @OnThread(Tag.FX)
    public void requestFocus()
    {
        // Don't allow focus
    }
}
