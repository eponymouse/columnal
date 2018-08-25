package records.transformations.expression.type;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.TypeEntry;
import records.jellytype.JellyType;
import records.loadsave.OutputBuilder;
import records.transformations.expression.BracketedStatus;
import styled.StyledString;

import java.util.Objects;
import java.util.stream.Stream;

public class UnfinishedTypeExpression extends TypeExpression
{
    private final String value;

    public UnfinishedTypeExpression(String value)
    {
        this.value = value;
    }

    @Override
    public Stream<SingleLoader<TypeExpression, TypeSaver>> loadAsConsecutive(BracketedStatus bracketedStatus)
    {
        return Stream.of(p -> new TypeEntry(p, value));
    }

    @Override
    public StyledString toStyledString()
    {
        return StyledString.s("Unfinished: \"" + value + "\"");
    }

    @Override
    public String save(TableAndColumnRenames renames)
    {
        return "@INCOMPLETE " + OutputBuilder.quoted(value);
    }

    @Override
    public @Nullable DataType toDataType(TypeManager typeManager)
    {
        return null;
    }

    @Override
    public JellyType toJellyType(TypeManager typeManager) throws InternalException, UserException
    {
        throw new UserException("Invalid type expression: \"" + value + "\"");
    }

    @Override
    public boolean isEmpty()
    {
        return value.isEmpty();
    }

    public String _test_getContent()
    {
        return value;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UnfinishedTypeExpression that = (UnfinishedTypeExpression) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(value);
    }

    @Override
    public TypeExpression replaceSubExpression(TypeExpression toReplace, TypeExpression replaceWith)
    {
        return this == toReplace ? replaceWith : this;
    }
}
