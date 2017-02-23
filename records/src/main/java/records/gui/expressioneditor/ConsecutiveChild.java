package records.gui.expressioneditor;

import javafx.geometry.Point2D;
import org.checkerframework.dataflow.qual.Pure;
import utility.Pair;

/**
 * Created by neil on 19/02/2017.
 */
public interface ConsecutiveChild
{
    @Pure
    public ConsecutiveBase getParent();

    void setSelected(boolean selected);

    Pair<ConsecutiveChild, Double> findClosestDrop(Point2D loc);

    void setHoverDropLeft(boolean on);

    default boolean isBlank() { return false; }

    void focusChanged();

    boolean isFocused();
}
