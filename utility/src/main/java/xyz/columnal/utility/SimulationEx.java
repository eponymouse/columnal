package utility;

import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

public interface SimulationEx
{
    @OnThread(Tag.Simulation)
    public void run() throws UserException, InternalException;
}
