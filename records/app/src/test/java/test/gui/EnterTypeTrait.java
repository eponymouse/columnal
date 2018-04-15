package test.gui;

import org.testfx.api.FxRobotInterface;
import records.transformations.expression.type.TypeExpression;
import test.DataEntryUtil;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Random;

public interface EnterTypeTrait extends FxRobotInterface
{
    @OnThread(Tag.Any)
    public default void enterType(TypeExpression typeExpression, Random r)
    {
    }
}
