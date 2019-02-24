package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

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
    public final ValueResult calculateValue(EvaluateState state) throws UserException, InternalException
    {
        if (expressions.stream().anyMatch(e -> e instanceof ImplicitLambdaArg))
        {
            return new ValueResult(ImplicitLambdaArg.makeImplicitFunction(expressions, state, s -> getValueNaryOp(s).getFirst()), expressions);
        }
        else
        {
            Pair<@Value Object, EvaluateState> p = getValueNaryOp(state);
            return new ValueResult(p.getFirst(), p.getSecond(), expressions);
        }
    }

    @OnThread(Tag.Simulation)
    public abstract Pair<@Value Object, EvaluateState> getValueNaryOp(EvaluateState state) throws UserException, InternalException;
}
