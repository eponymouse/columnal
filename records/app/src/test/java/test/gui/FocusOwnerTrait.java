package test.gui;

import javafx.scene.Node;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.testfx.api.FxRobotInterface;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public interface FocusOwnerTrait extends FxRobotInterface
{
    @OnThread(Tag.Any)
    default @Nullable Node getFocusOwner()
    {
        List<Window> curWindow = new ArrayList<>(listWindows().stream().filter(Window::isFocused).collect(Collectors.toList()));
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
        Window curWindowFinal = curWindow.size() == 1 ? curWindow.get(0) : targetWindow();
        return TestUtil.fx(() -> curWindowFinal.getScene().getFocusOwner());
    }

    @OnThread(Tag.Any)
    default <C extends Node> C getFocusOwner(Class<C> expectedClass)
    {
        Node node = getFocusOwner();
        if (!expectedClass.isInstance(node))
            throw new RuntimeException("Focus owner is " + (node == null ? "null" : node.getClass().toString()) + " but expected " + expectedClass);
        return expectedClass.cast(node);
    }
}
