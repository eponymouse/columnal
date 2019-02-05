package records.gui.expressioneditor;

import com.google.common.collect.ImmutableList;
import com.sun.javafx.scene.control.skin.TextFieldSkin;
import com.sun.javafx.scene.text.HitInfo;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import log.Log;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.expressioneditor.ConsecutiveBase.BracketBalanceType;
import records.gui.expressioneditor.ExpressionEditorUtil.ErrorTop;
import records.gui.expressioneditor.TopLevelEditor.ErrorInfo;
import records.transformations.expression.QuickFix;
import styled.StyledShowable;
import styled.StyledString;
import utility.FXPlatformRunnable;
import utility.Pair;
import utility.gui.FXUtility;

import java.util.List;
import java.util.stream.Stream;

/**
 * A helper class that implements various methods when you
 * have a single text field as a ConsecutiveChild
 *
 */
abstract class GeneralOperandEntry<EXPRESSION extends StyledShowable, SAVER extends ClipboardSaver> extends EntryNode<EXPRESSION, SAVER> implements ConsecutiveChild<EXPRESSION, SAVER>, ErrorDisplayer<EXPRESSION, SAVER>
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

    protected GeneralOperandEntry(Class<EXPRESSION> operandClass, ConsecutiveBase<EXPRESSION, SAVER> parent)
    {
        super(parent, operandClass);
        
        FXUtility.sizeToFit(textField, 3.0, 3.0);
        typeLabel = new Label("");
        typeLabel.getStyleClass().addAll("entry-type", "labelled-top");
        ExpressionEditorUtil.enableSelection(typeLabel, this, textField);
        ExpressionEditorUtil.enableDragFrom(typeLabel, this);
        prefix = new Label();
        container = new ErrorTop(typeLabel, new HBox(prefix, textField));
        container.getStyleClass().add("entry");
        this.expressionInfoDisplay = parent.getEditor().installErrorShower(container, typeLabel, textField, this);
        ExpressionEditorUtil.setStyles(typeLabel, parent.getParentStyles());
        
        GeneralOperandEntry us = FXUtility.mouse(this);
        textField.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (!us.availableForFocus())
            {
                // This will need updating in JavaFX 9 to use the public API:
                HitInfo hit = ((TextFieldSkin) textField.getSkin()).getIndex(e.getX(), e.getY());
                if ((double)hit.getInsertionIndex() < (double)textField.getLength() / 2.0)
                {
                    parent.focusLeftOf(us);
                }
                else
                {
                    parent.focusRightOf(us, Focus.LEFT, false);
                }
            }
        });
    }

    @Override
    public boolean isBlank(@UnknownInitialization(Object.class) GeneralOperandEntry<EXPRESSION, SAVER> this)
    {
        return textField == null || (textField.getText().trim().isEmpty() && !completing);
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

    public void changed(@UnknownInitialization(EEDisplayNode.class) EEDisplayNode child)
    {
        parent.changed(this);
    }

    @Override
    public void addErrorAndFixes(StyledString error, List<QuickFix<EXPRESSION, SAVER>> quickFixes)
    {
        container.setError(true);
        expressionInfoDisplay.addMessageAndFixes(error, quickFixes, getParent());
    }

    @Override
    public ImmutableList<ErrorInfo> getErrors()
    {
        return expressionInfoDisplay.getErrors();
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
    public void saved()
    {
        expressionInfoDisplay.saved();
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
    }

    @Override
    public Stream<Pair<Label, Boolean>> _test_getHeaders()
    {
        return container._test_getHeaderState();
    }

    @Override
    public boolean deleteLast()
    {
        if (availableForFocus())
        {
            textField.requestFocus();
            textField.positionCaret(textField.getLength());
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
            textField.positionCaret(0);
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
    public void setText(String initialContent, int caretPos)
    {
        if (autoComplete != null)
        {
            autoComplete.withProspectiveCaret(caretPos, () ->
                textField.setText(initialContent)
            );
        }
        else
        {
            textField.setText(initialContent);
            textField.positionCaret(caretPos);
        }
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

    @Override
    public boolean mergeFromRight(ConsecutiveChild<EXPRESSION, SAVER> right)
    {
        if (!right.getClass().equals(getClass()))
            return false;

        GeneralOperandEntry<EXPRESSION, SAVER> rightCast = (GeneralOperandEntry<EXPRESSION, SAVER>) right;
        
        if (textField.isEditable() && rightCast.textField.isEditable()
            && autoComplete != null && autoComplete.matchingAlphabets(textField.getText().trim(), rightCast.textField.getText().trim()))
        {
            Log.debug("Merging from right: " + textField.getText() + " and " + rightCast.textField.getText());
            int origLength = textField.getText().trim().length();
            textField.setText(textField.getText().trim() + rightCast.textField.getText().trim());
            if (rightCast.textField.isFocused())
            {
                textField.requestFocus();
                textField.positionCaret(origLength);
            }
            return true;
        }
        return false;
    }
}
