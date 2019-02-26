package records.transformations.expression;

import annotation.qual.Value;
import annotation.recorded.qual.Recorded;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.util.List;

public abstract class NaryOpShortCircuitExpression extends NaryOpExpression
{
    public NaryOpShortCircuitExpression(List<@Recorded Expression> expressions)
    {
        super(expressions);
    }

    @Override
    public final ValueResult calculateValue(EvaluateState state) throws UserException, InternalException
    {
        if (expressions.stream().anyMatch(e -> e instanceof ImplicitLambdaArg))
        {
            return ImplicitLambdaArg.makeImplicitFunction(this, expressions, state, s -> getValueNaryOp(s));
        }
        else
        {
            return getValueNaryOp(state);
        }
    }

    @OnThread(Tag.Simulation)
    public abstract ValueResult getValueNaryOp(EvaluateState state) throws UserException, InternalException;
}
