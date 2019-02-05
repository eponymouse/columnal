package utility.gui;

import javafx.scene.control.Dialog;
import javafx.stage.Window;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;

public interface DimmableParent
{
    public Window dimWhileShowing(@UnknownInitialization(Dialog.class) Dialog<?> dialog);
}
