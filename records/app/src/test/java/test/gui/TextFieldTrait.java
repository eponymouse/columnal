package test.gui;

import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
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
        TextField textField = (TextField)TestUtil.fx(() -> targetWindow().getScene().getFocusOwner());

        // Some sort of bug on OS X prevents Cmd-A working in TestFX:
        if (SystemUtils.IS_OS_MAC_OSX)
            TestUtil.fx_(() -> textField.selectAll());
        else
            push(KeyCode.CONTROL, KeyCode.A);
        return textField;
    }
}
