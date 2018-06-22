package records.transformations.expression.type;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.transformations.expression.BracketedStatus;
import records.gui.expressioneditor.TypeEntry;
import styled.StyledString;
import utility.StreamTreeBuilder;
import utility.Utility;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TupleTypeExpression extends TypeExpression
{
    private final ImmutableList<TypeExpression> members;

    public TupleTypeExpression(ImmutableList<TypeExpression> members)
    {
        this.members = members;
    }

    @Override
    public Stream<SingleLoader<TypeExpression, TypeSaver>> loadAsConsecutive(BracketedStatus bracketedStatus)
    {
        StreamTreeBuilder<SingleLoader<TypeExpression, TypeSaver>> items = new StreamTreeBuilder<>();
        for (int i = 0; i < members.size(); i++)
        {
            items.addAll(members.get(i).loadAsConsecutive(members.size() == 1 ? BracketedStatus.DIRECT_ROUND_BRACKETED : BracketedStatus.MISC));
            // Now we must add the comma:
            if (i < members.size() - 1)
                items.add(p -> new TypeEntry(p, ","));
        }

        return items.stream();
    }

    @Override
    public StyledString toStyledString()
    {
        return StyledString.roundBracket(members.stream().map(s -> s.toStyledString()).collect(StyledString.joining(", ")));
    }

    @Override
    public String save(TableAndColumnRenames renames)
    {
        return "(" + members.stream().map(m -> m.save(renames)).collect(Collectors.joining(", ")) + ")";
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
    public boolean isEmpty()
    {
        return false;
    }

    public ImmutableList<TypeExpression> _test_getItems()
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
    public TypeExpression replaceSubExpression(TypeExpression toReplace, TypeExpression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return new TupleTypeExpression(Utility.mapListI(members, t -> t.replaceSubExpression(toReplace, replaceWith)));
    }
}
