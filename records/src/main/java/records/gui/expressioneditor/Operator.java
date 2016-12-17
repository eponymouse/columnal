package records.gui.expressioneditor;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Label;

/**
 * Created by neil on 17/12/2016.
 */
public class Operator extends ExpressionNode
{
    private final Label content;
    private final ObservableList<Node> nodes;

    public Operator(String content, ExpressionParent parent)
    {
        super(parent);
        this.content = new Label(content);
        this.nodes = FXCollections.observableArrayList(this.content);
    }

    @Override
    public ObservableList<Node> nodes()
    {
        return nodes;
    }
}
