package test.gui;

import com.google.common.collect.Streams;
import javafx.scene.Node;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.stage.Window;
import org.apache.commons.lang3.SystemUtils;
import org.testfx.api.FxRobotInterface;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;

public interface TextFieldTrait extends FxRobotInterface, FocusOwnerTrait
{
    @OnThread(Tag.Any)
    public default TextInputControl selectAllCurrentTextField()
    {
        Node focusOwner = getFocusOwner();
        if (!(focusOwner instanceof TextInputControl))
            throw new RuntimeException("Focus owner is " + (focusOwner == null ? "null" : focusOwner.getClass().toString()) 
                + "\nTarget window is " + targetWindow() + " " + TestUtil.fx(() -> targetWindow().isFocused())
                //+ "\nOut of " + TestUtil.fx(() -> Streams.stream(Window.impl_getWindows()).map(w -> w.toString() + ":" + w.isFocused()).collect(Collectors.joining("/")))
            );
        TextInputControl textField = (TextInputControl) focusOwner;

        assertTrue(TestUtil.fx(textField::isEditable));
        assertTrue(!TestUtil.fx(textField::isDisabled));
        assertTrue(TestUtil.fx(textField::isFocused));
        
        // Some sort of bug on OS X prevents Cmd-A working in TestFX:
        if (SystemUtils.IS_OS_MAC_OSX)
            TestUtil.fx_(() -> textField.selectAll());
        else
            push(KeyCode.CONTROL, KeyCode.A);
        return textField;
    }
}
