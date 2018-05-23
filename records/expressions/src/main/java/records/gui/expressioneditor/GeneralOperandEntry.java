package records.gui.expressioneditor;

import annotation.recorded.qual.UnknownIfRecorded;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import records.gui.expressioneditor.ExpressionEditorUtil.ErrorTop;
import records.transformations.expression.QuickFix;
import records.transformations.expression.QuickFix.ReplacementTarget;
import records.transformations.expression.LoadableExpression;
import styled.StyledShowable;
import styled.StyledString;
import utility.Pair;
import utility.gui.FXUtility;

import java.util.List;
import java.util.stream.Stream;

/**
 * A helper class that implements various methods when you
 * have a single text field as a ConsecutiveChild
 *
 */
abstract class GeneralOperandEntry<EXPRESSION extends StyledShowable, SEMANTIC_PARENT> extends EntryNode<EXPRESSION, SEMANTIC_PARENT> implements ConsecutiveChild<EXPRESSION, SEMANTIC_PARENT>, ErrorDisplayer<EXPRESSION, SEMANTIC_PARENT>, OperandNode<EXPRESSION, SEMANTIC_PARENT>
{
    /**
     * A label to the left of the text-field, used for displaying things like the
     * arrows on column reference
     */
    protected final Label prefix;

    /**
     * The label which sits at the top describing the type
     */
    protected final Label typeLabel;

    /**
     * The outermost container for the whole thing:
     */
    protected final ErrorTop container;

    private final ExpressionInfoDisplay expressionInfoDisplay;
    
    protected @MonotonicNonNull AutoComplete<?> autoComplete;

    protected GeneralOperandEntry(Class<EXPRESSION> operandClass, ConsecutiveBase<EXPRESSION, SEMANTIC_PARENT> parent)
    {
        super(parent, operandClass);

        FXUtility.sizeToFit(textField, null, null);
        typeLabel = new Label();
        typeLabel.getStyleClass().addAll("entry-type", "labelled-top");
        ExpressionEditorUtil.enableSelection(typeLabel, this);
        ExpressionEditorUtil.enableDragFrom(typeLabel, this);
        prefix = new Label();
        container = new ErrorTop(typeLabel, new HBox(prefix, textField));
        container.getStyleClass().add("entry");
        this.expressionInfoDisplay = ExpressionEditorUtil.installErrorShower(container, typeLabel, textField);
        ExpressionEditorUtil.setStyles(typeLabel, parent.getParentStyles());
    }

    @Override
    public boolean isBlank()
    {
        return textField.getText().trim().isEmpty();
    }

    @Override
    public void setSelected(boolean selected)
    {
        FXUtility.setPseudoclass(container, "exp-selected", selected);
    }

    public void changed(@UnknownInitialization(EEDisplayNode.class) EEDisplayNode child)
    {
        parent.changed(this);
    }

    @Override
    public void addErrorAndFixes(StyledString error, List<QuickFix<EXPRESSION, SEMANTIC_PARENT>> quickFixes)
    {
        container.setError(true);
        expressionInfoDisplay.addMessageAndFixes(error, quickFixes, getParent().getEditor().getWindow(), getParent().getEditor().getTableManager(), (Pair<ReplacementTarget, @UnknownIfRecorded LoadableExpression<EXPRESSION, SEMANTIC_PARENT>> e) -> {
            getParent().replaceLoad(this, e);
        });
    }

    @Override
    public boolean isShowingError()
    {
        return expressionInfoDisplay.isShowingError();
    }

    protected static <T extends EEDisplayNode> T focusWhenShown(T node)
    {
        node.focusWhenShown();
        return node;
    }

    @Override
    public void clearAllErrors()
    {
        container.setError(false);
        expressionInfoDisplay.clearError();
    }

    @Override
    public void showType(String type)
    {
        expressionInfoDisplay.setType(type);
    }

    @Override
    public void cleanup()
    {
        if (autoComplete != null)
        {
            autoComplete.hide();
        }
        expressionInfoDisplay.hideImmediately();
    }

    @Override
    public Stream<Pair<String, Boolean>> _test_getHeaders()
    {
        return container._test_getHeaderState();
    }
}
