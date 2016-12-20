package records.gui.expressioneditor;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.transformations.expression.Expression;
import utility.FXPlatformConsumer;
import utility.Utility;

/**
 * Created by neil on 20/12/2016.
 */
public class StringLiteralNode extends LeafNode implements OperandNode
{
    private final TextField textField;
    private ObservableList<Node> nodes;

    @SuppressWarnings("initialization")
    public StringLiteralNode(Consecutive parent)
    {
        super(parent);
        textField = new LeaveableTextField(this, parent);
        nodes = FXCollections.observableArrayList(new Label("\u201C"), textField, new Label("\u201D"));
    }

    @Override
    public @Nullable DataType inferType()
    {
        return DataType.TEXT;
    }

    @Override
    public ExpressionNode prompt(String prompt)
    {
        textField.setPromptText(prompt);
        return this;
    }

    @Override
    public @Nullable Expression toExpression(FXPlatformConsumer<Object> onError)
    {
        return new records.transformations.expression.StringLiteral(textField.getText());
    }

    @Override
    public OperandNode focusWhenShown()
    {
        Utility.onNonNull(textField.sceneProperty(), s -> focus(Focus.RIGHT));
        return this;
    }

    @Override
    public ObservableList<Node> nodes()
    {
        return nodes;
    }

    @Override
    public void focus(Focus side)
    {
        textField.requestFocus();
        if (side == Focus.LEFT)
        {
            textField.positionCaret(0);
        }
        else
        {
            textField.positionCaret(textField.getLength());
        }
    }
}
