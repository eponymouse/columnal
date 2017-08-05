package records.gui.expressioneditor;

import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;

/**
 * Created by neil on 19/12/2016.
 */
public abstract class ChildNode implements ExpressionNode
{
    protected final ConsecutiveBase parent;

    public ChildNode(ConsecutiveBase parent)
    {
        this.parent = parent;
    }

    @SuppressWarnings("initialization")
    protected LeaveableTextField createLeaveableTextField(@UnknownInitialization(ChildNode.class)ChildNode this)
    {
        return (@Initialized LeaveableTextField)new LeaveableTextField(this, parent);
    }

    // Although we don't extend OperandNode, this deliberately implements a method from OperandNode:
    public ConsecutiveBase getParent()
    {
        return parent;
    }
}
