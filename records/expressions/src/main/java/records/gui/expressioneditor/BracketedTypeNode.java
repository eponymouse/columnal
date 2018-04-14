package records.gui.expressioneditor;

import javafx.beans.value.ObservableObjectValue;
import javafx.scene.control.Label;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.TypeParent;

// Round bracketed.
public class BracketedTypeNode extends Consecutive<TypeExpression, TypeParent> implements TypeParent, OperandNode<TypeExpression, TypeParent>
{
    private final ConsecutiveBase<TypeExpression, TypeParent> consecParent;

    public BracketedTypeNode(ConsecutiveBase<TypeExpression, TypeParent> parent, @Nullable ConsecutiveStartContent<TypeExpression, TypeParent> content)
    {
        super(TYPE_OPS, parent, new Label("("), new Label(")"), "", content, ')');
        this.consecParent = parent;
    }
    
    @Override
    protected BracketedStatus getChildrenBracketedStatus()
    {
        return BracketedStatus.DIRECT_ROUND_BRACKETED;
    }
    
    @Override
    public TypeParent getThisAsSemanticParent()
    {
        return this;
    }

    @Override
    protected boolean hasImplicitRoundBrackets()
    {
        return true;
    }

    @Override
    public boolean isFocused()
    {
        return super.childIsFocused();
    }

    @Override
    public boolean isTuple()
    {
        return true;
    }

    @Override
    public TypeExpression save(ErrorDisplayerRecord errorDisplayer, ErrorAndTypeRecorder onError)
    {
        return errorDisplayer.recordType(this, saveUnrecorded(errorDisplayer, onError));
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
