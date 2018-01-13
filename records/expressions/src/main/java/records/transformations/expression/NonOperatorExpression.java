package records.transformations.expression;

import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.OperatorEntry;
import utility.Pair;

import java.util.Collections;
import java.util.List;

/**
 * Any expression which does not load directly into a consecutive, and if needed,
 * will appear in a consecutive as a single item.
 */
public abstract class NonOperatorExpression extends Expression
{
    @Override
    public final Pair<List<SingleLoader<Expression, ExpressionNodeParent, OperandNode<Expression, ExpressionNodeParent>>>, List<SingleLoader<Expression, ExpressionNodeParent, OperatorEntry<Expression, ExpressionNodeParent>>>> loadAsConsecutive(boolean implicitlyRoundBracketed)
    {
        return new Pair<>(Collections.singletonList(loadAsSingle()), Collections.emptyList());
    }
}
