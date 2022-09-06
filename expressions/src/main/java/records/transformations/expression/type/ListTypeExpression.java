package records.transformations.expression.type;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.recorded.qual.Recorded;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.jellytype.JellyType;
import records.transformations.expression.Expression.SaveDestination;
import styled.StyledString;

import java.util.Objects;

public class ListTypeExpression extends TypeExpression
{
    private final @Recorded TypeExpression innerType;

    public ListTypeExpression(@Recorded TypeExpression innerType)
    {
        this.innerType = innerType;
    }

    @Override
    public StyledString toStyledString()
    {
        return StyledString.squareBracket(innerType.toStyledString());
    }

    @Override
    public String save(SaveDestination saveDestination, TableAndColumnRenames renames)
    {
        return "[" + innerType.save(saveDestination, renames) + "]";
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
    public @Recorded JellyType toJellyType(@Recorded ListTypeExpression this, TypeManager typeManager, JellyRecorder jellyRecorder) throws InternalException, UnJellyableTypeExpression
    {
        return jellyRecorder.record(JellyType.list(innerType.toJellyType(typeManager, jellyRecorder)), this);
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

    @SuppressWarnings("recorded")
    @Override
    public TypeExpression replaceSubExpression(TypeExpression toReplace, TypeExpression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return new ListTypeExpression(innerType.replaceSubExpression(toReplace, replaceWith));
    }

    @Override
    public @Nullable @ExpressionIdentifier String asIdent()
    {
        return null;
    }
}
