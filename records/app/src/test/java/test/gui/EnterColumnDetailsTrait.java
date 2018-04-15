package test.gui;

import javafx.scene.input.KeyCode;
import org.testfx.api.FxRobotInterface;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.gui.EditColumnDialog.ColumnDetails;
import records.transformations.expression.type.TypeExpression;
import test.TestUtil;

import java.util.Random;

public interface EnterColumnDetailsTrait extends FxRobotInterface, EnterTypeTrait, EnterStructuredValueTrait
{
    // Call once the dialog is showing.  We will click Ok button before returning.
    default public void enterColumnDetails(ColumnDetails columnDetails, Random r) throws InternalException
    {
        // We should be focused on name initially with the whole field selected, or blank:
        write(columnDetails.columnId.getRaw());
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
        
        // We should already be in the default value:
        enterStructuredValue(columnDetails.dataType, columnDetails.defaultValue);
        clickOn(".ok-button");
    }
}
