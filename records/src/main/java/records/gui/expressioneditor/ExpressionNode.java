package records.gui.expressioneditor;

import javafx.collections.ObservableList;
import javafx.scene.Node;
import org.checkerframework.dataflow.qual.Pure;

/**
 * Created by neil on 17/12/2016.
 */
public abstract class ExpressionNode
{
    protected final ExpressionParent parent;

    public ExpressionNode(ExpressionParent parent)
    {
        this.parent = parent;
    }

    @Pure
    public abstract ObservableList<Node> nodes();

    public abstract void deleteOneFromEnd();
    public abstract void deleteOneFromBegin();

    public abstract boolean focusEnd();
}
