package records.error;

import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 22/10/2016.
 */
public class InternalException extends Exception
{
    public InternalException(String message)
    {
        super(message);
    }

    public InternalException(String message, Exception e)
    {
        super(message, e);
    }
}
