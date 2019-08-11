package records.transformations.expression.type;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.recorded.qual.Recorded;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.jellytype.JellyType;
import records.loadsave.OutputBuilder;
import records.transformations.expression.Expression.SaveDestination;
import styled.StyledString;

import java.util.Objects;

// For a named, fully-formed type (not a tagged type name), not including numeric types
public class TypePrimitiveLiteral extends TypeExpression
{
    private final DataType dataType;

    public TypePrimitiveLiteral(DataType dataType)
    {
        this.dataType = dataType;
    }

    public String toDisplay()
    {
        try
        {
            return dataType.toDisplay(false);
        }
        catch (UserException | InternalException e)
        {
            Log.log(e);
            return "";
        }
    }

    @Override
    public StyledString toStyledString()
    {
        return dataType.toStyledString();
    }

    @Override
    public String save(SaveDestination saveDestination, TableAndColumnRenames renames)
    {
        try
        {
            return dataType.save(new OutputBuilder()).toString();
        }
        catch (InternalException e)
        {
            Log.log(e);
            return new InvalidIdentTypeExpression("").save(saveDestination, renames);
        }
    }

    @Override
    public @Nullable DataType toDataType(TypeManager typeManager)
    {
        return dataType;
    }

    @Override
    public @Recorded JellyType toJellyType(@Recorded TypePrimitiveLiteral this, TypeManager typeManager, JellyRecorder jellyRecorder) throws InternalException
    {
        return jellyRecorder.record(JellyType.fromConcrete(dataType), this);
    }

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    public DataType _test_getType()
    {
        return dataType;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TypePrimitiveLiteral that = (TypePrimitiveLiteral) o;
        return Objects.equals(dataType, that.dataType);
    }

    @Override
    public int hashCode()
    {

        return Objects.hash(dataType);
    }

    @Override
    public TypeExpression replaceSubExpression(TypeExpression toReplace, TypeExpression replaceWith)
    {
        return this == toReplace ? replaceWith : this;
    }

    @SuppressWarnings("identifier")
    @Override
    public @Nullable @ExpressionIdentifier String asIdent()
    {
        return dataType.toString();
    }
}
