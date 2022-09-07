package records.transformations.expression.type;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.recorded.qual.Recorded;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.TableAndColumnRenames;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.jellytype.JellyType;
import records.transformations.expression.Expression.SaveDestination;
import records.transformations.expression.UnitExpression;
import xyz.columnal.styled.StyledString;

import java.util.Objects;

public class UnitLiteralTypeExpression extends TypeExpression
{
    private final @Recorded UnitExpression unitExpression;
    
    public UnitLiteralTypeExpression(@Recorded UnitExpression unitExpression)
    {
        this.unitExpression = unitExpression;
    }

    @Override
    public String save(SaveDestination saveDestination, TableAndColumnRenames renames)
    {
        return "{" + unitExpression.save(saveDestination, true) + "}";
    }

    @Override
    public @Nullable DataType toDataType(TypeManager typeManager)
    {
        return null;
    }

    @Override
    public @Recorded JellyType toJellyType(@Recorded UnitLiteralTypeExpression this, TypeManager typeManager, JellyRecorder jellyRecorder) throws InternalException, UnJellyableTypeExpression
    {
        throw new UnJellyableTypeExpression("Unit not valid in this position", this);
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

    public @Recorded UnitExpression getUnitExpression()
    {
        return unitExpression;
    }

    @Override
    public @Nullable @ExpressionIdentifier String asIdent()
    {
        return null;
    }
}
