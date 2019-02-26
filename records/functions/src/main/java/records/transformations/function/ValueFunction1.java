package records.transformations.function;

import annotation.qual.Value;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import records.transformations.expression.function.ValueFunction;

/**
 * A helper extension for ValueFunction which does the casting
 * for you.
 */
public abstract class ValueFunction1<A> extends ValueFunction
{
    private final Class<A> classA;

    public ValueFunction1(Class<A> classA)
    {
        this.classA = classA;
    }

    @Override
    @OnThread(Tag.Simulation)
    public final @Value Object _call() throws InternalException, UserException
    {
        return call1(arg(0, classA));
    }

    @OnThread(Tag.Simulation)
    public abstract @Value Object call1(@Value A a) throws InternalException, UserException;
}
