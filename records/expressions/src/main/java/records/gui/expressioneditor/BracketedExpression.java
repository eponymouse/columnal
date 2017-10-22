package records.gui.expressioneditor;

import javafx.beans.value.ObservableObjectValue;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
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
    private final ConsecutiveBase<Expression, ExpressionNodeParent> consecParent;

    public BracketedExpression(OperandOps<Expression, ExpressionNodeParent> operations, ConsecutiveBase<Expression, ExpressionNodeParent> parent, @Nullable Node prefixNode, @Nullable Node suffixNode, @Nullable ConsecutiveStartContent<Expression, ExpressionNodeParent> content, char endCharacter)
    {
        super(operations, parent, prefixNode, suffixNode, "bracket", content, endCharacter);
        this.consecParent = parent;
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
    protected BracketedStatus getChildrenBracketedStatus()
    {
        return BracketedStatus.DIRECT_ROUND_BRACKETED;
    }

    @Override
    public <C> @Nullable Pair<ConsecutiveChild<? extends C>, Double> findClosestDrop(Point2D loc, Class<C> forType)
    {
        return Utility.streamNullable(ConsecutiveChild.closestDropSingle(this, operations.getOperandClass(), nodes().get(0), loc, forType), super.findClosestDrop(loc, forType)).min(Comparator.comparing(p -> p.getSecond())).orElse(null);
    }

    @Override
    public List<Pair<DataType, List<String>>> getSuggestedContext(EEDisplayNode child) throws InternalException, UserException
    {
        // Bracketed context identical to parent context:
        return consecParent.getThisAsSemanticParent().getSuggestedContext(this);
    }

    @Override
    public List<Pair<String, @Nullable DataType>> getAvailableVariables(@UnknownInitialization EEDisplayNode child)
    {
        return consecParent.getThisAsSemanticParent().getAvailableVariables(this);
    }

    @Override
    public boolean canDeclareVariable(@UnknownInitialization EEDisplayNode child)
    {
        // So technically you can only declare a var if this is a tuple or array.  But
        // we don't know that until they've finished, so we just allow it if the parent allows it,
        // and generate an error later if it's not allowed:
        return consecParent.getThisAsSemanticParent().canDeclareVariable(this);
    }
}
