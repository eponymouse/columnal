package records.gui.expressioneditor;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableStringValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.expressioneditor.AutoComplete.Completion;
import records.gui.expressioneditor.AutoComplete.KeyShortcutCompletion;
import records.gui.expressioneditor.AutoComplete.SimpleCompletionListener;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by neil on 17/12/2016.
 */
public class OperatorEntry extends LeafNode
{
    private final TextField textField;
    private final ObservableList<Node> nodes;
    private final AutoComplete autoComplete;
    private final static List<String> OPERATORS = Arrays.asList("=", "<>", "+", "-", "*", "/", "&", "|", "<", "<=", ">", ">=", "?", "^", ",");
    private final static Set<Integer> ALPHABET = OPERATORS.stream().flatMapToInt(String::codePoints).boxed().collect(Collectors.<@NonNull Integer>toSet());

    @SuppressWarnings("initialization")
    public OperatorEntry(String content, Consecutive parent)
    {
        super(parent);
        this.textField = new LeaveableTextField(this, parent);
        Utility.sizeToFit(textField, 5.0, 5.0);
        textField.getStyleClass().add("operator-field");
        this.nodes = FXCollections.observableArrayList(this.textField);

        this.autoComplete = new AutoComplete(textField, this::getCompletions, new SimpleCompletionListener()
        {
            @Override
            protected String selected(String currentText, Completion c, String rest)
            {
                if (c instanceof SimpleCompletion)
                {
                    parent.addOperandToRight(OperatorEntry.this, new GeneralEntry(rest, parent).focusWhenShown());
                    return ((SimpleCompletion) c).operator;
                }
                else if (c instanceof KeyShortcutCompletion)
                {
                    parent.focusRightOf(OperatorEntry.this);
                    return "";
                }
                return textField.getText();
            }
        }, c -> !isOperatorAlphabet(c));

        Utility.addChangeListenerPlatformNN(textField.textProperty(), text ->{
            parent.changed(OperatorEntry.this);
        });

        // Do this after auto-complete is set up and we are set as part of parent,
        // in case it finishes a completion:
        Utility.runAfter(() -> textField.setText(content));
    }

    private List<Completion> getCompletions(String s)
    {
        ArrayList<Completion> r = new ArrayList<>();
        if (!parent.isTopLevel())
            r.add(new KeyShortcutCompletion("End bracketed expressions", ')'));
        for (String operator : OPERATORS)
        {
            r.add(new SimpleCompletion(operator));
        }
        r.removeIf(c -> !c.shouldShow(s));
        return r;
    }

    @Override
    public ObservableList<Node> nodes()
    {
        return nodes;
    }

    public ExpressionNode focusWhenShown()
    {
        Utility.onNonNull(textField.sceneProperty(), scene -> focus(Focus.RIGHT));
        return this;
    }

    public static boolean isOperatorAlphabet(Character character)
    {
        return ALPHABET.contains((Integer)(int)(char)character);
    }

    // Returns false if it wasn't blank
    public boolean fromBlankTo(String s)
    {
        if (textField.getText().trim().isEmpty())
        {
            // We request focus before so that we can be overruled by setText's listeners:
            textField.requestFocus();
            textField.setText(s);
            // We position caret after because length will have changed
            // Will have no effect if we've lost focus:
            textField.positionCaret(textField.getLength());
            return true;
        }
        else
            return false;
    }

    private class SimpleCompletion extends Completion
    {
        private final String operator;

        public SimpleCompletion(String operator)
        {
            this.operator = operator;
        }

        @Override
        Pair<@Nullable Node, ObservableStringValue> getDisplay(ObservableStringValue currentText)
        {
            return new Pair<>(null, new ReadOnlyStringWrapper(operator));
        }

        @Override
        boolean shouldShow(String input)
        {
            return operator.contains(input);
        }

        @Override
        public CompletionAction completesOnExactly(String input, boolean isOnlyCompletion)
        {
            if (input.equals(operator))
                return isOnlyCompletion ? CompletionAction.COMPLETE_IMMEDIATELY : CompletionAction.SELECT;
            else
                return CompletionAction.NONE;
        }

        @Override
        boolean features(String curInput, char character)
        {
            return operator.contains("" + character);
        }
    }

    public String get()
    {
        return textField.getText();
    }

    @Override
    public void focus(Focus side)
    {
        textField.requestFocus();
        textField.positionCaret(side == Focus.LEFT ? 0 : textField.getLength());
    }
}
