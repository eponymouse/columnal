package utility;

import annotation.qual.Value;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

public abstract @Value class ValueFunction
{
    @OnThread(Tag.Simulation)
    public abstract @Value Object call(@Value Object arg) throws InternalException, UserException;
}
