package records.transformations.expression.type;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.TypeEntry;
import records.jellytype.JellyType;
import records.transformations.expression.BracketedStatus;
import styled.StyledString;
import utility.Utility;

import java.util.Objects;
import java.util.stream.Stream;

public class ListTypeExpression extends TypeExpression
{
    private final TypeExpression innerType;

    public ListTypeExpression(TypeExpression innerType)
    {
        this.innerType = innerType;
    }

    @Override
    public Stream<SingleLoader<TypeExpression, TypeSaver>> loadAsConsecutive(BracketedStatus bracketedStatus)
    {
        return Utility.concatStreams(
            Stream.of(TypeEntry.load("[")),
            innerType.loadAsConsecutive(BracketedStatus.DIRECT_SQUARE_BRACKETED),
            Stream.of(TypeEntry.load("]"))
        );
    }

    @Override
    public StyledString toStyledString()
    {
        return StyledString.squareBracket(innerType.toStyledString());
    }

    @Override
    public String save(boolean structured, TableAndColumnRenames renames)
    {
        return "[" + innerType.save(structured, renames) + "]";
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
    public JellyType toJellyType(TypeManager typeManager) throws InternalException, UserException
    {
        return JellyType.list(innerType.toJellyType(typeManager));
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

    @Override
    public TypeExpression replaceSubExpression(TypeExpression toReplace, TypeExpression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return new ListTypeExpression(innerType.replaceSubExpression(toReplace, replaceWith));
    }
}
