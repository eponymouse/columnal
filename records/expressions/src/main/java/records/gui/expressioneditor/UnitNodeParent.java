package records.gui.expressioneditor;

import records.data.unit.UnitManager;
import records.transformations.expression.SingleUnitExpression;
import records.transformations.expression.UnitExpression;
import utility.FXPlatformConsumer;

// TODO rename UnitSaver
public class UnitNodeParent
{
    //UnitManager getUnitManager();

    class Context {}

    // Note: if we are copying to clipboard, callback will not be called
    public void saveOperator(String operator, ErrorDisplayer<UnitExpression, UnitNodeParent> errorDisplayer, FXPlatformConsumer<Context> withContext) {}
    public void saveOperand(SingleUnitExpression singleItem, ErrorDisplayer<UnitExpression, UnitNodeParent> errorDisplayer, FXPlatformConsumer<Context> withContext) {}
}
