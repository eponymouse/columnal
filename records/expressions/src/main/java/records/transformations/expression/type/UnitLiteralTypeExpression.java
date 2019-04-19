package records.transformations.expression.type;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.jellytype.JellyType;
import records.transformations.expression.UnitExpression;
import styled.StyledString;

import java.util.Objects;

public class UnitLiteralTypeExpression extends TypeExpression
{
    private final UnitExpression unitExpression;
    
    public UnitLiteralTypeExpression(UnitExpression unitExpression)
    {
        this.unitExpression = unitExpression;
    }

    @Override
    public String save(boolean structured, TableAndColumnRenames renames)
    {
        return "{" + unitExpression.save(structured, true) + "}";
    }

    @Override
    public @Nullable DataType toDataType(TypeManager typeManager)
    {
        return null;
    }

    @Override
    public JellyType toJellyType(TypeManager typeManager) throws InternalException, UnJellyableTypeExpression
    {
        throw new UnJellyableTypeExpression("Unit not valid in this position");
    }

    @Override
    public boolean isEmpty()
    {
        return unitExpression.isEmpty();
    }

    @Override
    public TypeExpression replaceSubExpression(TypeExpression toReplace, TypeExpression replaceWith)
    {
        return this == toReplace ? replaceWith : this;
    }

    @Override
    public StyledString toStyledString()
    {
        return StyledString.concat(StyledString.s("{"), unitExpression.toStyledString(), StyledString.s("}"));
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UnitLiteralTypeExpression that = (UnitLiteralTypeExpression) o;
        return Objects.equals(unitExpression, that.unitExpression);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(unitExpression);
    }

    public UnitExpression getUnitExpression()
    {
        return unitExpression;
    }
}
