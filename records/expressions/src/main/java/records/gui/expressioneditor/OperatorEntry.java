package records.gui.expressioneditor;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
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
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.expressioneditor.AutoComplete.Completion;
import records.gui.expressioneditor.AutoComplete.KeyShortcutCompletion;
import records.gui.expressioneditor.AutoComplete.SimpleCompletionListener;
import records.gui.expressioneditor.AutoComplete.WhitespacePolicy;
import records.gui.expressioneditor.ConsecutiveBase.OperatorOutcome;
import records.transformations.expression.ErrorAndTypeRecorder.QuickFix;
import records.transformations.expression.LoadableExpression;
import styled.StyledShowable;
import styled.StyledString;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.TranslationUtility;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Created by neil on 17/12/2016.
 */
public class OperatorEntry<EXPRESSION extends LoadableExpression<EXPRESSION, SEMANTIC_PARENT> & StyledShowable, SEMANTIC_PARENT> extends EntryNode<EXPRESSION, SEMANTIC_PARENT> implements ConsecutiveChild<EXPRESSION, SEMANTIC_PARENT>, ErrorDisplayer<EXPRESSION>
{
    /**
     * The outermost container for the whole thing:
     */
    private final Pair<VBox, ErrorDisplayer<EXPRESSION>> container;
    private final @MonotonicNonNull AutoComplete autoComplete;
    private final SimpleBooleanProperty initialContentEntered = new SimpleBooleanProperty(false);


    public OperatorEntry(Class<EXPRESSION> operandClass, ConsecutiveBase<EXPRESSION, SEMANTIC_PARENT> parent)
    {
        this(operandClass, "", false, parent);
    }

    public OperatorEntry(Class<EXPRESSION> operandClass, String content, boolean userEntered, ConsecutiveBase<EXPRESSION, SEMANTIC_PARENT> parent)
    {
        super(parent, operandClass);
        textField.getStyleClass().add("operator-entry");
        FXUtility.setPseudoclass(textField, "op-empty", content.isEmpty());
        if (!userEntered)
        {
            textField.setText(content); // Do before auto complete is on the field
            initialContentEntered.set(true);
        }
        FXUtility.sizeToFit(textField, 5.0, 5.0);
        container = ExpressionEditorUtil.withLabelAbove(textField, "operator", "", this, getParent().getEditor(), e -> parent.replaceWholeLoad(FXUtility.mouse(this), e.getSecond()), parent.getParentStyles());
        container.getFirst().getStyleClass().add("entry");
        updateNodes();

        this.autoComplete = new AutoComplete(textField, (s, q) -> getCompletions(parent, parent.operations.getValidOperators(parent.getThisAsSemanticParent()), s), new CompletionListener(), WhitespacePolicy.DISALLOW, c -> c == '-' || (!parent.operations.isOperatorAlphabet(c, parent.getThisAsSemanticParent()) && !parent.terminatedByChars().contains(c)));

        FXUtility.addChangeListenerPlatformNN(textField.textProperty(), text ->{
            parent.changed(OperatorEntry.this);
            FXUtility.setPseudoclass(textField, "op-empty", text.isEmpty());
        });

        if (userEntered)
        {
            // Do this after auto-complete is set up and we are set as part of parent,
            // in case it finishes a completion:
            FXUtility.runAfter(() -> {
                textField.setText(content);
                initialContentEntered.set(true);
            });
        }
    }

    @Override
    protected Stream<Node> calculateNodes()
    {
        return Stream.of(container.getFirst());
    }

    private static <EXPRESSION extends @NonNull LoadableExpression<EXPRESSION, SEMANTIC_PARENT>, SEMANTIC_PARENT> List<Completion> getCompletions(ConsecutiveBase<EXPRESSION, SEMANTIC_PARENT> parent, List<Pair<String, @Localized String>> validOperators, String s)
    {
        ArrayList<Completion> r = new ArrayList<>();
        for (Character c : parent.terminatedByChars())
        {
            r.add(new KeyShortcutCompletion("End bracketed expressions", c));
        }
        for (Pair<String, @Localized String> operator : validOperators)
        {
            r.add(new SimpleCompletion(operator.getFirst(), operator.getSecond()));
        }
        r.removeIf(c -> !c.shouldShow(s));
        return r;
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
        FXUtility.setPseudoclass(container.getFirst(), "exp-selected", selected);
    }

    @Override
    public boolean isBlank()
    {
        return textField.getText().isEmpty();
    }

    @Override
    public boolean isOrContains(EEDisplayNode child)
    {
        return this == child;
    }

    @Override
    public void focus(Focus side)
    {
        super.focus(side);
        FXUtility.onceTrue(initialContentEntered, () -> {
            // Only if we haven't lost focus in the mean time, adjust ours:
            if (isFocused())
                super.focus(side);
        });
    }

    @Override
    public void showType(String type)
    {
        container.getSecond().showType(type);
    }

    @Override
    public void showError(StyledString error, List<QuickFix<EXPRESSION>> quickFixes)
    {
        container.getSecond().showError(error, quickFixes);
    }

    @Override
    public void clearError()
    {
        container.getSecond().clearError();
    }

    @Override
    public boolean isShowingError()
    {
        return container.getSecond().isShowingError();
    }

    @Override
    public void cleanup()
    {
        container.getSecond().cleanup();
    }

    private static class SimpleCompletion extends Completion
    {
        private final String operator;
        private final @Localized String description;

        public SimpleCompletion(String operator, @Localized String description)
        {
            this.operator = operator;
            this.description = description;
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

    private class CompletionListener extends SimpleCompletionListener
    {
        public CompletionListener()
        {
        }

        @Override
        protected String selected(String currentText, @Nullable Completion c, String rest)
        {
            if (c instanceof SimpleCompletion)
            {
                OperatorOutcome outcome = parent.addOperandToRight(OperatorEntry.this, currentText, rest, true);
                switch (outcome)
                {
                    case KEEP:
                        return ((SimpleCompletion) c).operator;
                    default:
                        return "";
                }

            }
            else if (c instanceof KeyShortcutCompletion)
            {
                parent.parentFocusRightOfThis(Focus.LEFT);
                return "";
            }
            return textField.getText();
        }

        @Override
        public String focusLeaving(String currentText, AutoComplete.@Nullable Completion selectedItem)
        {
            if (selectedItem != null && selectedItem instanceof SimpleCompletion)
            {
                SimpleCompletion c = (SimpleCompletion)selectedItem;
                // Has to be exact match if leaving slot:
                if (c.operator.equals(currentText))
                {
                    return selected(currentText, c, "");
                }
            }
            return currentText;
        }
    }
}
