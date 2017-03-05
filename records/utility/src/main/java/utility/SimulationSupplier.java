package utility;

import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 02/11/2016.
 */
@FunctionalInterface
@OnThread(Tag.Simulation)
public interface SimulationSupplier<T>
{
    public T get() throws InternalException, UserException;
}
