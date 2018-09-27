package test.gui;

import annotation.qual.Value;
import javafx.scene.Node;
import javafx.stage.Window;
import org.testfx.api.FxRobotInterface;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.gui.stf.StructuredTextField;
import test.DataEntryUtil;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformRunnable;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public interface EnterStructuredValueTrait extends FxRobotInterface, FocusOwnerTrait
{
    @OnThread(Tag.Any)
    default public void enterStructuredValue(DataType dataType, @Value Object value, Random r) throws InternalException, UserException
    {
        // Should we inline this there, or vice versa?
        DataEntryUtil.enterValue(this, r, dataType, value, false);
    }
    
    // Checks STF has same content after running defocus
    @OnThread(Tag.Any)
    default public void defocusSTFAndCheck(FXPlatformRunnable defocus)
    {
        Window window = window(Window::isFocused);
        Node node = TestUtil.fx(() -> window.getScene().getFocusOwner());
        assertTrue("" + node, node instanceof StructuredTextField);
        String content = TestUtil.fx(() -> ((StructuredTextField)node).getText());
        TestUtil.fx_(defocus);
        assertNotEquals(node, TestUtil.fx(() -> window.getScene().getFocusOwner()));
        assertEquals(content, TestUtil.fx(() -> ((StructuredTextField)node).getText()));
    }
}
