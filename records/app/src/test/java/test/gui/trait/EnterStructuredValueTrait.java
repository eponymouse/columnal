package test.gui.trait;

import annotation.qual.Value;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.stage.Window;
import log.Log;
import org.testfx.api.FxRobotInterface;
import org.testfx.util.WaitForAsyncUtils;
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
    default public void defocusSTFAndCheck(boolean checkContentSame, FXPlatformRunnable defocus)
    {
        Window window = TestUtil.fx(() -> getRealFocusedWindow());
        Node node = TestUtil.fx(() -> window.getScene().getFocusOwner());
        assertTrue("" + node, node instanceof StructuredTextField);
        String content = TestUtil.fx(() -> ((StructuredTextField)node).getText());
        ChangeListener<String> logTextChange = (a, oldVal, newVal) -> Log.logStackTrace("Text changed on defocus from : \"" + oldVal + "\" to \"" + newVal + "\"");
        if (checkContentSame)
        {
            TestUtil.fx_(() -> ((StructuredTextField)node).textProperty().addListener(logTextChange));
        }
        TestUtil.fx_(defocus);
        WaitForAsyncUtils.waitForFxEvents();
        assertNotEquals(node, TestUtil.fx(() -> window.getScene().getFocusOwner()));
        if (checkContentSame)
        {
            assertEquals(content, TestUtil.fx(() -> ((StructuredTextField) node).getText()));
            TestUtil.fx_(() -> ((StructuredTextField)node).textProperty().removeListener(logTextChange));
        }
    }
}
