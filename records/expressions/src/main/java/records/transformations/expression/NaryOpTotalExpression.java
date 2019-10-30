package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.util.List;

/**
 * A subclass of {@link NaryOpExpression} for expressions that use
 * all their arguments for calculation every time (e.g.
 * times expression) as opposed to being able to short-circuit
 * (like in an and expression).
 */
public abstract class NaryOpTotalExpression extends NaryOpExpression
{
    public NaryOpTotalExpression(List<@Recorded Expression> expressions)
    {
        super(expressions);
    }

    @Override
    @OnThread(Tag.Simulation)
    public final ValueResult calculateValue(EvaluateState state) throws EvaluationException, InternalException
    {
        if (expressions.stream().anyMatch(e -> e instanceof ImplicitLambdaArg))
        {
            return ImplicitLambdaArg.makeImplicitFunction(this, expressions, state, s -> {
                ImmutableList.Builder<ValueResult> expressionValues = ImmutableList.builderWithExpectedSize(expressions.size());
                for (Expression expression : expressions)
                {
                    fetchSubExpression(expression, s, expressionValues);
                }
                return getValueNaryOp(expressionValues.build(), s);
            });
        }
        else
        {
            ImmutableList.Builder<ValueResult> expressionValues = ImmutableList.builderWithExpectedSize(expressions.size());
            for (Expression expression : expressions)
            {
                fetchSubExpression(expression, state, expressionValues);
            }
            return getValueNaryOp(expressionValues.build(), state);
        }
    }

    @OnThread(Tag.Simulation)
    public abstract ValueResult getValueNaryOp(ImmutableList<ValueResult> expressionValues, EvaluateState state) throws EvaluationException, InternalException;
}
