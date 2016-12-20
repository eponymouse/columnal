package records.gui.expressioneditor;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.gui.expressioneditor.AutoComplete.Completion;
import records.gui.expressioneditor.AutoComplete.KeyShortcutCompletion;
import records.transformations.expression.Expression;
import utility.FXPlatformConsumer;
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
    private final static List<String> OPERATORS = Arrays.asList("=", "/=", "+", "-", "*", "/", "&", "|", "<", "<=", ">", ">=", "?", "^");
    private final static Set<Integer> ALPHABET = OPERATORS.stream().flatMapToInt(String::codePoints).boxed().collect(Collectors.<@NonNull Integer>toSet());

    @SuppressWarnings("initialization")
    public OperatorEntry(String content, Consecutive parent)
    {
        super(parent);
        this.textField = new LeaveableTextField(this, parent);
        Utility.sizeToFit(textField, 5.0, 5.0);
        textField.getStyleClass().add("operator-field");
        this.nodes = FXCollections.observableArrayList(this.textField);

        this.autoComplete = new AutoComplete(textField, this::getCompletions, (c, rest) -> {
            if (c instanceof SimpleCompletion)
            {
                textField.setText(((SimpleCompletion) c).operator);
                parent.addOperandToRight(this, new GeneralEntry(rest, parent).focusWhenShown());
            }
            else if (c instanceof KeyShortcutCompletion)
            {
                parent.focusRightOf(this);
                textField.setText("");
            }
        }, c -> !isOperatorAlphabet(c));

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
        return r;
    }

    @Override
    public ObservableList<Node> nodes()
    {
        return nodes;
    }

    public ExpressionNode focusWhenShown()
    {
        Utility.onNonNull(textField.sceneProperty(), scene -> textField.requestFocus());
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
            textField.setText(s);
            focus(Focus.RIGHT);
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
        Pair<@Nullable Node, String> getDisplay(String currentText)
        {
            return new Pair<>(null, operator);
        }

        @Override
        boolean shouldShow(String input)
        {
            return operator.contains(input);
        }

        @Override
        public boolean completesOnExactly(String input)
        {
            // TODO but not if there's another longer operator which also fits
            return input.equals(operator);
        }
    }

    @Override
    public void focus(Focus side)
    {
        textField.requestFocus();
        textField.positionCaret(side == Focus.LEFT ? 0 : textField.getLength());
    }
}
