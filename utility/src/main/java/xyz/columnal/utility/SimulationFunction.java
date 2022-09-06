package xyz.columnal.utility;

import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 24/10/2016.
 */
public interface SimulationFunction<T, R>
{
    @OnThread(Tag.Simulation)
    public R apply(T t) throws InternalException, UserException;
}
