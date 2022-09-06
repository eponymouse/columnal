package xyz.columnal.utility;

import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;

/**
 * A version of Runnable which throws exceptions
 */
public interface ExRunnable
{
    public void run() throws InternalException, UserException;
}
