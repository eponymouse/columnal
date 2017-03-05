package utility;

import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 18/11/2016.
 */
public interface FXPlatformRunnable
{
    @OnThread(Tag.FXPlatform)
    public void run();
}
