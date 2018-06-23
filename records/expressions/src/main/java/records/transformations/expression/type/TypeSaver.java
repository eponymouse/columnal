package records.transformations.expression.type;

import records.gui.expressioneditor.ConsecutiveBase;
import records.gui.expressioneditor.ConsecutiveChild;
import records.gui.expressioneditor.SaverBase;
import records.gui.expressioneditor.TypeEntry.Keyword;
import records.transformations.expression.type.TypeSaver.Context;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.UnitType;

@OnThread(Tag.FXPlatform)
public class TypeSaver extends SaverBase<TypeExpression, TypeSaver, UnitType, Keyword, Context>
{
    public TypeSaver(ConsecutiveBase<TypeExpression, TypeSaver> parent)
    {
        super(parent);
    }

    public class Context {}

    public void saveKeyword(Keyword keyword, ConsecutiveChild<TypeExpression, TypeSaver> errorDisplayer, FXPlatformConsumer<Context> withContext)
    {
        if (keyword == Keyword.COMMA)
            super.saveOperator(UnitType.UNIT, errorDisplayer, withContext);
    }
    
    public void saveOperand(TypeExpression operand, ConsecutiveChild<TypeExpression, TypeSaver> errorDisplayer, FXPlatformConsumer<Context> withContext)
    {
        
    }
}
