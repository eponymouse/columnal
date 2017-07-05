package utility;

import records.error.InternalException;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 24/10/2016.
 */
public interface FXPlatformBiFunctionInt<T, U, R>
{
    @OnThread(Tag.FXPlatform)
    public R apply(T t, U u) throws InternalException;
}
