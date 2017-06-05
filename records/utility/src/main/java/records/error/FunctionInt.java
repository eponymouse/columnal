package records.error;

import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * TODO remove this in favour of ExFunction
 */
@FunctionalInterface
public interface FunctionInt<T, R>
{
    @OnThread(Tag.Simulation)
    public R apply(T param) throws InternalException;
}
