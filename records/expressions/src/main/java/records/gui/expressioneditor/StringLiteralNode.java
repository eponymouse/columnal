package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import javafx.beans.value.ObservableObjectValue;
import javafx.beans.value.ObservableStringValue;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.expressioneditor.AutoComplete.Completion;
import records.gui.expressioneditor.AutoComplete.SimpleCompletionListener;
import records.gui.expressioneditor.AutoComplete.WhitespacePolicy;
import records.gui.expressioneditor.ExpressionEditorUtil.ErrorTop;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.Expression;
import styled.StyledString;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.gui.FXUtility;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Created by neil on 20/12/2016.
 */
public class StringLiteralNode extends EntryNode<Expression, ExpressionNodeParent> implements OperandNode<Expression, ExpressionNodeParent>
{
    private final AutoComplete autoComplete;
    private final ErrorTop container;
    private final ExpressionInfoDisplay expressionInfoDisplay;

    public StringLiteralNode(String initialValue, ConsecutiveBase<Expression, ExpressionNodeParent> parent)
    {
        super(parent, Expression.class);
        // We need a completion so you can leave the field using tab/enter
        // Otherwise only right-arrow will get you out
        Completion currentCompletion = new EndStringCompletion();
        this.autoComplete = new AutoComplete(textField, (s, q) ->
        {
            return Collections.singletonList(currentCompletion);
        }, new SimpleCompletionListener()
        {
            @Override
            public String exactCompletion(String currentText, Completion selectedItem)
            {
                super.exactCompletion(currentText, selectedItem);
                if (currentText.endsWith("\""))
                    return currentText.substring(0, currentText.length() - 1);
                else
                    return currentText;
            }

            @Override
            protected String selected(String currentText, @Nullable Completion c, String rest)
            {
                parent.setOperatorToRight(StringLiteralNode.this, "");
                parent.focusRightOf(StringLiteralNode.this, Focus.LEFT);
                return currentText;
            }

            @Override
            public String focusLeaving(String currentText, AutoComplete.@Nullable Completion selectedItem)
            {
                return currentText;
            }
        }, WhitespacePolicy.ALLOW_ANYWHERE, c -> false);

        FXUtility.addChangeListenerPlatformNN(textField.textProperty(), text -> parent.changed(this));
        textField.setText(initialValue);

        // We don't put anything in the type label, because it's clearly a String:
        Label typeLabel = new Label();
        typeLabel.getStyleClass().addAll("entry-type", "labelled-top");
        ExpressionEditorUtil.enableSelection(typeLabel, this);
        ExpressionEditorUtil.enableDragFrom(typeLabel, this);
        container = new ErrorTop(typeLabel, new BorderPane(textField, null, new Label("\u201D"), null, new Label("\u201C")));
        this.expressionInfoDisplay = ExpressionEditorUtil.installErrorShower(container, typeLabel, textField);
        ExpressionEditorUtil.setStyles(typeLabel, parent.getParentStyles());
        
        updateNodes();
    }

    @Override
    protected Stream<Node> calculateNodes()
    {
        return Stream.of(container);
    }

    @Override
    public @Recorded Expression save(ErrorDisplayerRecord errorDisplayer, ErrorAndTypeRecorder onError)
    {
        return errorDisplayer.record(this, new records.transformations.expression.StringLiteral(textField.getText()));
    }

    @Override
    public @Nullable ObservableObjectValue<@Nullable String> getStyleWhenInner()
    {
        return null;
    }

    @Override
    public void addErrorAndFixes(StyledString error, List<ErrorAndTypeRecorder.QuickFix<Expression>> quickFixes)
    {
        expressionInfoDisplay.addMessageAndFixes(error, quickFixes, parent.getEditor().getWindow(), parent.getEditor().getTableManager(), e -> parent.replaceLoad(this, e));
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
            if (input.endsWith("\""))
                return CompletionAction.COMPLETE_IMMEDIATELY;

            return CompletionAction.SELECT;
        }

        @Override
        public boolean features(String curInput, char character)
        {
            return true;
        }
    }
}
