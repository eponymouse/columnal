package records.transformations.expression.type;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.gui.expressioneditor.Consecutive.ConsecutiveStartContent;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.SquareBracketedExpression;
import records.gui.expressioneditor.SquareBracketedTypeNode;
import styled.StyledString;

import java.util.Objects;

import static records.transformations.expression.LoadableExpression.SingleLoader.withSemanticParent;

public class ListTypeExpression extends TypeExpression
{
    private final TypeExpression innerType;

    public ListTypeExpression(TypeExpression innerType)
    {
        this.innerType = innerType;
    }

    @Override
    public SingleLoader<TypeExpression, TypeParent, OperandNode<TypeExpression, TypeParent>> loadAsSingle()
    {
        return (p, s) -> new SquareBracketedTypeNode(p, withSemanticParent(innerType.loadAsConsecutive(false), s));
    }

    @Override
    public StyledString toStyledString()
    {
        return StyledString.squareBracket(innerType.toStyledString());
    }

    @Override
    public String save(TableAndColumnRenames renames)
    {
        return "[" + innerType.save(renames) + "]";
    }

    @Override
    public @Nullable DataType toDataType(TypeManager typeManager)
    {
        // Be careful here; null is a valid value inside a list type, but we don't want to pass null!
        DataType inner = innerType.toDataType(typeManager);
        if (inner != null)
            return DataType.array(inner);
        return null;
    }

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    public TypeExpression _test_getContent()
    {
        return innerType;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ListTypeExpression that = (ListTypeExpression) o;
        return Objects.equals(innerType, that.innerType);
    }

    @Override
    public int hashCode()
    {

        return Objects.hash(innerType);
    }
}
