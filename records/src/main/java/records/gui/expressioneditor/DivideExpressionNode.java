package records.gui.expressioneditor;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Label;

/**
 * Created by neil on 17/12/2016.
 */
public abstract class DivideExpressionNode extends ExpressionNode implements ExpressionParent
{
    private final ObservableList<Node> nodes;
    private final ObjectProperty<ExpressionNode> lhs = new SimpleObjectProperty<>();
    private final ObjectProperty<ExpressionNode> rhs = new SimpleObjectProperty<>();

    public DivideExpressionNode(ExpressionNode lhs, ExpressionNode rhs, ExpressionParent parent)
    {
        super(parent);
        this.lhs.setValue(lhs);
        this.rhs.setValue(rhs);
        nodes = FXCollections.observableArrayList();
        nodes.addAll(lhs.nodes());
        nodes.add(new Label("/"));
        nodes.addAll(rhs.nodes());
        //TODO bind and update
    }

    @Override
    public ObservableList<Node> nodes()
    {
        return nodes;
    }
}
