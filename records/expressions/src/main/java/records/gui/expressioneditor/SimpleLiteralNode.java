package records.gui.expressioneditor;

import javafx.beans.value.ObservableStringValue;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.expressioneditor.AutoComplete.Completion;
import records.gui.expressioneditor.AutoComplete.SimpleCompletionListener;
import records.gui.expressioneditor.AutoComplete.WhitespacePolicy;
import records.gui.expressioneditor.ConsecutiveBase.BracketBalanceType;
import records.gui.expressioneditor.ExpressionEditorUtil.ErrorTop;
import records.transformations.expression.Expression;
import records.transformations.expression.QuickFix;
import styled.StyledString;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.List;
import java.util.stream.Stream;

public abstract class SimpleLiteralNode extends EntryNode<Expression, ExpressionSaver> implements ConsecutiveChild<Expression, ExpressionSaver>
{
    protected final AutoComplete autoComplete;
    protected final ErrorTop container;
    protected final ExpressionInfoDisplay expressionInfoDisplay;

    public SimpleLiteralNode(ConsecutiveBase<Expression, ExpressionSaver> parent, Class<Expression> expressionClass, String ending)
    {
        super(parent, expressionClass);

        // We need a completion so you can leave the field using tab/enter
        // Otherwise only right-arrow will get you out
        EndCompletion currentCompletion = new EndCompletion(ending);
        this.autoComplete = new AutoComplete<EndCompletion>(textField, (s, q) ->
        {
            return Stream.of(currentCompletion);
        }, new SimpleCompletionListener<EndCompletion>()
        {
            @Override
            public String exactCompletion(String currentText, EndCompletion selectedItem)
            {
                super.exactCompletion(currentText, selectedItem);
                parent.focusRightOf(FXUtility.mouse(SimpleLiteralNode.this), Focus.LEFT);
                if (currentText.endsWith(ending))
                    return currentText.substring(0, currentText.length() - 1);
                else
                    return currentText;
            }

            @Override
            protected String selected(String currentText, @Nullable EndCompletion c, String rest, boolean moveFocus)
            {
                if (moveFocus)
                    parent.focusRightOf(FXUtility.mouse(SimpleLiteralNode.this), Focus.LEFT);
                return currentText;
            }

            @Override
            public String focusLeaving(String currentText, @Nullable EndCompletion selectedItem)
            {
                if (selectedItem == null)
                    return currentText;
                else
                    return exactCompletion(currentText, selectedItem);
            }

            @Override
            public void tabPressed()
            {
                parent.focusRightOf(FXUtility.keyboard(SimpleLiteralNode.this), Focus.LEFT);
            }

            @Override
            protected boolean isFocused()
            {
                return SimpleLiteralNode.this.isFocused();
            }
        }, WhitespacePolicy.ALLOW_ANYWHERE, (cur, next) -> Utility.containsCodepoint(ending, next));
        
        // We don't put anything in the type label, because it's clearly a String:
        Label typeLabel = new Label();
        typeLabel.getStyleClass().addAll("entry-type", "labelled-top");
        ExpressionEditorUtil.enableSelection(typeLabel, this);
        ExpressionEditorUtil.enableDragFrom(typeLabel, this);
        container = new ErrorTop(typeLabel, new BorderPane(textField, null, new Label("\u201D"), null, new Label("\u201C")));
        this.expressionInfoDisplay = parent.getEditor().installErrorShower(container, typeLabel, textField);
        ExpressionEditorUtil.setStyles(typeLabel, parent.getParentStyles());

        updateNodes();
    }

    @Override
    public void unmaskErrors()
    {
        expressionInfoDisplay.unmaskErrors();
    }

    @Override
    public boolean isShowingError()
    {
        return expressionInfoDisplay.isShowingError();
    }

    @Override
    public void clearAllErrors()
    {
        expressionInfoDisplay.clearError();
    }
    
    @Override
    protected Stream<Node> calculateNodes()
    {
        return Stream.of(container);
    }

    @Override
    public void addErrorAndFixes(StyledString error, List<QuickFix<Expression,ExpressionSaver>> quickFixes)
    {
        expressionInfoDisplay.addMessageAndFixes(error, quickFixes, parent);
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
        expressionInfoDisplay.hideImmediately();
    }

    @Override
    public Stream<Pair<String, Boolean>> _test_getHeaders()
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

    protected class EndCompletion extends Completion
    {
        private final String ending;

        public EndCompletion(String ending)
        {
            this.ending = ending;
        }
        
        @Override
        public CompletionContent getDisplay(ObservableStringValue currentText)
        {
            return new CompletionContent(currentText.get() + ending, null);
        }

        @Override
        public ShowStatus shouldShow(String input)
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
    }
}
