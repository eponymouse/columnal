package utility;

import records.error.InternalException;
import records.error.UserException;

/**
 * Created by neil on 16/11/2016.
 */
public interface ExBiFunction<S, T, R>
{
    public R apply(S s, T t) throws UserException, InternalException;
}
