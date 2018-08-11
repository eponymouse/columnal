package records.gui.expressioneditor;


import javafx.beans.value.ObservableObjectValue;
import javafx.scene.Node;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.expressioneditor.ConsecutiveBase.BracketBalanceType;
import records.transformations.expression.Expression;
import records.transformations.expression.Replaceable;
import styled.StyledShowable;

import java.util.stream.Stream;

// Super-class of TypeLiteralNode/UnitLiteralExpressionNode with all the shared functionality
public abstract class TreeLiteralNode<EXPRESSION extends StyledShowable & Replaceable<EXPRESSION>, SEMANTIC_PARENT> extends DeepNodeTree implements EEDisplayNodeParent, ConsecutiveChild<EXPRESSION, SEMANTIC_PARENT>, ErrorDisplayer<EXPRESSION, SEMANTIC_PARENT>
{
    protected final ConsecutiveBase<EXPRESSION, SEMANTIC_PARENT> consecParent;

    protected TreeLiteralNode(ConsecutiveBase<EXPRESSION, SEMANTIC_PARENT> consecParent)
    {
        this.consecParent = consecParent;
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
    public void focusRightOf(@UnknownInitialization(EEDisplayNode.class) EEDisplayNode child, Focus side, boolean becauseOfTab)
    {
        consecParent.focusRightOf(this, side, becauseOfTab);
    }

    @Override
    public void focusLeftOf(@UnknownInitialization(EEDisplayNode.class) EEDisplayNode child)
    {
        consecParent.focusLeftOf(this);
    }

    @Override
    public ConsecutiveBase<EXPRESSION, SEMANTIC_PARENT> getParent()
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
    public boolean isShowingError()
    {
        return consecParent.isShowingError();
    }

    @Override
    public void showType(String type)
    {
        // Pretty needless when we are the type/unit...
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
    public abstract void removeNestedBlanks();

    @Override
    public void setPrompt(@Localized String prompt)
    {
        // We are rich node so we don't set prompts
    }
}
