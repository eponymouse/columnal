package records.gui.expressioneditor;

import javafx.collections.ObservableList;
import javafx.scene.Node;
import org.checkerframework.dataflow.qual.Pure;

/**
 * Created by neil on 17/12/2016.
 */
public abstract class ExpressionNode
{
    @Pure
    public abstract ObservableList<Node> nodes();

    public abstract void focus();
}
