package records.transformations.expression.type;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import records.gui.expressioneditor.ConsecutiveChild;
import records.gui.expressioneditor.SaverBase;
import records.gui.expressioneditor.TypeEntry.Keyword;
import records.transformations.expression.BracketedStatus;
import records.transformations.expression.type.TypeSaver.Context;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.UnitType;

import java.util.ArrayList;
import java.util.Stack;
import java.util.function.Function;

@OnThread(Tag.FXPlatform)
public class TypeSaver extends SaverBase<TypeExpression, TypeSaver, UnitType, Keyword, Context>
{
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
