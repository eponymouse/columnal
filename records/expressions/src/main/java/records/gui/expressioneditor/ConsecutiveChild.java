package records.gui.expressioneditor;

import javafx.geometry.Point2D;
import javafx.scene.Node;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.transformations.expression.LoadableExpression;
import styled.StyledShowable;
import utility.Pair;
import utility.gui.FXUtility;

/**
 * A child of a ConsecutiveBase item.  Has methods for selection, dragging and focusing.
 */
public interface ConsecutiveChild<EXPRESSION extends LoadableExpression<EXPRESSION, SEMANTIC_PARENT>, SEMANTIC_PARENT> extends EEDisplayNode
{
    @Pure
    public ConsecutiveBase<EXPRESSION, SEMANTIC_PARENT> getParent();

    void setSelected(boolean selected);

    <C extends LoadableExpression<C, ?>> @Nullable Pair<ConsecutiveChild<? extends C, ?>, Double> findClosestDrop(Point2D loc, Class<C> forType);

    // Ideally this would be protected access:
    @SuppressWarnings("unchecked")
    public static <US extends LoadableExpression<US, ?>, TARGET extends LoadableExpression<TARGET, ?>> @Nullable Pair<ConsecutiveChild<? extends TARGET, ?>, Double> closestDropSingle(ConsecutiveChild<US, ?> us, Class<US> ourClass, Node node, Point2D loc, Class<TARGET> forType)
    {
        if (forType.isAssignableFrom(ourClass))
            return new Pair<ConsecutiveChild<? extends TARGET, ?>, Double>((ConsecutiveChild)us, FXUtility.distanceToLeft(node, loc));
        else
            return null;
    }
    
    void setHoverDropLeft(boolean on);

    default boolean isBlank() { return false; }

    void focusChanged();
}
