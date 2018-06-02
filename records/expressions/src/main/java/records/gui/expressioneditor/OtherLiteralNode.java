package records.gui.expressioneditor;


import javafx.beans.value.ObservableObjectValue;
import javafx.scene.Node;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.transformations.expression.Expression;

import java.util.stream.Stream;

// Super-class of TypeLiteralNode/UnitLiteralNode with all the shared functionality
public abstract class OtherLiteralNode extends DeepNodeTree implements EEDisplayNodeParent, ConsecutiveChild<Expression, ExpressionNodeParent>, ErrorDisplayer<Expression, ExpressionNodeParent>
{
    protected final ConsecutiveBase<Expression, ExpressionNodeParent> consecParent;

    protected OtherLiteralNode(ConsecutiveBase<Expression, ExpressionNodeParent> consecParent)
    {
        this.consecParent = consecParent;
    }

    @Override
    protected void updateDisplay()
    {
        
    }

    @Override
    protected Stream<Node> calculateNodes()
    {
        // Note: deliberately don't call calculateNodes() again as that's unneeded work if nothing has changed:
        return getInnerDisplayNode().nodes().stream();
    }

    @Override
    protected Stream<EEDisplayNode> calculateChildren()
    {
        return Stream.of(getInnerDisplayNode());
    }

    // Can't use Java generics to share the types, so we have to have multiple methods which should return the same thing:
    protected abstract EEDisplayNode getInnerDisplayNode();
    protected abstract ErrorDisplayer<?, ?> getInnerErrorDisplayer();

    @Override
    public void focus(Focus side)
    {
        getInnerDisplayNode().focus(side);
    }

    @Override
    public boolean isFocused()
    {
        return getInnerDisplayNode().isFocused();
    }

    @Override
    public void focusWhenShown()
    {
        getInnerDisplayNode().focusWhenShown();
    }

    @Override
    public boolean isOrContains(EEDisplayNode child)
    {
        return getInnerDisplayNode().isOrContains(child);
    }

    @Override
    public void cleanup()
    {
        getInnerDisplayNode().cleanup();
    }

    @Override
    public void focusRightOf(@UnknownInitialization(EEDisplayNode.class) EEDisplayNode child, Focus side)
    {
        consecParent.focusRightOf(this, side);
    }

    @Override
    public void focusLeftOf(@UnknownInitialization(EEDisplayNode.class) EEDisplayNode child)
    {
        consecParent.focusLeftOf(this);
    }

    @Override
    public ConsecutiveBase<Expression, ExpressionNodeParent> getParent()
    {
        return consecParent;
    }

    @Override
    public TopLevelEditor<?, ?> getEditor()
    {
        return consecParent.getEditor();
    }

    @Override
    public Stream<String> getParentStyles()
    {
        return Stream.empty();
    }

    @Override
    public void changed(@UnknownInitialization(EEDisplayNode.class) EEDisplayNode child)
    {
        consecParent.changed(this);
    }

    @Override
    public void clearAllErrors()
    {
        getInnerErrorDisplayer().clearAllErrors();
    }

    @Override
    public boolean isShowingError()
    {
        return getInnerErrorDisplayer().isShowingError();
    }

    @Override
    public void showType(String type)
    {
        // Pretty needless when we are the type/unit...
    }
}
