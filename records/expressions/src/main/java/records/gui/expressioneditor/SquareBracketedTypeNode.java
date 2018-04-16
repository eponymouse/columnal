package records.gui.expressioneditor;

import annotation.recorded.qual.Recorded;
import annotation.recorded.qual.UnknownIfRecorded;
import com.google.common.collect.ImmutableList;
import javafx.beans.value.ObservableObjectValue;
import javafx.scene.control.Label;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.transformations.expression.ArrayExpression;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.Expression;
import records.transformations.expression.type.ListTypeExpression;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.TypeParent;
import records.types.TypeExp;
import utility.Pair;
import utility.Utility;

import java.util.List;

public class SquareBracketedTypeNode extends Consecutive<TypeExpression, TypeParent> implements TypeParent, OperandNode<TypeExpression, TypeParent>
{
    private final ConsecutiveBase<TypeExpression, TypeParent> consecParent;

    public SquareBracketedTypeNode(ConsecutiveBase<TypeExpression, TypeParent> parent, @Nullable ConsecutiveStartContent<TypeExpression, TypeParent> content)
    {
        super(TYPE_OPS, parent, new Label("["), new Label("]"), "", content, ']');
        this.consecParent = parent;
    }

    @Override
    protected BracketedStatus getChildrenBracketedStatus()
    {
        return BracketedStatus.DIRECT_SQUARE_BRACKETED;
    }
    
    @Override
    public TypeParent getThisAsSemanticParent()
    {
        return this;
    }

    @Override
    protected boolean hasImplicitRoundBrackets()
    {
        return false;
    }

    @Override
    public boolean isFocused()
    {
        return super.childIsFocused();
    }

    @Override
    public boolean isTuple()
    {
        return false;
    }

    @Override
    public @Recorded TypeExpression save(ErrorDisplayerRecord errorDisplayer, ErrorAndTypeRecorder onError)
    {
        return errorDisplayer.recordType(this, new ListTypeExpression(saveUnrecorded(errorDisplayer, onError)));
    }
    
    @Override
    public @Nullable ObservableObjectValue<@Nullable String> getStyleWhenInner()
    {
        return null;
    }

    @Override
    public ConsecutiveBase<TypeExpression, TypeParent> getParent()
    {
        return consecParent;
    }

    @Override
    public void setHoverDropLeft(boolean on)
    {

    }
}
