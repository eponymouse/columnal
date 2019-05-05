package utility.gui;

import javafx.scene.control.Dialog;
import javafx.stage.Window;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import threadchecker.OnThread;
import threadchecker.Tag;

public interface DimmableParent
{
    @OnThread(Tag.FXPlatform)
    public Window dimWhileShowing(@UnknownInitialization(Dialog.class) Dialog<?> dialog);
}
