package utility;

import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 24/10/2016.
 */
public interface FXPlatformSupplier<T>
{
    @OnThread(Tag.FXPlatform)
    public T get();
}
