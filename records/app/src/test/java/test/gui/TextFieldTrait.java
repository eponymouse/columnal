package test.gui;

import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.stage.Window;
import org.apache.commons.lang3.SystemUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.testfx.api.FxRobotInterface;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;

public interface TextFieldTrait extends FxRobotInterface, FocusOwnerTrait
{
    @OnThread(Tag.Any)
    public default TextInputControl selectAllCurrentTextField()
    {
        Node focusOwner = getFocusOwner();
        if (!(focusOwner instanceof TextInputControl))
            throw new RuntimeException("Focus owner is " + (focusOwner == null ? "null" : focusOwner.getClass().toString()));
        TextInputControl textField = (TextInputControl) focusOwner;

        // Some sort of bug on OS X prevents Cmd-A working in TestFX:
        if (SystemUtils.IS_OS_MAC_OSX)
            TestUtil.fx_(() -> textField.selectAll());
        else
            push(KeyCode.CONTROL, KeyCode.A);
        return textField;
    }
}
