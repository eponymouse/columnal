package records.gui.expressioneditor;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.gui.expressioneditor.AutoComplete.Completion;
import records.gui.expressioneditor.AutoComplete.SimpleCompletionListener;
import records.transformations.expression.Expression;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;

import java.util.Collections;

/**
 * Created by neil on 20/12/2016.
 */
public class StringLiteralNode extends LeafNode implements OperandNode
{
    private final TextField textField;
    private final AutoComplete autoComplete;
    private ObservableList<Node> nodes;

    @SuppressWarnings("initialization")
    public StringLiteralNode(Consecutive parent)
    {
        super(parent);
        textField = new LeaveableTextField(this, parent);
        nodes = FXCollections.observableArrayList(new Label("\u201C"), textField, new Label("\u201D"));
        // We need a completion so you can leave the field using tab/enter
        // Otherwise only right-arrow will get you out
        Completion currentCompletion = new Completion()
        {
            @Override
            Pair<Node, String> getDisplay(String currentText)
            {
                return new Pair<>(null, currentText);
            }

            @Override
            boolean shouldShow(String input)
            {
                return true;
            }
        };
        this.autoComplete = new AutoComplete(textField, s ->
        {
            return Collections.singletonList(currentCompletion);
        }, new SimpleCompletionListener()
        {
            @Override
            protected String selected(String currentText, Completion c, String rest)
            {
                return currentText;
            }
        }, c -> false);

        Utility.addChangeListenerPlatformNN(textField.textProperty(), text -> parent.changed(this));
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
