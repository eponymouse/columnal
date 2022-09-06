package utility;

import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 24/10/2016.
 */
public interface SimulationFunctionInt<T, R>
{
    @OnThread(Tag.Simulation)
    public R apply(T t) throws InternalException;
}
