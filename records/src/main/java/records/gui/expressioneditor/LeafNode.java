package records.gui.expressioneditor;

/**
 * Created by neil on 19/12/2016.
 */
public abstract class LeafNode extends ExpressionNode
{
    protected final ExpressionParent parent;

    public LeafNode(ExpressionParent parent)
    {
        this.parent = parent;
    }

}
