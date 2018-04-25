package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import javafx.beans.value.ObservableObjectValue;
import javafx.scene.Node;
import javafx.scene.control.Label;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.ErrorAndTypeRecorder.QuickFix;
import records.transformations.expression.Expression;
import records.transformations.expression.TypeLiteralExpression;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.TypeParent;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.util.List;
import java.util.stream.Stream;

import static records.transformations.expression.LoadableExpression.SingleLoader.withSemanticParent;

public class TypeLiteralNode extends OtherLiteralNode implements TypeParent
{
    private final Consecutive<TypeExpression, TypeParent> type;
    
    @SuppressWarnings("initialization")
    public TypeLiteralNode(ConsecutiveBase<Expression, ExpressionNodeParent> parent, ExpressionNodeParent semanticParent, @Nullable TypeExpression startingType)
    {
        super(parent);
        this.type = new Consecutive<TypeExpression, TypeParent>(ConsecutiveBase.TYPE_OPS, this, new Label("`"), new Label("`"), "", startingType == null ? null : withSemanticParent(startingType.loadAsConsecutive(false), this), '}')
        {
            @Override
            public TypeParent getThisAsSemanticParent()
            {
                return TypeLiteralNode.this;
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
        updateNodes();
        updateListeners();
    }


    @Override
    protected EEDisplayNode getInnerDisplayNode()
    {
        return type;
    }

    @Override
    protected ErrorDisplayer<?, ?> getInnerErrorDisplayer()
    {
        return type;
    }

    @Override
    public void addErrorAndFixes(StyledString error, List<QuickFix<Expression, ExpressionNodeParent>> quickFixes)
    {
    }

    @Override
    public void prompt(String prompt)
    {
        type.prompt(prompt);
    }

    @Override
    public @Recorded Expression save(ErrorDisplayerRecord errorDisplayer, ErrorAndTypeRecorder onError)
    {
        return errorDisplayer.record(this, new TypeLiteralExpression(
            errorDisplayer.recordType(type, type.saveUnrecorded(errorDisplayer, onError))
        ));
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
    }

    @Override
    public @OnThread(Tag.FXPlatform) Stream<Pair<String, Boolean>> _test_getHeaders()
    {
        return type._test_getHeaders();
    }

    @Override
    public void visitLocatable(LocatableVisitor visitor)
    {
        type.visitLocatable(visitor);
    }

    @Override
    public boolean isRoundBracketed()
    {
        return false;
    }
}
