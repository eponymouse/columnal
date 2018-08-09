package test.gui;

import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.stage.Window;
import org.apache.commons.lang3.SystemUtils;
import org.testfx.api.FxRobotInterface;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;

public interface TextFieldTrait extends FxRobotInterface
{
    @OnThread(Tag.Any)
    public default TextField selectAllCurrentTextField()
    {
        Window curWindow = listWindows().stream().filter(Window::isFocused).findFirst().orElse(null);
        if (curWindow == null)
            throw new RuntimeException("No focused window?!");
        Window curWindowFinal = curWindow;
        Node focusOwner = TestUtil.fx(() -> curWindowFinal.getScene().getFocusOwner());
        if (!(focusOwner instanceof TextField))
            throw new RuntimeException("Focus owner is " + focusOwner.getClass());
        TextField textField = (TextField) focusOwner;

        // Some sort of bug on OS X prevents Cmd-A working in TestFX:
        if (SystemUtils.IS_OS_MAC_OSX)
            TestUtil.fx_(() -> textField.selectAll());
        else
            push(KeyCode.CONTROL, KeyCode.A);
        return textField;
    }
}
