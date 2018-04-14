package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import javafx.beans.value.ObservableObjectValue;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import log.Log;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.ErrorAndTypeRecorder.QuickFix;
import records.transformations.expression.Expression;
import records.transformations.expression.FixedTypeExpression;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.TypeParent;
import records.transformations.expression.type.UnfinishedTypeExpression;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.TranslationUtility;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static records.transformations.expression.LoadableExpression.SingleLoader.withSemanticParent;

public class FixedTypeNode extends DeepNodeTree implements EEDisplayNodeParent, OperandNode<Expression, ExpressionNodeParent>, ErrorDisplayer<Expression, ExpressionNodeParent>, ExpressionNodeParent, TypeParent
{
    private final ConsecutiveBase<Expression, ExpressionNodeParent> consecParent;
    private final Consecutive<TypeExpression, TypeParent> type;
    private final TextField middle;
    private final Consecutive<Expression, ExpressionNodeParent> expression;

    @SuppressWarnings("initialization")
    public FixedTypeNode(ConsecutiveBase<Expression, ExpressionNodeParent> parent, ExpressionNodeParent semanticParent, @Nullable TypeExpression startingType, @Nullable Expression startingInner)
    {
        this.consecParent = parent;
        this.expression = new Consecutive<Expression, ExpressionNodeParent>(ConsecutiveBase.EXPRESSION_OPS, this, new Label("("), new Label(")"), "", startingInner == null ? null : withSemanticParent(startingInner.loadAsConsecutive(true), this), ')')
        {
            @Override
            public boolean isFocused()
            {
                return childIsFocused();
            }

            @Override
            public ExpressionNodeParent getThisAsSemanticParent()
            {
                return FixedTypeNode.this;
            }

            @Override
            protected boolean hasImplicitRoundBrackets()
            {
                return true;
            }
        };
        this.type = new Consecutive<TypeExpression, TypeParent>(ConsecutiveBase.TYPE_OPS, this, new Label("{"), new Label("}"), "", startingType == null ? null : withSemanticParent(startingType.loadAsConsecutive(false), this), '}')
        {
            @Override
            public TypeParent getThisAsSemanticParent()
            {
                return FixedTypeNode.this;
            }

            @Override
            protected boolean hasImplicitRoundBrackets()
            {
                return false;
            }

            @Override
            public boolean isFocused()
            {
                return childIsFocused();
            }
        };
        this.middle = new TextField();
    }

    @Override
    public List<Pair<DataType, List<String>>> getSuggestedContext(EEDisplayNode child) throws InternalException, UserException
    {
        return Collections.emptyList();
    }

    @Override
    public List<Pair<String, @Nullable DataType>> getAvailableVariables(@UnknownInitialization EEDisplayNode child)
    {
        return consecParent.getThisAsSemanticParent().getAvailableVariables(this);
    }

    @Override
    public boolean canDeclareVariable(EEDisplayNode chid)
    {
        return false;
    }

    @Override
    protected Stream<EEDisplayNode> calculateChildren()
    {
        // TODO should we include middle?
        return Stream.concat(type.calculateChildren(), expression.calculateChildren());
    }

    @Override
    protected Stream<Node> calculateNodes()
    {
        return Stream.concat(Stream.concat(type.nodes().stream(), Stream.of(middle)), expression.nodes().stream());
    }

    @Override
    protected void updateDisplay()
    {
        
    }

    @Override
    public void focus(Focus side)
    {
        switch (side)
        {
            case LEFT:
                type.focus(Focus.LEFT);
                break;
            case RIGHT:
                expression.focus(Focus.RIGHT);
                break;
        }
    }

    @Override
    public boolean isFocused()
    {
        return type.isFocused() || middle.isFocused() || expression.isFocused();
    }

    @Override
    public void focusWhenShown()
    {
        type.focusWhenShown();
    }

    @Override
    public boolean isOrContains(EEDisplayNode child)
    {
        return type.isOrContains(child) || child == middle || expression.isOrContains(child);
    }

    @Override
    public void addErrorAndFixes(StyledString error, List<QuickFix<Expression, ExpressionNodeParent>> quickFixes)
    {
        expression.addErrorAndFixes(error, quickFixes);
    }

    @Override
    public void showType(String type)
    {
        // Pretty needless when we are fixing the type...
    }

    @Override
    public boolean isShowingError()
    {
        return expression.isShowingError();
    }

    @Override
    public void cleanup()
    {
        type.cleanup();
        expression.cleanup();
    }

    @Override
    public void clearAllErrors()
    {
        type.clearAllErrors();
        expression.clearAllErrors();
    }

    @Override
    public void prompt(String prompt)
    {
        expression.prompt(prompt);
    }

    @Override
    public @Recorded Expression save(ErrorDisplayerRecord errorDisplayer, ErrorAndTypeRecorder onError)
    {
        return errorDisplayer.record(this, new FixedTypeExpression(
            errorDisplayer.recordType(type, type.saveUnrecorded(errorDisplayer, onError)),
            errorDisplayer.record(expression, expression.saveUnrecorded(errorDisplayer, onError))
        ));
    }

    @Override
    public @Nullable ObservableObjectValue<@Nullable String> getStyleWhenInner()
    {
        return null;
    }

    @Override
    public ConsecutiveBase<Expression, ExpressionNodeParent> getParent()
    {
        return consecParent;
    }

    @Override
    public void setSelected(boolean selected)
    {
        // TODO
    }

    @Override
    public void setHoverDropLeft(boolean on)
    {

    }

    @Override
    public void focusChanged()
    {
        type.focusChanged();
        expression.focusChanged();
    }

    @Override
    public @OnThread(Tag.FXPlatform) Stream<Pair<String, Boolean>> _test_getHeaders()
    {
        return Stream.concat(type._test_getHeaders(), expression._test_getHeaders());
    }

    @Override
    public void changed(@UnknownInitialization(EEDisplayNode.class) EEDisplayNode child)
    {
        
    }

    @Override
    public void focusRightOf(@UnknownInitialization(EEDisplayNode.class) EEDisplayNode child, Focus side)
    {
        if (child == type)
            middle.requestFocus();
        else if (child == middle)
            expression.focus(Focus.LEFT);
        else
            consecParent.focusRightOf(this, side);
    }

    @Override
    public void focusLeftOf(@UnknownInitialization(EEDisplayNode.class) EEDisplayNode child)
    {
        if (child == type)
            consecParent.focusLeftOf(this);
        else if (child == middle)
            type.focus(Focus.RIGHT);
        else
            expression.focus(Focus.RIGHT);
    }

    @Override
    public Stream<String> getParentStyles()
    {
        return Stream.empty();
    }

    @Override
    public TopLevelEditor<?, ?> getEditor()
    {
        return consecParent.getEditor();
    }

    @Override
    public void visitLocatable(LocatableVisitor visitor)
    {
        type.visitLocatable(visitor);
        expression.visitLocatable(visitor);
    }

    @Override
    public boolean isTuple()
    {
        return false;
    }
}
