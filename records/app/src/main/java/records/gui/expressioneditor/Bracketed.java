package records.gui.expressioneditor;

import javafx.beans.value.ObservableObjectValue;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import utility.FXPlatformFunction;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Created by neil on 20/12/2016.
 */
public class Bracketed<EXPRESSION extends @NonNull Object> extends Consecutive<EXPRESSION> implements OperandNode<EXPRESSION>
{
    private final ConsecutiveBase<EXPRESSION> consecParent;

    public Bracketed(OperandOps<EXPRESSION> operations, ConsecutiveBase<EXPRESSION> consecParent, ExpressionParent expParent, @Nullable Node prefixNode, @Nullable Node suffixNode, @Nullable Pair<List<FXPlatformFunction<ConsecutiveBase<EXPRESSION>, OperandNode<EXPRESSION>>>, List<FXPlatformFunction<ConsecutiveBase<EXPRESSION>, OperatorEntry<EXPRESSION>>>> content)
    {
        super(operations, expParent, prefixNode, suffixNode, "bracket", content, ')');
        this.consecParent = consecParent;
    }

    @Override
    public Bracketed<EXPRESSION> focusWhenShown()
    {
        super.focusWhenShown();
        return this;
    }

    @Override
    public @Nullable ObservableObjectValue<@Nullable String> getStyleWhenInner()
    {
        return null;
    }

    @Override
    public boolean isFocused()
    {
        return childIsFocused();
    }

    @Override
    public ConsecutiveBase<EXPRESSION> getParent()
    {
        return consecParent;
    }

    @Override
    public void setSelected(boolean selected)
    {
        // Not sure how to style this, yet
    }

    @Override
    public void setHoverDropLeft(boolean on)
    {
        FXUtility.setPseudoclass(nodes().get(0), "exp-hover-drop-left", on);
    }

    @Override
    public Bracketed<EXPRESSION> prompt(String prompt)
    {
        super.prompt(prompt);
        return this;
    }

    @Override
    public <C> @Nullable Pair<ConsecutiveChild<? extends C>, Double> findClosestDrop(Point2D loc, Class<C> forType)
    {
        return Utility.streamNullable(ConsecutiveChild.closestDropSingle(this, operations.getOperandClass(), nodes().get(0), loc, forType), super.findClosestDrop(loc, forType)).min(Comparator.comparing(p -> p.getSecond())).orElse(null);
    }
}
