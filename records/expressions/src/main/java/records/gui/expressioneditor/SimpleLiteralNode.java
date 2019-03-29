package records.gui.expressioneditor;

import com.google.common.collect.ImmutableList;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableStringValue;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.expressioneditor.AutoComplete.Completion;
import records.gui.expressioneditor.AutoComplete.SimpleCompletionListener;
import records.gui.expressioneditor.AutoComplete.WhitespacePolicy;
import records.gui.expressioneditor.ConsecutiveBase.BracketBalanceType;
import records.gui.expressioneditor.ExpressionEditorUtil.ErrorTop;
import records.gui.expressioneditor.TopLevelEditor.ErrorInfo;
import records.transformations.expression.Expression;
import records.transformations.expression.QuickFix;
import styled.StyledString;
import utility.Either;
import utility.FXPlatformRunnable;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.GUI;

import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Stream;

public abstract class SimpleLiteralNode extends EntryNode<Expression, ExpressionSaver> implements ConsecutiveChild<Expression, ExpressionSaver>
{
    protected final AutoComplete autoComplete;
    protected final ErrorTop container;
    protected final ExpressionInfoDisplay expressionInfoDisplay;
    private final Label typeLabel;

    public SimpleLiteralNode(ConsecutiveBase<Expression, ExpressionSaver> parent, Class<Expression> expressionClass, String ending)
    {
        super(parent, expressionClass);

        // We need a completion so you can leave the field using tab/enter
        // Otherwise only right-arrow will get you out
        EndCompletion currentCompletion = new EndCompletion(ending);
        this.autoComplete = new AutoComplete<EndCompletion>(textField, (s, c, q) ->
        {
            return Stream.of(currentCompletion);
        }, new SimpleCompletionListener<EndCompletion>()
        {
            @Override
            public String exactCompletion(String currentText, EndCompletion selectedItem)
            {
                return exact(currentText, selectedItem, true);
            }

            private String exact(String currentText, EndCompletion selectedItem, boolean moveFocus)
            {
                super.exactCompletion(currentText, selectedItem);
                if (moveFocus)
                    parent.focusRightOf(FXUtility.mouse(SimpleLiteralNode.this), Focus.LEFT, false);
                if (currentText.endsWith(ending))
                    return currentText.substring(0, currentText.length() - 1);
                else
                    return currentText;
            }

            @Override
            protected String selected(String currentText, @Nullable EndCompletion c, String rest, OptionalInt moveFocus)
            {
                if (moveFocus.isPresent())
                    parent.focusRightOf(FXUtility.mouse(SimpleLiteralNode.this), Either.right(moveFocus.getAsInt()), false);
                return currentText;
            }

            @Override
            public String focusLeaving(String currentText, @Nullable EndCompletion selectedItem)
            {
                if (selectedItem == null)
                    return currentText;
                else
                    return exact(currentText, selectedItem, false);
            }

            @Override
            public void tabPressed()
            {
                parent.focusRightOf(FXUtility.keyboard(SimpleLiteralNode.this), Focus.LEFT, true);
            }

            @Override
            protected boolean isFocused()
            {
                return SimpleLiteralNode.this.isFocused();
            }
        }, () -> true, WhitespacePolicy.ALLOW_ANYWHERE, (cur, next) -> Utility.containsCodepoint(ending, next));
        
        // We don't put anything in the type label, because it's clearly a String:
        typeLabel = new Label();
        typeLabel.getStyleClass().addAll("entry-type", "labelled-top");
        ExpressionEditorUtil.enableSelection(typeLabel, this, textField);
        ExpressionEditorUtil.enableDragFrom(typeLabel, this);
        container = new ErrorTop(typeLabel, new BorderPane(textField, null, GUI.labelRaw(Utility.universal("\u201D"), "literal-delimiter"), null, GUI.labelRaw(Utility.universal("\u201C"), "literal-delimiter")));
        container.getStyleClass().add("entry");
        this.expressionInfoDisplay = parent.getEditor().installErrorShower(container, typeLabel, textField, this);
        ExpressionEditorUtil.setStyles(typeLabel, parent.getParentStyles());
        FXUtility.addChangeListenerPlatformNN(textField.textProperty(), text -> parent.changed(this));
    }

    @Override
    public void unmaskErrors()
    {
        expressionInfoDisplay.unmaskErrors();
    }

    @Override
    public boolean isShowingError(@UnknownInitialization(Object.class) SimpleLiteralNode this)
    {
        return expressionInfoDisplay == null ? false : expressionInfoDisplay.isShowingError();
    }

    @Override
    public void clearAllErrors()
    {
        expressionInfoDisplay.clearError();
    }

    @Override
    public void saved()
    {
        expressionInfoDisplay.saved();
    }

    @Override
    protected Stream<Node> calculateNodes(@UnknownInitialization(DeepNodeTree.class) SimpleLiteralNode this)
    {
        return Utility.streamNullable(container);
    }

    @Override
    public void addErrorAndFixes(StyledString error, List<QuickFix<Expression>> quickFixes)
    {
        expressionInfoDisplay.addMessageAndFixes(error, quickFixes, parent);
    }

    @Override
    public ImmutableList<ErrorInfo> getErrors()
    {
        return expressionInfoDisplay.getErrors();
    }

    @Override
    public void showType(String type)
    {
        expressionInfoDisplay.setType(type);
    }

    @Override
    public boolean isOrContains(EEDisplayNode child)
    {
        return this == child;
    }

    @Override
    public void cleanup()
    {
    }

    @Override
    public final void setSelected(boolean selected, boolean focus, @Nullable FXPlatformRunnable onFocusLost)
    {
        FXUtility.setPseudoclass(container, "exp-selected", selected);
        if (focus)
        {
            typeLabel.requestFocus();
            if (onFocusLost != null)
                FXUtility.onFocusLostOnce(typeLabel, onFocusLost);
        }
    }

    @Override
    public boolean isSelectionFocused()
    {
        return typeLabel.isFocused();
    }

    @Override
    public Stream<Pair<Label, Boolean>> _test_getHeaders()
    {
        return container._test_getHeaderState();
    }

    @Override
    public boolean opensBracket(BracketBalanceType bracketBalanceType)
    {
        return false;
    }

    @Override
    public boolean closesBracket(BracketBalanceType bracketBalanceType)
    {
        return false;
    }

    @Override
    public void setText(String initialContent, int caretPos)
    {
        autoComplete.withProspectiveCaret(caretPos, () -> 
            textField.setText(initialContent)
        );
    }

    @Override
    public boolean isImplicitlyBracketed()
    {
        return true;
    }

    protected class EndCompletion extends Completion
    {
        private final String ending;

        public EndCompletion(String ending)
        {
            this.ending = ending;
        }
        
        @Override
        public CompletionContent makeDisplay(ObservableStringValue currentText)
        {
            return new CompletionContent(Bindings.createStringBinding(() -> currentText.get() + ending, currentText), null);
        }

        @Override
        public ShowStatus shouldShow(String input, int caretPos)
        {
            if (input.endsWith(ending))
                return ShowStatus.DIRECT_MATCH;
            else
                return ShowStatus.PHANTOM;
        }

        @Override
        public boolean features(String curInput, int character)
        {
            return !Utility.containsCodepoint(ending, character);
        }

        @Override
        public boolean completesWhenSingleDirect()
        {
            return true;
        }
    }
}
