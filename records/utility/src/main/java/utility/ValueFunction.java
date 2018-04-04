package utility;

import annotation.qual.Value;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

// I want to label this class @Value but I don't seem able to.  Perhaps because it is abstract?
public abstract class ValueFunction
{
    @OnThread(Tag.Simulation)
    public abstract @Value Object call(@Value Object arg) throws InternalException, UserException;
}
