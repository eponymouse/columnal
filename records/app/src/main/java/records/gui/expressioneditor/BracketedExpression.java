package records.gui.expressioneditor;

import javafx.beans.value.ObservableObjectValue;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.Expression;
import utility.FXPlatformFunction;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.Comparator;
import java.util.List;

/**
 * Created by neil on 20/12/2016.
 */
public class BracketedExpression extends Consecutive<Expression, ExpressionNodeParent> implements OperandNode<Expression>, ExpressionNodeParent
{
    private final ExpressionNodeParent semanticParent;

    public BracketedExpression(OperandOps<Expression, ExpressionNodeParent> operations, ExpressionNodeParent semanticParent, EEDisplayNodeParent expParent, @Nullable Node prefixNode, @Nullable Node suffixNode, @Nullable Pair<List<FXPlatformFunction<ConsecutiveBase<Expression, ExpressionNodeParent>, OperandNode<Expression>>>, List<FXPlatformFunction<ConsecutiveBase<Expression, ExpressionNodeParent>, OperatorEntry<Expression, ExpressionNodeParent>>>> content)
    {
        super(operations, expParent, prefixNode, suffixNode, "bracket", content, ')');
        this.semanticParent = semanticParent;
    }

    @Override
    protected ExpressionNodeParent getThisAsSemanticParent()
    {
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
    public ConsecutiveBase<Expression, ExpressionNodeParent> getParent()
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
    protected List<Pair<DataType, List<String>>> getSuggestedParentContext() throws UserException, InternalException
    {
        return semanticParent.getSuggestedContext(this);
    }

    @Override
    public <C> @Nullable Pair<ConsecutiveChild<? extends C>, Double> findClosestDrop(Point2D loc, Class<C> forType)
    {
        return Utility.streamNullable(ConsecutiveChild.closestDropSingle(this, operations.getOperandClass(), nodes().get(0), loc, forType), super.findClosestDrop(loc, forType)).min(Comparator.comparing(p -> p.getSecond())).orElse(null);
    }
}
