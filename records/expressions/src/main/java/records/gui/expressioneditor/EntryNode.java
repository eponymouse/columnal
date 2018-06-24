package records.gui.expressioneditor;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import log.Log;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.transformations.expression.Expression;
import records.transformations.expression.LoadableExpression;
import styled.StyledShowable;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.gui.FXUtility;

import java.util.stream.Stream;

/**
 * A helper class that shares code between various text-field based nodes.
 */
public abstract class EntryNode<EXPRESSION extends StyledShowable, SEMANTIC_PARENT> extends DeepNodeTree implements EEDisplayNode, ConsecutiveChild<EXPRESSION, SEMANTIC_PARENT>, ErrorDisplayer<EXPRESSION, SEMANTIC_PARENT>
{
    protected final ConsecutiveBase<EXPRESSION, SEMANTIC_PARENT> parent;
    private final Class<EXPRESSION> expressionClass;

    protected final LeaveableTextField textField;
    private boolean focusPending;

    @SuppressWarnings("initialization")
    public EntryNode(ConsecutiveBase<EXPRESSION, SEMANTIC_PARENT> parent, Class<EXPRESSION> expressionClass)
    {
        this.parent = parent;
        this.expressionClass = expressionClass;
        textField = new LeaveableTextField(this, parent) {
            @Override
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public void home()
            {
                parent.focusBlankAtLeft();
            }
        };
        textField.getStyleClass().add("entry-field");
    }
    
    // Don't understand why the checker doesn't see that this method is OK:
    @SuppressWarnings("nullness")
    // Although we don't extend OperandNode, this deliberately implements a method from OperandNode:
    public final ConsecutiveBase<EXPRESSION, SEMANTIC_PARENT> getParent(@UnknownInitialization(EntryNode.class) EntryNode<EXPRESSION, SEMANTIC_PARENT> this)
    {
        return parent;
    }

    // Although we don't extend OperandNode, this deliberately implements a method from OperandNode:
    public void prompt(String prompt)
    {
        textField.setPromptText(prompt);
    }

    @Override
    public boolean isFocused()
    {
        return textField.isFocused();
    }

    @Override
    public void setSelected(boolean selected)
    {
        // TODO
    }

    @Override
    public void setHoverDropLeft(boolean on)
    {
        FXUtility.setPseudoclass(nodes().get(0), "exp-hover-drop-left", on);
    }

    @Override
    public void focusChanged()
    {
        // Nothing to do
    }

    @Override
    public void focusWhenShown()
    {
        Log.debug("### Focusing when shown: " + textField);
        focusPending = true;
        FXUtility.onceNotNull(textField.sceneProperty(), s -> FXUtility.runAfter(() -> {
            flushFocusRequest();
        }));
    }

    /**
     * If waiting to request focus, do that now.
     */
    @Override
    public void flushFocusRequest()
    {
        if (focusPending)
        {
            focus(Focus.RIGHT);
            focusPending = false;
        }
    }

    @Override
    public boolean isFocusPending()
    {
        return focusPending;
    }

    @Override
    public void focus(Focus side)
    {
        Log.debug("### Focusing now: " + textField + " Editable: " + textField.isEditable() + " Scene: " + textField.getScene());
        textField.requestFocus();
        Log.debug("  Focused? " + textField.isFocused());
        if (side == Focus.LEFT)
        {
            textField.positionCaret(0);
        }
        else
        {
            textField.positionCaret(textField.getLength());
        }
    }

    @Override
    public void visitLocatable(LocatableVisitor visitor)
    {
        visitor.register(this, expressionClass);
    }

    @Override
    protected Stream<EEDisplayNode> calculateChildren()
    {
        return Stream.empty();
    }

    // Should also trigger completions if applicable
    public abstract void setText(String initialContent);
}
