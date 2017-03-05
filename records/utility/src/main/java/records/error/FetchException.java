package records.error;

import java.io.IOException;

/**
 * Created by neil on 22/10/2016.
 */
public class FetchException extends UserException
{
    public FetchException(String message, IOException e)
    {
        super(message, e);
    }

    public FetchException(String message, NumberFormatException cause)
    {
        super(message, cause);
    }
}
