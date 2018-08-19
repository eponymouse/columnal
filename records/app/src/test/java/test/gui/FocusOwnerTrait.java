package test.gui;

import javafx.scene.Node;
import javafx.stage.Window;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.testfx.api.FxRobotInterface;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;

public interface FocusOwnerTrait extends FxRobotInterface
{
    @OnThread(Tag.Any)
    default @Nullable Node getFocusOwner()
    {
        Window curWindow = listWindows().stream().filter(Window::isFocused).findFirst().orElse(null);
        if (curWindow == null)
            throw new RuntimeException("No focused window?!");
        Window curWindowFinal = curWindow;
        return TestUtil.fx(() -> curWindowFinal.getScene().getFocusOwner());
    }

    @OnThread(Tag.Any)
    default <C extends Node> C getFocusOwner(Class<C> expectedClass)
    {
        Window curWindow = listWindows().stream().filter(Window::isFocused).findFirst().orElse(null);
        if (curWindow == null)
            throw new RuntimeException("No focused window?!");
        Window curWindowFinal = curWindow;
        Node node = TestUtil.fx(() -> curWindowFinal.getScene().getFocusOwner());
        if (!expectedClass.isInstance(node))
            throw new RuntimeException("Focus owner is " + (node == null ? "null" : node.getClass().toString()) + " but expected " + expectedClass);
        return expectedClass.cast(node);
    }
}
