package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import javafx.beans.value.ObservableObjectValue;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.Expression;
import records.transformations.expression.LoadableExpression;
import styled.StyledShowable;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.Comparator;
import java.util.List;

/**
 * Created by neil on 20/12/2016.
 */
public class BracketedExpression extends Consecutive<Expression, ExpressionNodeParent> implements OperandNode<Expression, ExpressionNodeParent>, ExpressionNodeParent
{
    private final ConsecutiveBase<Expression, ExpressionNodeParent> consecParent;

    protected BracketedExpression(ConsecutiveBase<Expression, ExpressionNodeParent> parent, Node prefixNode, Node suffixNode, @Nullable ConsecutiveStartContent<Expression, ExpressionNodeParent> content, char endCharacter)
    {
        super(EXPRESSION_OPS, parent, prefixNode, suffixNode, "bracket", content, endCharacter);
        this.consecParent = parent;
    }

    public BracketedExpression(ConsecutiveBase<Expression, ExpressionNodeParent> parent, @Nullable ConsecutiveStartContent<Expression, ExpressionNodeParent> content, char endCharacter)
    {
        this(parent, new Label("("), new Label(")"), content, endCharacter);
    }

    @Override
    public ExpressionNodeParent getThisAsSemanticParent()
    {
        return this;
    }

    @Override
    public @Nullable ObservableObjectValue<@Nullable String> getStyleWhenInner()
    {
        return null;
    }

    @Override
    public boolean isFocused()
    {
        return childIsFocused();
    }

    @Override
    public ConsecutiveBase<Expression, ExpressionNodeParent> getParent()
    {
        return consecParent;
    }

    @Override
    public void setSelected(boolean selected)
    {
        // Not sure how to style this, yet
    }

    @Override
    public void setHoverDropLeft(boolean on)
    {
        FXUtility.setPseudoclass(nodes().get(0), "exp-hover-drop-left", on);
    }

    @Override
    protected BracketedStatus getChildrenBracketedStatus()
    {
        return BracketedStatus.DIRECT_ROUND_BRACKETED;
    }

    @Override
    public void visitLocatable(LocatableVisitor visitor)
    {
        super.visitLocatable(visitor);
        visitor.register(this, operations.getOperandClass());
    }

    @Override
    public List<Pair<DataType, List<String>>> getSuggestedContext(EEDisplayNode child) throws InternalException, UserException
    {
        // Bracketed context identical to parent context:
        return consecParent.getThisAsSemanticParent().getSuggestedContext(this);
    }

    @Override
    public boolean canDeclareVariable(@UnknownInitialization EEDisplayNode child)
    {
        // So technically you can only declare a var if this is a tuple or array.  But
        // we don't know that until they've finished, so we just allow it if the parent allows it,
        // and generate an error later if it's not allowed:
        return consecParent.getThisAsSemanticParent().canDeclareVariable(this);
    }

    @Override
    protected boolean hasImplicitRoundBrackets()
    {
        return true;
    }

    @Override
    public @Recorded Expression save(ErrorDisplayerRecord errorDisplayer, ErrorAndTypeRecorder onError)
    {
        return errorDisplayer.record(this, saveUnrecorded(errorDisplayer, onError));
    }
}
