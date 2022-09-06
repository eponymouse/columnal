package utility;

import records.error.InternalException;
import records.error.UserException;

/**
 * A version of Runnable which throws exceptions
 */
public interface ExRunnable
{
    public void run() throws InternalException, UserException;
}
