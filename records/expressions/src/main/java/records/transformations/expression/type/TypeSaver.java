package records.transformations.expression.type;

import records.gui.expressioneditor.ErrorDisplayer;
import records.transformations.expression.UnitExpression;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;

@OnThread(Tag.FXPlatform)
public interface TypeSaver
{
    class Context {}
    
    void saveOperand(UnitExpression unitExpression, ErrorDisplayer<TypeExpression, TypeSaver> unitLiteralTypeNode, FXPlatformConsumer<Context> withContext);
    //public boolean isRoundBracketed();
}
