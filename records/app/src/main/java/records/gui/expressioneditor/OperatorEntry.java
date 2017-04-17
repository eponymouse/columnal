package records.gui.expressioneditor;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableStringValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.expressioneditor.AutoComplete.Completion;
import records.gui.expressioneditor.AutoComplete.KeyShortcutCompletion;
import records.gui.expressioneditor.AutoComplete.SimpleCompletionListener;
import records.gui.expressioneditor.GeneralEntry.Status;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.TranslationUtility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by neil on 17/12/2016.
 */
public class OperatorEntry extends LeafNode implements ConsecutiveChild
{
    /**
     * The outermost container for the whole thing:
     */
    private final VBox container;
    /**
     * The text field for actually entering the operator.
     */
    private final TextField textField;
    private final ObservableList<Node> nodes;
    private final @MonotonicNonNull AutoComplete autoComplete;
    private final static List<Pair<String, @LocalizableKey String>> OPERATORS = Arrays.asList(
        opD("=", "op.equal"),
        opD("<>", "op.notEqual"),
        opD("+", "op.plus"),
        opD("-", "op.minus"),
        opD("*", "op.times"),
        opD("/", "op.divide"),
        opD("&", "op.and"),
        opD("|", "op.or"),
        opD("<", "op.lessThan"),
        opD("<=", "op.lessThanOrEqual"),
        opD(">", "op.greaterThan"),
        opD(">=", "op.greaterThanOrEqual"),
        opD("^", "op.raise"),
        opD(",", "op.separator"),
        opD("~", "op.matches"),
        opD("\u00B1", "op.plusminus")
    );

    private static Pair<String, @LocalizableKey String> opD(String op, @LocalizableKey String key)
    {
        return new Pair<>(op, key);
    }

    private final static Set<Integer> ALPHABET = OPERATORS.stream().map(Pair::getFirst).flatMapToInt(String::codePoints).boxed().collect(Collectors.<@NonNull Integer>toSet());

    public OperatorEntry(ConsecutiveBase parent)
    {
        this("", false, parent);
    }

    public OperatorEntry(String content, boolean userEntered, ConsecutiveBase parent)
    {
        super(parent);
        this.textField = createLeaveableTextField();
        FXUtility.setPseudoclass(textField, "op-empty", content.isEmpty());
        if (!userEntered)
            textField.setText(content); // Do before auto complete is on the field
        FXUtility.sizeToFit(textField, 5.0, 5.0);
        container = ExpressionEditorUtil.withLabelAbove(textField, "operator", "", this, parent.getParentStyles()).getFirst();
        container.getStyleClass().add("entry");
        this.nodes = FXCollections.observableArrayList(this.container);

        this.autoComplete = new AutoComplete(textField, this::getCompletions, new CompletionListener(), c -> !isOperatorAlphabet(c));

        Utility.addChangeListenerPlatformNN(textField.textProperty(), text ->{
            parent.changed(OperatorEntry.this);
            FXUtility.setPseudoclass(textField, "op-empty", text.isEmpty());
        });

        if (userEntered)
        {
            // Do this after auto-complete is set up and we are set as part of parent,
            // in case it finishes a completion:
            Utility.runAfter(() -> textField.setText(content));
        }
    }

    private List<Completion> getCompletions(@UnknownInitialization(OperatorEntry.class) OperatorEntry this, String s)
    {
        ArrayList<Completion> r = new ArrayList<>();
        if (!parent.isTopLevel())
            r.add(new KeyShortcutCompletion("End bracketed expressions", ')'));
        for (Pair<String, @LocalizableKey String> operator : OPERATORS)
        {
            r.add(new SimpleCompletion(operator.getFirst(), operator.getSecond()));
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

    @Override
    public void setSelected(boolean selected)
    {
        FXUtility.setPseudoclass(container, "exp-selected", selected);
    }

    @Override
    public void setHoverDropLeft(boolean selected)
    {
        FXUtility.setPseudoclass(container, "exp-hover-drop-left", selected);
    }

    @Override
    public boolean isBlank()
    {
        return textField.getText().isEmpty();
    }

    @Override
    public void focusChanged()
    {
        // Nothing to be done
    }

    public boolean isFocused()
    {
        return textField.isFocused();
    }

    private class SimpleCompletion extends Completion
    {
        private final String operator;
        private final @Localized String description;

        public SimpleCompletion(String operator, @LocalizableKey String descriptionKey)
        {
            this.operator = operator;
            this.description = TranslationUtility.getString(descriptionKey);
        }

        @Override
        public Pair<@Nullable Node, ObservableStringValue> getDisplay(ObservableStringValue currentText)
        {
            Label description = new Label(this.description);
            BorderPane.setAlignment(description, Pos.BOTTOM_RIGHT);
            description.getStyleClass().add("operator-description");
            Label mainLabel = new Label(operator);
            BorderPane.setAlignment(mainLabel, Pos.BOTTOM_LEFT);
            return new Pair<>(new BorderPane(description, null, null, null, mainLabel), new ReadOnlyStringWrapper(""));
        }

        @Override
        public boolean shouldShow(String input)
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
        public boolean features(String curInput, char character)
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

    private class CompletionListener extends SimpleCompletionListener
    {
        public CompletionListener()
        {
        }

        @Override
        protected String selected(String currentText, Completion c, String rest)
        {
            if (c instanceof SimpleCompletion)
            {
                parent.addOperandToRight(OperatorEntry.this, new GeneralEntry(rest, Status.UNFINISHED, parent).focusWhenShown());
                return ((SimpleCompletion) c).operator;
            }
            else if (c instanceof KeyShortcutCompletion)
            {
                parent.focusRightOf(OperatorEntry.this);
                return "";
            }
            return textField.getText();
        }
    }

    @Override
    public Pair<ConsecutiveChild, Double> findClosestDrop(Point2D loc)
    {
        return new Pair<>(this, FXUtility.distanceToLeft(container, loc));
    }
}
