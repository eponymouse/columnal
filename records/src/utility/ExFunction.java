package utility;

import records.error.InternalException;
import records.error.UserException;

/**
 * Created by neil on 16/11/2016.
 */
public interface ExFunction<T, R>
{
    public R apply(T t) throws UserException, InternalException;
}
