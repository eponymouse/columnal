package records.transformations.expression;

import records.gui.expressioneditor.Consecutive;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.OperatorEntry;
import utility.FXPlatformFunction;
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
    public Pair<List<FXPlatformFunction<Consecutive, OperandNode>>, List<FXPlatformFunction<Consecutive, OperatorEntry>>> loadAsConsecutive()
    {
        return new Pair<>(Collections.singletonList(loadAsSingle()), Collections.emptyList());
    }
}
