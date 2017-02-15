package utility.gui;

import edu.emory.mathcs.backport.java.util.Collections;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.scene.control.ListCell;
import javafx.scene.control.SkinBase;
import javafx.util.Duration;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformRunnable;
import utility.Utility;

import java.util.List;

/**
 * Allows you to slide a list cell's content left or right during an animation.
 *
 * Note: only works with a graphic, not with text (just use a Label, if you want text).
 */
public class SlidableListCell<T> extends ListCell<T>
{
    @OnThread(Tag.FX)
    protected final DoubleProperty xPosition = new SimpleDoubleProperty(0);

    @SuppressWarnings("initialization")
    public SlidableListCell()
    {
        textProperty().addListener(c -> { throw new UnsupportedOperationException("SlidableListCell only supports graphic, not text.");});
        xPosition.addListener(c -> requestLayout());
    }

    @OnThread(Tag.FXPlatform)
    public static void animateOutToRight(List<? extends SlidableListCell<?>> cells, @Nullable FXPlatformRunnable after)
    {
        for (SlidableListCell<?> cell : cells)
        {
            cell.xPosition.setValue(0);
        }

        Timeline t = new Timeline(new KeyFrame(Duration.millis(200),
            Utility.mapList(cells, c -> new KeyValue(c.xPosition, c.getWidth())).toArray(new KeyValue[0])));

        if (after != null)
        {
            FXPlatformRunnable afterFinal = after;
            t.setOnFinished(e -> afterFinal.run());
        }
        t.play();
    }

    @OnThread(Tag.FXPlatform)
    public static void animateInFromLeft(List<? extends SlidableListCell<?>> cells, @Nullable FXPlatformRunnable after)
    {
        for (SlidableListCell<?> cell : cells)
        {
            cell.xPosition.setValue(-cell.getWidth());
        }

        Timeline t = new Timeline(new KeyFrame(Duration.millis(200),
            Utility.mapList(cells, c -> new KeyValue(c.xPosition, 0)).toArray(new KeyValue[0])));

        if (after != null)
        {
            FXPlatformRunnable afterFinal = after;
            t.setOnFinished(e -> afterFinal.run());
        }
        t.play();
    }

    @Override
    @OnThread(Tag.FX)
    protected void updateItem(T item, boolean empty)
    {
        super.updateItem(item, empty);
        xPosition.setValue(0);
    }

    @Override
    @OnThread(Tag.FX)
    protected void layoutChildren()
    {
        final double x = snappedLeftInset();
        final double y = snappedTopInset();
        final double w = snapSize(getWidth()) - x - snappedRightInset();
        final double h = snapSize(getHeight()) - y - snappedBottomInset();
        getGraphic().resizeRelocate(x + xPosition.get(), y, w - Math.max(0, xPosition.get()), h);
        //super.layoutChildren();
    }


}
