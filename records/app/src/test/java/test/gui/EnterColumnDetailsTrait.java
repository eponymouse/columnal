package test.gui;

import javafx.scene.input.KeyCode;
import log.Log;
import org.testfx.api.FxRobotInterface;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.gui.EditColumnDialog.ColumnDetails;
import records.transformations.expression.type.TypeExpression;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Random;

import static org.junit.Assert.fail;

public interface EnterColumnDetailsTrait extends FxRobotInterface, EnterTypeTrait, EnterStructuredValueTrait
{
    // Call once the dialog is showing.  We will click Ok button before returning.
    @OnThread(Tag.Simulation)
    default public void enterColumnDetails(ColumnDetails columnDetails, Random r) throws InternalException, UserException
    {
        // We should be focused on name initially with the whole field selected, or blank:
        write(columnDetails.columnId.getRaw(), DELAY);
        Log.debug("Entering type: " + columnDetails.dataType);
        if (r.nextBoolean())
        {
            // Navigate with keyboard
            push(KeyCode.TAB);
            if (columnDetails.dataType.equals(DataType.NUMBER) && r.nextBoolean())
                push(KeyCode.SPACE);
            else if (columnDetails.dataType.equals(DataType.TEXT) && r.nextBoolean())
            {
                push(KeyCode.TAB);
                push(KeyCode.SPACE);
            }
            else
            {
                push(KeyCode.TAB);
                push(KeyCode.TAB);
                enterType(TypeExpression.fromDataType(columnDetails.dataType), r);
            }
        }
        else
        {
            // Navigate with mouse
            if (columnDetails.dataType.equals(DataType.NUMBER) && r.nextBoolean())
                clickOn(".radio-type-number");
            else if (columnDetails.dataType.equals(DataType.TEXT) && r.nextBoolean())
                clickOn(".radio-type-text");
            else
            {
                // Should be empty:
                clickOn(".type-editor");
                enterType(TypeExpression.fromDataType(columnDetails.dataType), r);
            }
        }
        push(KeyCode.ESCAPE);
        clickOn(".default-value");
        
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
