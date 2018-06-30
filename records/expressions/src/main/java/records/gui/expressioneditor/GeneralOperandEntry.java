package records.gui.expressioneditor;

import annotation.recorded.qual.UnknownIfRecorded;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import records.gui.expressioneditor.ConsecutiveBase.BracketBalanceType;
import records.gui.expressioneditor.ExpressionEditorUtil.ErrorTop;
import records.transformations.expression.BracketedStatus;
import records.transformations.expression.QuickFix;
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
abstract class GeneralOperandEntry<EXPRESSION extends StyledShowable, SEMANTIC_PARENT> extends EntryNode<EXPRESSION, SEMANTIC_PARENT> implements ConsecutiveChild<EXPRESSION, SEMANTIC_PARENT>, ErrorDisplayer<EXPRESSION, SEMANTIC_PARENT>
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


    /**
     * Set to true while updating field with auto completion.  Allows us to avoid
     * certain listeners firing which should only fire when the user has made a change.
     */
    protected boolean completing;

    protected GeneralOperandEntry(Class<EXPRESSION> operandClass, ConsecutiveBase<EXPRESSION, SEMANTIC_PARENT> parent)
    {
        super(parent, operandClass);
        
        FXUtility.sizeToFit(textField, 3.0, 3.0);
        typeLabel = new Label();
        typeLabel.getStyleClass().addAll("entry-type", "labelled-top");
        ExpressionEditorUtil.enableSelection(typeLabel, this);
        ExpressionEditorUtil.enableDragFrom(typeLabel, this);
        prefix = new Label();
        container = new ErrorTop(typeLabel, new HBox(prefix, textField));
        container.getStyleClass().add("entry");
        this.expressionInfoDisplay = ExpressionEditorUtil.installErrorShower(container, typeLabel, textField);
        ExpressionEditorUtil.setStyles(typeLabel, parent.getParentStyles());
        
        GeneralOperandEntry us = FXUtility.mouse(this);
        textField.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (!us.availableForFocus())
            {
                if (textField.getCaretPosition() < textField.getLength() / 2)
                {
                    parent.focusLeftOf(us);
                }
                else
                {
                    parent.focusRightOf(us, Focus.LEFT);
                }
            }
        });
    }

    @Override
    public boolean isBlank(@UnknownInitialization(Object.class) GeneralOperandEntry<EXPRESSION, SEMANTIC_PARENT> this)
    {
        return textField == null || (textField.getText().trim().isEmpty() && !completing);
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
        expressionInfoDisplay.addMessageAndFixes(error, quickFixes, getParent());
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

    @Override
    public boolean deleteLast()
    {
        if (availableForFocus())
        {
            textField.requestFocus();
            textField.end();
            textField.deletePreviousChar();
            return true;
        }
        return false;
    }

    @Override
    public boolean deleteFirst()
    {
        if (availableForFocus())
        {
            textField.requestFocus();
            textField.home();
            textField.deleteNextChar();
            return true;
        }
        return false;
    }

    @Override
    public void unmaskErrors()
    {
        expressionInfoDisplay.unmaskErrors();
    }

    @Override
    public void setText(String initialContent)
    {
        textField.setText(initialContent);
        textField.positionCaret(textField.getLength());
    }

    @Override
    public boolean opensBracket(BracketBalanceType bracketBalanceType)
    {
        return (bracketBalanceType == BracketBalanceType.ROUND && textField.getText().equals("(")) || (bracketBalanceType == BracketBalanceType.SQUARE && textField.getText().equals("["));
    }

    @Override
    public boolean closesBracket(BracketBalanceType bracketBalanceType)
    {
        return (bracketBalanceType == BracketBalanceType.ROUND && textField.getText().equals(")")) || (bracketBalanceType == BracketBalanceType.SQUARE && textField.getText().equals("]"));
    }
}
