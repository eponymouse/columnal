package xyz.columnal.utility;

import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 24/10/2016.
 */
public interface FXPlatformBiConsumer<S, T>
{
    @OnThread(Tag.FXPlatform)
    public void consume(S s, T t);
}
