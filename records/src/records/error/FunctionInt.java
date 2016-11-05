package records.error;

import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 31/10/2016.
 */
@FunctionalInterface
public interface FunctionInt<T, R>
{
    @OnThread(Tag.Simulation)
    public R apply(T param) throws InternalException, UserException;
}
