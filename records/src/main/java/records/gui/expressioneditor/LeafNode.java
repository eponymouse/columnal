package records.gui.expressioneditor;

import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.initialization.qual.NotOnlyInitialized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.jetbrains.annotations.NotNull;

/**
 * Created by neil on 19/12/2016.
 */
public abstract class LeafNode implements ExpressionNode
{
    protected final Consecutive parent;

    public LeafNode(Consecutive parent)
    {
        this.parent = parent;
    }

    @SuppressWarnings("initialization")
    protected LeaveableTextField createLeaveableTextField(@UnknownInitialization(LeafNode.class) LeafNode this)
    {
        return (@Initialized LeaveableTextField)new LeaveableTextField(this, parent);
    }
}
