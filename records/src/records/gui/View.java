package records.gui;

import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.geometry.Point2D;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.shape.QuadCurve;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.DataSource;
import records.data.Transformation;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by neil on 18/10/2016.
 */
@OnThread(Tag.FXPlatform)
public class View extends Pane
{
    private static final double DEFAULT_SPACE = 150.0;

    private final ObservableMap<Transformation, Overlays> overlays;
    private final List<DataSource> sources = new ArrayList<>();
    private final List<Transformation> transformations = new ArrayList<>();

    // Does not write to that destination, just uses it for relative paths
    public String save(@Nullable File destination)
    {
        return Stream.concat(sources.stream(), transformations.stream()).map(s -> s.save(destination)).collect(Collectors.joining("\n\n"));
    }

    @OnThread(Tag.FXPlatform)
    private static class Overlays
    {
        private final QuadCurve arrowFrom;
        private final Label name;
        private final QuadCurve arrowTo;

        public Overlays(Table source, String text, Table dest)
        {
            name = new Label(text);
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

    // TODO replace Table here with a new DataSource class,
    // i.e. all tables are datasource or transformation
    public void add(DataSource data, @Nullable Table alignToRightOf)
    {
        sources.add(data);
        add(new Table(this, data.getData()), alignToRightOf);
    }

    private void add(Table table, @Nullable Table alignToRightOf)
    {
        getChildren().add(table);
        if (alignToRightOf != null)
        {
            table.setLayoutX(alignToRightOf.getLayoutX() + alignToRightOf.getWidth() + DEFAULT_SPACE);
            table.setLayoutY(alignToRightOf.getLayoutY());
        }
    }

    public void add(Transformation transformation)
    {
        transformations.add(transformation);
        Table table = new Table(this, transformation.getData());
        add(table, transformation.getSource());
        overlays.put(transformation, new Overlays(transformation.getSource(), transformation.getTransformationLabel(), table));

    }

    @Override
    @OnThread(Tag.FX)
    public void requestFocus()
    {
        // Don't allow focus
    }
}
