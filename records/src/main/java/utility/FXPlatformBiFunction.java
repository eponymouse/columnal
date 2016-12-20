package utility;

import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 24/10/2016.
 */
public interface FXPlatformBiFunction<S, T, R>
{
    @OnThread(Tag.FXPlatform)
    public R apply(S s, T t);
}
