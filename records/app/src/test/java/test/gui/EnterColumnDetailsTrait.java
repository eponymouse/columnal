package test.gui;

import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.stage.Window;
import log.Log;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.testfx.api.FxRobotInterface;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.gui.EditImmediateColumnDialog.ColumnDetails;
import records.transformations.expression.type.TypeExpression;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Random;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public interface EnterColumnDetailsTrait extends FxRobotInterface, EnterTypeTrait, EnterStructuredValueTrait
{
    // Call once the dialog is showing.  We will click Ok button before returning.
    @OnThread(Tag.Simulation)
    default public void enterColumnDetails(ColumnDetails columnDetails, Random r) throws InternalException, UserException
    {
        // We should be focused on name initially with the whole field selected, or blank:
        write(columnDetails.columnId.getRaw(), DELAY);
        Window dialog = TestUtil.fx(() -> getRealFocusedWindow());
        Log.debug("Pressing TAB on window: " + dialog);
        push(KeyCode.TAB);
        enterType(TypeExpression.fromDataType(columnDetails.dataType), r);
        dialog = TestUtil.fx(() -> getRealFocusedWindow());
        Log.debug("Pressing ESCAPE on window: " + dialog);
        push(KeyCode.ESCAPE);
        push(KeyCode.ESCAPE);
        //assertTrue(TestUtil.fx(() -> dialog.isShowing()));
        Node defValue = lookup(".default-value").<Node>query();
        assertNotNull(defValue);
        if (defValue != null)
        {
            @NonNull Node defValueFinal = defValue;
            assertFalse(TestUtil.fx(() -> defValueFinal.isDisabled()));
            clickOn(".default-value");
            assertTrue(TestUtil.fx(() -> defValueFinal.isFocused()));
        }
        
        
        // We should already be in the default value:
        enterStructuredValue(columnDetails.dataType, columnDetails.defaultValue, r);
        clickOn(".ok-button");
        
        // If we can still see the OK button, there was a problem.  Cancel the dialog instead:
        if (lookup(".ok-button").tryQuery().isPresent())
        {
            Log.normal("Error in dialog; cancelling");
            // Let us see what the problem is:
            TestUtil.sleep(1500);
            clickOn(".cancel-button");
            fail("Could not click OK in dialog without error");
        }
    }
}
