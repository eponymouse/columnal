package records.gui.expressioneditor;

import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

import java.util.Collections;

/**
 * Created by neil on 19/12/2016.
 */
public class FunctionNode extends ExpressionNode
{
    private final TextField functionName = new TextField();
    private final Consecutive arguments;

    public FunctionNode(String funcName, ExpressionParent parent)
    {
        arguments = new Consecutive(Collections.singletonList(p -> new GeneralEntry("", p)), parent, new HBox(functionName, new Label("(")), new Label(")"));
        functionName.setText(funcName);
    }

    @Override
    public ObservableList<Node> nodes()
    {
        return arguments.nodes();
    }

    @Override
    public void focus()
    {
        functionName.requestFocus();
    }

    // Focuses the arguments because we are only shown after they've chosen the function
    public ExpressionNode focusWhenShown()
    {
        arguments.focus();
        return this;
    }
}
