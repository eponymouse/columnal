package records.gui.expressioneditor;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.transformations.expression.Expression;
import records.transformations.expression.LoadableExpression;
import styled.StyledShowable;
import utility.Pair;
import utility.gui.FXUtility;

import java.util.stream.Stream;

/**
 * A helper class that shares code between various text-field based nodes.
 */
public abstract class EntryNode<EXPRESSION extends LoadableExpression<EXPRESSION, SEMANTIC_PARENT> & StyledShowable, SEMANTIC_PARENT> extends DeepNodeTree implements EEDisplayNode, ConsecutiveChild<EXPRESSION, SEMANTIC_PARENT>
{
    protected final ConsecutiveBase<EXPRESSION, SEMANTIC_PARENT> parent;
    private final Class<EXPRESSION> expressionClass;

    protected final TextField textField;

    @SuppressWarnings("initialization")
    public EntryNode(ConsecutiveBase<EXPRESSION, SEMANTIC_PARENT> parent, Class<EXPRESSION> expressionClass)
    {
        this.parent = parent;
        this.expressionClass = expressionClass;
        textField = new LeaveableTextField(this, parent);
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
        FXUtility.onceNotNull(textField.sceneProperty(), s -> focus(Focus.RIGHT));
    }


    @Override
    public void focus(Focus side)
    {
        textField.requestFocus();
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
    public <C extends LoadableExpression<C, ?>> @Nullable Pair<ConsecutiveChild<? extends C, ?>, Double> findClosestDrop(Point2D loc, Class<C> forType)
    {
        return ConsecutiveChild.closestDropSingle(this, expressionClass, calculateNodes().findFirst().get(), loc, forType);
    }

    @Override
    protected void updateDisplay()
    {

    }

    @Override
    protected Stream<EEDisplayNode> calculateChildren()
    {
        return Stream.empty();
    }
}
