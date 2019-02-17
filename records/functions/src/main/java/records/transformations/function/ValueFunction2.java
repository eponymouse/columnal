package records.transformations.function;

import annotation.qual.Value;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;
import records.data.ValueFunction;

/**
 * A helper extension for ValueFunction which does the casting
 * for you.
 */
public abstract class ValueFunction2<A, B> extends ValueFunction
{
    private final Class<A> classA;
    private final Class<B> classB;

    public ValueFunction2(Class<A> classA, Class<B> classB)
    {
        this.classA = classA;
        this.classB = classB;
    }

    @Override
    @OnThread(Tag.Simulation)
    public final @Value Object call() throws InternalException, UserException
    {
        return call2(arg(0, classA), arg(1, classB));
    }

    @OnThread(Tag.Simulation)
    public abstract @Value Object call2(@Value A a, @Value B b) throws InternalException, UserException;
}
