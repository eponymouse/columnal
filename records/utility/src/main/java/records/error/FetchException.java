package records.error;

import java.io.IOException;

/**
 * Exception thrown when there is an issue reading external data.
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
