package records.error;

import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 22/10/2016.
 */
@OnThread(Tag.Any)
public class UserException extends Exception
{
    public UserException(String message)
    {
        super(message);
    }

    protected UserException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
