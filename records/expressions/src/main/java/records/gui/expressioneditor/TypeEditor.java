package records.gui.expressioneditor;

import com.google.common.collect.ImmutableSet;
import records.data.TableManager;
import records.transformations.expression.type.TypeExpression;
import records.transformations.expression.type.TypeParent;
import utility.UnitType;

import java.util.stream.Stream;

public class TypeEditor extends TopLevelEditor<TypeExpression, TypeParent> implements TypeParent
{
    public TypeEditor(TableManager tableManager)
    {
        super(new TypeExpressionOps(), tableManager);
    }

    @Override
    public TypeParent getThisAsSemanticParent()
    {
        return this;
    }

    @Override
    protected void selfChanged()
    {

    }

    @Override
    protected boolean hasImplicitRoundBrackets()
    {
        return false;
    }

    @Override
    protected void parentFocusRightOfThis(Focus side)
    {

    }

    @Override
    protected void parentFocusLeftOfThis()
    {

    }

    @Override
    protected boolean isMatchNode()
    {
        return false;
    }

    @Override
    public ImmutableSet<Character> terminatedByChars()
    {
        return ImmutableSet.of();
    }

    @Override
    public Stream<String> getParentStyles()
    {
        return Stream.empty();
    }

    @Override
    public boolean isTuple()
    {
        return false;
    }
}
