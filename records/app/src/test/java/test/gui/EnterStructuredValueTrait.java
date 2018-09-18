package test.gui;

import annotation.qual.Value;
import org.testfx.api.FxRobotInterface;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import test.DataEntryUtil;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Random;

public interface EnterStructuredValueTrait extends FxRobotInterface
{
    @OnThread(Tag.Any)
    default public void enterStructuredValue(DataType dataType, @Value Object value, Random r) throws InternalException, UserException
    {
        // Should we inline this there, or vice versa?
        DataEntryUtil.enterValue(this, r, dataType, value, false);
    }
}
