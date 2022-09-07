package xyz.columnal.transformations.expression.type;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.TableAndColumnRenames;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.grammar.FormatLexer;
import xyz.columnal.jellytype.JellyType;
import xyz.columnal.loadsave.OutputBuilder;
import xyz.columnal.transformations.expression.Expression.SaveDestination;
import xyz.columnal.styled.StyledString;
import xyz.columnal.utility.Utility;

import java.util.Objects;
import java.util.stream.Collectors;

public class InvalidOpTypeExpression extends TypeExpression
{
    private final ImmutableList<@Recorded TypeExpression> items;

    public InvalidOpTypeExpression(ImmutableList<@Recorded TypeExpression> items)
    {
        this.items = items;
    }

    @Override
    public String save(SaveDestination saveDestination, TableAndColumnRenames renames)
    {
        if (saveDestination.needKeywords())
            return OutputBuilder.token(FormatLexer.VOCABULARY, FormatLexer.INVALIDOPS) + " (" +
                items.stream().map(item -> item.save(saveDestination, renames)).collect(Collectors.joining(", ")) 
                + ")";
        else
            return items.stream().map(item -> item.save(saveDestination, renames)).collect(Collectors.joining(""));
    }

    @Override
    public @Nullable DataType toDataType(TypeManager typeManager)
    {
        return null;
    }

    @Override
    public @Recorded JellyType toJellyType(@Recorded InvalidOpTypeExpression this, TypeManager typeManager, JellyRecorder jellyRecorder) throws InternalException, UnJellyableTypeExpression
    {
        throw new UnJellyableTypeExpression("Invalid type expression: " + this, this);
    }

    @Override
    public boolean isEmpty()
    {
        return items.isEmpty();
    }

    @Override
    public StyledString toStyledString()
    {
        return StyledString.s("Invalid"); // TODO
    }

    public ImmutableList<@Recorded TypeExpression> _test_getItems()
    {
        return items;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InvalidOpTypeExpression that = (InvalidOpTypeExpression) o;
        return Objects.equals(items, that.items);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(items);
    }

    @Override
    @SuppressWarnings("recorded") // Because the replaced version is immediately loaded again
    public TypeExpression replaceSubExpression(TypeExpression toReplace, TypeExpression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return new InvalidOpTypeExpression(Utility.mapListI(items, e -> e.replaceSubExpression(toReplace, replaceWith)));
    }

    @Override
    public @Nullable @ExpressionIdentifier String asIdent()
    {
        return null;
    }
}
