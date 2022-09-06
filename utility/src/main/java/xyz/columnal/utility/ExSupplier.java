package xyz.columnal.utility;

import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;

/**
 * Created by neil on 16/11/2016.
 */
@FunctionalInterface
public interface ExSupplier<R>
{
    public R get() throws UserException, InternalException;
}
