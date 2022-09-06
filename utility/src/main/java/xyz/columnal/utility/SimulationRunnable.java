package xyz.columnal.utility;

import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 02/11/2016.
 */
@FunctionalInterface
@OnThread(Tag.Simulation)
public interface SimulationRunnable
{
    public void run() throws InternalException;
}
