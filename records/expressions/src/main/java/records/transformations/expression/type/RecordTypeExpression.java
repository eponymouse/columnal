package records.transformations.expression.type;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.jellytype.JellyType;
import records.jellytype.JellyTypeRecord;
import records.jellytype.JellyTypeRecord.Field;
import styled.StyledString;
import utility.Pair;
import utility.Utility;

import java.util.Collection;
import java.util.HashMap;
import java.util.Objects;
import java.util.stream.Collectors;

public class RecordTypeExpression extends TypeExpression
{
    private final ImmutableList<Pair<@ExpressionIdentifier String, @Recorded TypeExpression>> members;

    public RecordTypeExpression(ImmutableList<Pair<@ExpressionIdentifier String, @Recorded TypeExpression>> members)
    {
        this.members = members;
    }
    
    @Override
    public @Nullable DataType toDataType(TypeManager typeManager)
    {
        HashMap<@ExpressionIdentifier String, DataType> types = new HashMap<>();

        for (Pair<@ExpressionIdentifier String, TypeExpression> member : members)
        {
            DataType memberType = member.getSecond().toDataType(typeManager);
            if (memberType == null || types.put(member.getFirst(), memberType) != null)
                return null; // Duplicate field
        }
        
        return DataType.record(types);
    }

    @Override
    public @Recorded JellyType toJellyType(@Recorded RecordTypeExpression this, TypeManager typeManager, JellyRecorder jellyRecorder) throws InternalException, UnJellyableTypeExpression
    {
        HashMap<@ExpressionIdentifier String, Field> types = new HashMap<>();

        for (Pair<@ExpressionIdentifier String, @Recorded TypeExpression> member : members)
        {
            @Recorded JellyType memberType = member.getSecond().toJellyType(typeManager, jellyRecorder);
            if (types.put(member.getFirst(), new Field(memberType, true)) != null)
                throw new UnJellyableTypeExpression("Duplicate field in record: \"" + member.getFirst() + "\"", this);
        }
        
        return jellyRecorder.record(new JellyTypeRecord(ImmutableMap.copyOf(types), true), this);
    }

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RecordTypeExpression that = (RecordTypeExpression) o;
        return members.equals(that.members);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(members);
    }

    @SuppressWarnings("recorded")
    @Override
    public TypeExpression replaceSubExpression(TypeExpression toReplace, TypeExpression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return new RecordTypeExpression(Utility.<Pair<@ExpressionIdentifier String, TypeExpression>, Pair<@ExpressionIdentifier String, TypeExpression>>mapListI(members, (Pair<@ExpressionIdentifier String, TypeExpression> p) -> p.mapSecond(e -> e.replaceSubExpression(toReplace, replaceWith))));
    }

    @Override
    public StyledString toStyledString()
    {
        return StyledString.roundBracket(members.stream().map(s -> StyledString.concat(StyledString.s(s.getFirst() + ": "), s.getSecond().toStyledString())).collect(StyledString.joining(", ")));
    }

    @Override
    public String save(boolean structured, TableAndColumnRenames renames)
    {
        return "(" + members.stream().map(m -> m.getFirst() + ": " + m.getSecond().save(structured, renames)).collect(Collectors.joining(", ")) + ")";
    }

    public ImmutableList<Pair<@ExpressionIdentifier String, @Recorded TypeExpression>> _test_getItems()
    {
        return members;
    }

    @Override
    public @Nullable @ExpressionIdentifier String asIdent()
    {
        return null;
    }
}
