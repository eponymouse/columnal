package records.transformations.function;

import annotation.qual.Value;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 11/12/2016.
 */
public abstract class FunctionInstance
{
    @OnThread(Tag.Simulation)
    public abstract @Value Object getValue(int rowIndex, @Value Object param) throws UserException, InternalException;
}
