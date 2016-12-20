package records.gui.expressioneditor;

import org.checkerframework.checker.interning.qual.Interned;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.transformations.expression.Expression;
import utility.FXPlatformConsumer;

import java.util.Collections;
import java.util.List;

/**
 * An item which can be in a Consecutive
 */
public @Interned interface OperandNode extends ExpressionNode
{
    public default List<String> getDeclaredVariables()
    {
        return Collections.emptyList();
    }

    public abstract @Nullable DataType inferType();

    public abstract ExpressionNode prompt(String prompt);

    public abstract @Nullable Expression toExpression(FXPlatformConsumer<Object> onError);

    public OperandNode focusWhenShown();
}
