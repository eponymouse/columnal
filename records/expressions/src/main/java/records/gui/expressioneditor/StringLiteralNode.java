package records.gui.expressioneditor;

import javafx.beans.value.ObservableStringValue;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.expressioneditor.AutoComplete.Completion;
import records.gui.expressioneditor.AutoComplete.EndCompletion;
import records.gui.expressioneditor.AutoComplete.SimpleCompletionListener;
import records.gui.expressioneditor.AutoComplete.WhitespacePolicy;
import records.gui.expressioneditor.ExpressionEditorUtil.ErrorTop;
import records.transformations.expression.Expression;
import records.transformations.expression.QuickFix;
import styled.StyledString;
import utility.Pair;
import utility.gui.FXUtility;

import java.util.List;
import java.util.stream.Stream;

/**
 * Created by neil on 20/12/2016.
 */
public class StringLiteralNode extends EntryNode<Expression, ExpressionSaver> implements ConsecutiveChild<Expression, ExpressionSaver>
{
    private final AutoComplete autoComplete;
    private final ErrorTop container;
    private final ExpressionInfoDisplay expressionInfoDisplay;

    public StringLiteralNode(String initialValue, ConsecutiveBase<Expression, ExpressionSaver> parent)
    {
        super(parent, Expression.class);
        // We need a completion so you can leave the field using tab/enter
        // Otherwise only right-arrow will get you out
        EndCompletion currentCompletion = new EndCompletion("\"");
        this.autoComplete = new AutoComplete<EndCompletion>(textField, (s, q) ->
        {
            return Stream.of(currentCompletion);
        }, new SimpleCompletionListener<EndCompletion>()
        {
            @Override
            public String exactCompletion(String currentText, EndCompletion selectedItem)
            {
                super.exactCompletion(currentText, selectedItem);
                if (currentText.endsWith("\""))
                    return currentText.substring(0, currentText.length() - 1);
                else
                    return currentText;
            }

            @Override
            protected String selected(String currentText, @Nullable EndCompletion c, String rest, boolean moveFocus)
            {
                if (moveFocus)
                    parent.focusRightOf(FXUtility.mouse(StringLiteralNode.this), Focus.LEFT);
                return currentText;
            }

            @Override
            public String focusLeaving(String currentText, @Nullable EndCompletion selectedItem)
            {
                return currentText;
            }

            @Override
            public void tabPressed()
            {
                parent.focusRightOf(FXUtility.keyboard(StringLiteralNode.this), Focus.LEFT);
            }

            @Override
            protected boolean isFocused()
            {
                return StringLiteralNode.this.isFocused();
            }
        }, WhitespacePolicy.ALLOW_ANYWHERE, (cur, next) -> false);

        FXUtility.sizeToFit(textField, 10.0, 10.0);
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
    public void save(ExpressionSaver saver)
    {
        saver.saveOperand(new records.transformations.expression.StringLiteral(textField.getText()), this, this, c -> {});
    }

    @Override
    public void unmaskErrors()
    {
        expressionInfoDisplay.unmaskErrors();
    }

    @Override
    public void addErrorAndFixes(StyledString error, List<QuickFix<Expression,ExpressionSaver>> quickFixes)
    {
        expressionInfoDisplay.addMessageAndFixes(error, quickFixes, parent);
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
    public boolean deleteLast()
    {
        return false;
    }

    @Override
    public boolean deleteFirst()
    {
        return false;
    }

    @Override
    public Stream<Pair<String, Boolean>> _test_getHeaders()
    {
        return container._test_getHeaderState();
    }

    @Override
    public void setText(String initialContent)
    {
        textField.setText(initialContent);
        textField.positionCaret(textField.getLength());
    }

}
