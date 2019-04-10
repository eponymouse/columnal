package records.transformations.expression.type;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.jellytype.JellyType;
import styled.StyledString;
import utility.Utility;

import java.util.Objects;
import java.util.stream.Collectors;

public class TupleTypeExpression extends TypeExpression
{
    private final ImmutableList<@Recorded TypeExpression> members;

    public TupleTypeExpression(ImmutableList<@Recorded TypeExpression> members)
    {
        this.members = members;
    }

    @Override
    public StyledString toStyledString()
    {
        return StyledString.roundBracket(members.stream().map(s -> s.toStyledString()).collect(StyledString.joining(", ")));
    }

    @Override
    public String save(boolean structured, TableAndColumnRenames renames)
    {
        return "(" + members.stream().map(m -> m.save(structured, renames)).collect(Collectors.joining(", ")) + ")";
    }

    @Override
    public @Nullable DataType toDataType(TypeManager typeManager)
    {
        ImmutableList.Builder<DataType> memberTypes = ImmutableList.builderWithExpectedSize(members.size());
        for (TypeExpression member : members)
        {
            DataType memberType = member.toDataType(typeManager);
            if (memberType == null)
                return null;
            memberTypes.add(memberType);
        }
        return DataType.tuple(memberTypes.build());
    }

    @Override
    public JellyType toJellyType(TypeManager typeManager) throws InternalException, UserException
    {
        return JellyType.tuple(Utility.mapListExI(members, m -> m.toJellyType(typeManager)));
    }

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    public ImmutableList<@Recorded TypeExpression> _test_getItems()
    {
        return members;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TupleTypeExpression that = (TupleTypeExpression) o;
        return Objects.equals(members, that.members);
    }

    @Override
    public int hashCode()
    {

        return Objects.hash(members);
    }

    @Override
    @SuppressWarnings("recorded") // Because the replaced version is immediately loaded again
    public TypeExpression replaceSubExpression(TypeExpression toReplace, TypeExpression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return new TupleTypeExpression(Utility.mapListI(members, t -> t.replaceSubExpression(toReplace, replaceWith)));
    }
}
