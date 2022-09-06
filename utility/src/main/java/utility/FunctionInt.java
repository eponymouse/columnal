package utility;

import records.error.InternalException;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 *
 */
@FunctionalInterface
public interface FunctionInt<T, R>
{
    public R apply(T param) throws InternalException;
}
