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
public interface SimulationConsumerNoError<T>
{
    public void consume(T t);
}
