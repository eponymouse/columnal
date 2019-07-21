package test.gui.trait;

import javafx.stage.Window;
import threadchecker.OnThread;
import threadchecker.Tag;

public interface ScreenshotTrait
{
    @OnThread(Tag.FXPlatform)
    public void dumpScreenshot();

    @OnThread(Tag.FXPlatform)
    public void dumpScreenshot(Window target);
}
