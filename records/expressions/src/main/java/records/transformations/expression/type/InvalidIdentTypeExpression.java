package records.transformations.expression.type;

import annotation.identifier.qual.ExpressionIdentifier;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.FormatLexer;
import records.jellytype.JellyType;
import records.loadsave.OutputBuilder;
import styled.StyledString;
import utility.IdentifierUtility;

import java.util.Objects;

public class InvalidIdentTypeExpression extends TypeExpression
{
    private final String value;
    
    public InvalidIdentTypeExpression(String value)
    {
        this.value = value;
    }

    @Override
    public StyledString toStyledString()
    {
        return StyledString.s("Invalid: \"" + value + "\"");
    }

    @Override
    public String save(boolean structured, TableAndColumnRenames renames)
    {
        if (structured)
            return OutputBuilder.token(FormatLexer.VOCABULARY, FormatLexer.INCOMPLETE) + " " + OutputBuilder.quoted(value);
        else
            return value;
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
        InvalidIdentTypeExpression that = (InvalidIdentTypeExpression) o;
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

    // IdentExpression if possible, otherwise InvalidIdentExpression
    public static TypeExpression identOrUnfinished(String src)
    {
        @ExpressionIdentifier String valid = IdentifierUtility.asExpressionIdentifier(src);
        if (valid != null)
            return new IdentTypeExpression(valid);
        else
            return new InvalidIdentTypeExpression(src);
    }
}
