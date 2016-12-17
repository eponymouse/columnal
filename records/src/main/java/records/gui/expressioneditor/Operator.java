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

    @Override
    public void deleteOneFromEnd()
    {
        content.setText(content.getText().substring(0, content.getText().length() - 1));
        if (content.getText().isEmpty())
            parent.replace(this, null);
    }

    @Override
    public void deleteOneFromBegin()
    {
        content.setText(content.getText().substring(1));
        if (content.getText().isEmpty())
            parent.replace(this, null);
    }

    @Override
    public boolean focusEnd()
    {
        return false;
    }
}
