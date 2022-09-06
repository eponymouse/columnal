package utility.gui;

import javafx.scene.control.Dialog;
import javafx.stage.Window;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.FXPlatformFunction;

public interface DimmableParent
{
    @OnThread(Tag.FXPlatform)
    public Window dimWhileShowing(@UnknownInitialization(Dialog.class) Dialog<?> dialog);

    // Only use for things like file choosers where you can't get a reference to the dialog.
    @OnThread(Tag.FXPlatform)
    public <T> T dimAndWait(FXPlatformFunction<Window, T> showAndWait);
    
    public static class Undimmed implements DimmableParent
    {
        private final Window window;

        public Undimmed(Window w)
        {
            this.window = w;
        }

        @Override
        public @OnThread(Tag.FXPlatform) Window dimWhileShowing(@UnknownInitialization(Dialog.class) Dialog<?> dialog)
        {
            return window;
        }

        @Override
        public <T> @OnThread(Tag.FXPlatform) T dimAndWait(FXPlatformFunction<Window, T> showAndWait)
        {
            return showAndWait.apply(window);
        }
    }
}
