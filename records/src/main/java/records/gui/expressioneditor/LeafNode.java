package records.gui.expressioneditor;

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

}
