package records.gui.expressioneditor;

import javafx.beans.value.ObservableObjectValue;
import org.checkerframework.checker.interning.qual.Interned;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.LoadableExpression;
import utility.FXPlatformConsumer;
import utility.Pair;

import java.util.Collections;
import java.util.List;

/**
 * An item which can be in a Consecutive as an operand by itself or
 * next to operators.  (Depends on type: some operands, like tagged operands,
 * may not have a useful type available.)
 */
public @Interned interface OperandNode<@NonNull EXPRESSION extends LoadableExpression<EXPRESSION, SEMANTIC_PARENT>, SEMANTIC_PARENT> extends EEDisplayNode, ConsecutiveChild<EXPRESSION, SEMANTIC_PARENT>, ErrorDisplayer<EXPRESSION>
{
    /**
     * Gets the variables declared in this node.
     */
    public default List<Pair<String, @Nullable DataType>> getDeclaredVariables()
    {
        return Collections.emptyList();
    }

    /**
     * Sets the prompt text for this node
     */
    public abstract void prompt(String prompt);

    /**
     * Saves this item to an Expression (AST-like item).  Should also record who is responsible
     * for displaying the errors for a given expression (matched by reference).
     *
     * If there is a problem,
     * should call onError (1+ times) with problem, and return InvalidExpression if needed.
     *
     */
    public abstract @NonNull EXPRESSION save(ErrorDisplayerRecord errorDisplayer, ErrorAndTypeRecorder onError);

    public @Nullable ObservableObjectValue<@Nullable String> getStyleWhenInner();
}
