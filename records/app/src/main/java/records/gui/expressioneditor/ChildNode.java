package records.gui.expressioneditor;

import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Created by neil on 19/12/2016.
 */
public abstract class ChildNode<EXPRESSION extends @NonNull Object> implements ExpressionNode
{
    protected final ConsecutiveBase<EXPRESSION> parent;

    public ChildNode(ConsecutiveBase<EXPRESSION> parent)
    {
        this.parent = parent;
    }

    @SuppressWarnings("initialization")
    protected LeaveableTextField createLeaveableTextField(@UnknownInitialization(ChildNode.class) ChildNode<EXPRESSION> this)
    {
        return (@Initialized LeaveableTextField)new LeaveableTextField(this, parent);
    }

    // Although we don't extend OperandNode, this deliberately implements a method from OperandNode:
    public ConsecutiveBase<EXPRESSION> getParent()
    {
        return parent;
    }
}
