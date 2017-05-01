package records.gui.expressioneditor;

import javafx.beans.value.ObservableObjectValue;
import org.checkerframework.checker.interning.qual.Interned;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.transformations.expression.Expression;
import utility.FXPlatformConsumer;
import utility.Pair;

import java.util.Collections;
import java.util.List;

/**
 * An item which can be in a Consecutive as an operand by itself or
 * next to operators.  (Depends on type: some operands, like tagged operands,
 * may not have a useful type available.)
 */
public @Interned interface OperandNode extends ExpressionNode, ConsecutiveChild, ErrorDisplayer
{
    /**
     * Gets the variables declared in this node.
     */
    public default List<Pair<String, @Nullable DataType>> getDeclaredVariables()
    {
        return Collections.emptyList();
    }

    // TODO document this once it's used
    // TODO: but what the hell was it for?  I think the idea was if you infer type
    // of part of expression, you can work out expected type for another part.
    public abstract @Nullable DataType inferType();

    /**
     * Sets the prompt text for this node, and returns self-reference
     * (for chaining initialisation methods)
     */
    public abstract OperandNode prompt(String prompt);

    /**
     * Saves this item to an Expression (AST-like item).  Should also record who is responsible
     * for displaying the errors for a given expression (matched by reference).
     *
     * If there is a problem,
     * should call onError (1+ times) with problem, and return InvalidExpression if needed.
     *
     */
    public abstract Expression toExpression(ErrorDisplayerRecord errorDisplayer, FXPlatformConsumer<Object> onError);

    /**
     * Focus appropriate item once this has been shown and return self-reference
     * (for chaining initialisation methods)
     */
    public OperandNode focusWhenShown();

    public @Nullable ObservableObjectValue<@Nullable String> getStyleWhenInner();

    /**
     * Is focus in this operand (or one of its children)?
     */
    public boolean isFocused();
}
