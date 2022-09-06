package utility;

import records.error.InternalException;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 24/10/2016.
 */
public interface FXPlatformSupplierInt<T>
{
    @OnThread(Tag.FXPlatform)
    public T get() throws InternalException;
}
