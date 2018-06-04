package records.transformations.expression.type;

import records.gui.expressioneditor.ErrorDisplayer;
import records.gui.expressioneditor.UnitLiteralTypeNode;
import records.transformations.expression.UnitExpression;
import records.transformations.expression.UnitLiteralExpression;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;

// TODO rename TypeSaver
@OnThread(Tag.FXPlatform)
public interface TypeParent
{
    class Context {}
    
    void saveOperand(UnitExpression unitExpression, ErrorDisplayer<TypeExpression, TypeParent> unitLiteralTypeNode, FXPlatformConsumer<Context> withContext);
    //public boolean isRoundBracketed();
}
