package utility;

import annotation.qual.Value;
import records.error.InternalException;
import records.error.UserException;

public @Value abstract class ValueFunction
{
    public abstract @Value Object call(@Value Object arg) throws InternalException, UserException;
}
