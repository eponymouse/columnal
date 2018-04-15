package records.gui.expressioneditor;

import annotation.recorded.qual.UnknownIfRecorded;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableStringValue;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.expressioneditor.AutoComplete.Completion;
import records.gui.expressioneditor.AutoComplete.KeyShortcutCompletion;
import records.gui.expressioneditor.AutoComplete.SimpleCompletionListener;
import records.gui.expressioneditor.AutoComplete.WhitespacePolicy;
import records.gui.expressioneditor.ConsecutiveBase.OperatorOutcome;
import records.gui.expressioneditor.ExpressionEditorUtil.ErrorTop;
import records.transformations.expression.ErrorAndTypeRecorder.QuickFix;
import records.transformations.expression.ErrorAndTypeRecorder.QuickFix.ReplacementTarget;
import records.transformations.expression.LoadableExpression;
import styled.StyledShowable;
import styled.StyledString;
import utility.Pair;
import utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Created by neil on 17/12/2016.
 */
public class OperatorEntry<EXPRESSION extends StyledShowable, SEMANTIC_PARENT> extends EntryNode<EXPRESSION, SEMANTIC_PARENT> implements ConsecutiveChild<EXPRESSION, SEMANTIC_PARENT>, ErrorDisplayer<EXPRESSION, SEMANTIC_PARENT>
{
    /**
     * The outermost container for the whole thing:
     */
    private final Pair<ErrorTop, ErrorDisplayer<EXPRESSION, SEMANTIC_PARENT>> container;
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
        container = ExpressionEditorUtil.withLabelAbove(textField, "operator", "", this, getParent().getEditor(), (Pair<ReplacementTarget, @UnknownIfRecorded LoadableExpression<EXPRESSION, SEMANTIC_PARENT>> e) -> parent.replaceWholeLoad(FXUtility.mouse(this), e.getSecond()), parent.getParentStyles());
        container.getFirst().getStyleClass().add("entry");
        updateNodes();

        this.autoComplete = new AutoComplete<Completion>(textField, (s, q) -> getCompletions(parent, parent.operations.getValidOperators(parent.getThisAsSemanticParent()), s), new CompletionListener(), WhitespacePolicy.DISALLOW, c -> c == '-' || (!parent.operations.isOperatorAlphabet(c, parent.getThisAsSemanticParent()) && !parent.terminatedByChars().contains(c)));

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

    private static <EXPRESSION extends StyledShowable, SEMANTIC_PARENT> List<Completion> getCompletions(ConsecutiveBase<EXPRESSION, SEMANTIC_PARENT> parent, List<Pair<String, @Localized String>> validOperators, String s)
    {
        ArrayList<Completion> r = new ArrayList<>();
        for (Character c : parent.terminatedByChars())
        {
            r.add(new KeyShortcutCompletion("autocomplete.bracket.end", c));
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
    public Stream<Pair<String, Boolean>> _test_getHeaders()
    {
        return this.container.getFirst()._test_getHeaderState();
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
    public void addErrorAndFixes(StyledString error, List<QuickFix<EXPRESSION, SEMANTIC_PARENT>> quickFixes)
    {
        container.getSecond().addErrorAndFixes(error, quickFixes);
    }

    @Override
    public void clearAllErrors()
    {
        container.getSecond().clearAllErrors();
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
        public CompletionContent getDisplay(ObservableStringValue currentText)
        {
            return new CompletionContent(new Pair<String, @Localized String>(operator, this.description));
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
    public String toString()
    {
        // Useful for debugging:
        return super.toString() + ";" + textField;
    }
    
    private class CompletionListener extends SimpleCompletionListener<Completion>
    {
        public CompletionListener()
        {
        }

        @Override
        protected @Nullable String selected(String currentText, @Nullable Completion c, String rest)
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
            return null;
        }

        @Override
        public @Nullable String focusLeaving(String currentText, AutoComplete.@Nullable Completion selectedItem)
        {
            if (textField.leavingByCursorLeft())
                return null;
            
            if (selectedItem != null && selectedItem instanceof SimpleCompletion)
            {
                SimpleCompletion c = (SimpleCompletion)selectedItem;
                // Has to be exact match if leaving slot:
                if (c.operator.equals(currentText))
                {
                    return selected(currentText, c, "");
                }
            }
            return null;
        }
    }
}
