package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.Expression;
import records.transformations.expression.LoadableExpression;
import styled.StyledShowable;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.gui.FXUtility;

import java.util.stream.Stream;

/**
 * A child of a ConsecutiveBase item.  Has methods for selection, dragging and focusing.
 */
public interface ConsecutiveChild<EXPRESSION extends StyledShowable, SEMANTIC_PARENT> extends EEDisplayNode, Locatable, ErrorDisplayer<EXPRESSION, SEMANTIC_PARENT>
{
    @Pure
    public ConsecutiveBase<EXPRESSION, SEMANTIC_PARENT> getParent();

    void setSelected(boolean selected);

    // Ideally this would be protected access:
    @SuppressWarnings("unchecked")
    public static <US extends StyledShowable, TARGET extends StyledShowable> @Nullable Pair<ConsecutiveChild<? extends TARGET, ?>, Double> closestDropSingle(ConsecutiveChild<US, ?> us, Class<US> ourClass, Node node, Point2D loc, Class<TARGET> forType)
    {
        if (forType.isAssignableFrom(ourClass))
            return new Pair<ConsecutiveChild<? extends TARGET, ?>, Double>((ConsecutiveChild)us, FXUtility.distanceToLeft(node, loc));
        else
            return null;
    }
    
    void setHoverDropLeft(boolean on);

    default boolean isBlank() { return false; }

    void focusChanged();

    @OnThread(Tag.FXPlatform)
    Stream<Pair<String, Boolean>> _test_getHeaders();

    public @Recorded EXPRESSION save(ErrorDisplayerRecord errorDisplayer, ErrorAndTypeRecorder onError);
}
