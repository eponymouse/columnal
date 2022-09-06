package utility;

import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 24/10/2016.
 */
public interface FXPlatformFunction<T, R>
{
    @OnThread(Tag.FXPlatform)
    public R apply(T t);
}
