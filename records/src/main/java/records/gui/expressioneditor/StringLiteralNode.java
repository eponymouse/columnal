package records.gui.expressioneditor;

import javafx.beans.value.ObservableObjectValue;
import javafx.beans.value.ObservableStringValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
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
import utility.gui.FXUtility;

import java.util.Collections;

/**
 * Created by neil on 20/12/2016.
 */
public class StringLiteralNode extends LeafNode implements OperandNode
{
    private final TextField textField;
    private final AutoComplete autoComplete;
    private ObservableList<Node> nodes;

    public StringLiteralNode(String initialValue, ConsecutiveBase parent)
    {
        super(parent);
        textField = createLeaveableTextField();
        nodes = FXCollections.observableArrayList(new Label("\u201C"), textField, new Label("\u201D"));
        // We need a completion so you can leave the field using tab/enter
        // Otherwise only right-arrow will get you out
        Completion currentCompletion = new EndStringCompletion();
        this.autoComplete = new AutoComplete(textField, s ->
        {
            return Collections.singletonList(currentCompletion);
        }, new SimpleCompletionListener()
        {
            @Override
            protected String selected(String currentText, Completion c, String rest)
            {
                parent.focusRightOf(StringLiteralNode.this);
                return currentText;
            }
        }, c -> false);

        Utility.addChangeListenerPlatformNN(textField.textProperty(), text -> parent.changed(this));
        textField.setText(initialValue);
    }

    @Override
    public @Nullable DataType inferType()
    {
        return DataType.TEXT;
    }

    @Override
    public OperandNode prompt(String prompt)
    {
        textField.setPromptText(prompt);
        return this;
    }

    @Override
    public Expression toExpression(FXPlatformConsumer<Object> onError)
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
    public @Nullable ObservableObjectValue<@Nullable String> getStyleWhenInner()
    {
        return null;
    }

    @Override
    public void setSelected(boolean selected)
    {
        // TODO
    }

    @Override
    public void setHoverDropLeft(boolean on)
    {
        FXUtility.setPseudoclass(nodes().get(0), "exp-hover-drop-left", on);
    }

    @Override
    public Pair<ConsecutiveChild, Double> findClosestDrop(Point2D loc)
    {
        return new Pair<>(this, FXUtility.distanceToLeft(nodes.get(0), loc));
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

    private static class EndStringCompletion extends Completion
    {
        @Override
        public Pair<@Nullable Node, ObservableStringValue> getDisplay(ObservableStringValue currentText)
        {
            return new Pair<>(null, currentText);
        }

        @Override
        public boolean shouldShow(String input)
        {
            return true;
        }

        @Override
        public CompletionAction completesOnExactly(String input, boolean onlyAvailableCompletion)
        {
            return CompletionAction.SELECT;
        }

        @Override
        public boolean features(String curInput, char character)
        {
            return true;
        }
    }
}
