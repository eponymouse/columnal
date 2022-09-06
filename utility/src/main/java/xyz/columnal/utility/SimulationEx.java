package xyz.columnal.utility;

import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

public interface SimulationEx
{
    @OnThread(Tag.Simulation)
    public void run() throws UserException, InternalException;
}
