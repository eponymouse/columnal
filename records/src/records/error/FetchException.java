package records.error;

import java.io.IOException;

/**
 * Created by neil on 22/10/2016.
 */
public class FetchException extends UserException
{
    public FetchException(IOException e)
    {
        super("IOException: " + e.getLocalizedMessage(), e);
    }
}
