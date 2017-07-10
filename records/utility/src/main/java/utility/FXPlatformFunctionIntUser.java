package utility;

import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 24/10/2016.
 */
public interface FXPlatformFunctionIntUser<T, R>
{
    @OnThread(Tag.FXPlatform)
    public R apply(T t) throws InternalException, UserException;
}
