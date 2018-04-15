package test.gui;

import annotation.qual.Value;
import org.testfx.api.FxRobotInterface;
import records.data.datatype.DataType;

public interface EnterStructuredValueTrait extends FxRobotInterface
{
    default public void enterStructuredValue(DataType dataType, @Value Object value)
    {
        
    }
}
