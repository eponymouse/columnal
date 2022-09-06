package xyz.columnal.utility;

import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 24/10/2016.
 */
public interface FXPlatformConsumer<T>
{
    @OnThread(Tag.FXPlatform)
    public void consume(T t);
}
