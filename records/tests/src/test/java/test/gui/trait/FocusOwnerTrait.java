package test.gui.trait;

import javafx.scene.Node;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.testfx.api.FxRobotInterface;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;

public interface FocusOwnerTrait extends FxRobotInterface
{
    @OnThread(Tag.Any)
    default @Nullable Node getFocusOwner()
    {
        Window curWindowFinal = TestUtil.fx(() -> getRealFocusedWindow());
        return TestUtil.fx(() -> curWindowFinal.getScene().getFocusOwner());
    }

    @OnThread(Tag.Any)
    default Window fxGetRealFocusedWindow()
    {
        return TestUtil.fx(this::getRealFocusedWindow);
    }
    

    // Ignores PopupWindow
    @OnThread(Tag.FXPlatform)
    default Window getRealFocusedWindow()
    {
        // The only children of Window are PopupWindow, Stage and EmbeddedWindow.
        // We are not interested in popup or embedded so we may as well
        // filter down to Stage:
        List<Stage> curWindow = new ArrayList<>(
            Utility.filterClass(
                listWindows().stream().filter(Window::isFocused),
                Stage.class)
            .collect(Collectors.toList()));
        if (curWindow.isEmpty())
            throw new RuntimeException("No focused window?!");
        // It seems that (only in Monocle?) multiple windows can claim to
        // have focus when a main window shows sub-dialogs, so we have to manually
        // try to work out the real focused window:
        if (curWindow.size() > 1)
        {
            // Remove any windows claiming to be focused which have a child
            // window that is focused:
            TestUtil.fx_(() -> {
                curWindow.removeIf(w -> curWindow.stream().anyMatch(parent -> parent instanceof Stage && ((Stage) parent).getOwner() == w));
            });
        }
        // Fall back to targetWindow if we still haven't narrowed it down:
        return curWindow.size() == 1 ? curWindow.get(0) : targetWindow();
    }

    @OnThread(Tag.Any)
    default <C extends Node> C getFocusOwner(Class<C> expectedClass)
    {
        Node node = getFocusOwner();
        if (!expectedClass.isInstance(node))
            throw new RuntimeException("Focus owner is " + (node == null ? "null" : node.getClass().toString()) + " but expected " + expectedClass + " Target window: " + targetWindow() + " Real focused window: " + TestUtil.fx(() -> getRealFocusedWindow()));
        return expectedClass.cast(node);
    }

    @OnThread(Tag.Any) 
    default FxRobotInterface correctTargetWindow()
    {
        return targetWindow(TestUtil.fx(() -> getRealFocusedWindow()));
    }
    
    default public void checkDialogFocused(String msg)
    {
        Window window = getRealFocusedWindow();
        assertTrue(msg + " " + window + " " + window.getClass(), window.getClass().toString().contains("Dialog"));
    }
    
}
