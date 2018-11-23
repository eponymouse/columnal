package records.transformations.expression.type;

import annotation.identifier.qual.ExpressionIdentifier;
import com.google.common.collect.ImmutableList;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.data.datatype.TaggedTypeDefinition;
import records.data.datatype.TypeId;
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

public class IdentTypeExpression extends TypeExpression
{
    private final @ExpressionIdentifier String value;

    public IdentTypeExpression(@ExpressionIdentifier String value)
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
        return StyledString.s(value);
    }

    @Override
    public String save(boolean structured, TableAndColumnRenames renames)
    {
        return value;
    }

    @Override
    public @Nullable DataType toDataType(TypeManager typeManager)
    {
        // TODO pass in type variable subsitutions to this method
        try
        {
            TaggedTypeDefinition typeDefinition = typeManager.lookupDefinition(new TypeId(value));
            if (typeDefinition == null)
                return null;
            else
                return typeDefinition.instantiate(ImmutableList.of(), typeManager);
        }
        catch (InternalException | UserException e)
        {
            if (e instanceof InternalException)
                Log.log(e);
            return null;
        }
    }

    @Override
    public JellyType toJellyType(TypeManager typeManager) throws InternalException, UserException
    {
        return JellyType.typeVariable(value);
    }

    @Override
    public boolean isEmpty()
    {
        return value.isEmpty();
    }

    public @ExpressionIdentifier String getIdent()
    {
        return value;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IdentTypeExpression that = (IdentTypeExpression) o;
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
