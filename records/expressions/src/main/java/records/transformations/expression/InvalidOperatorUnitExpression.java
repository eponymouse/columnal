package records.transformations.expression;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.UnitManager;
import records.jellytype.JellyUnit;
import styled.StyledString;
import utility.Either;
import utility.Pair;
import utility.Utility;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class InvalidOperatorUnitExpression extends UnitExpression
{
    private final ImmutableList<@Recorded UnitExpression> items;

    public InvalidOperatorUnitExpression(ImmutableList<@Recorded UnitExpression> items)
    {
        this.items = items;
    }

    @Override
    public Either<Pair<@Nullable StyledString, List<UnitExpression>>, JellyUnit> asUnit(UnitManager unitManager)
    {
        return Either.left(new Pair<>(null, Collections.emptyList()));
    }

    @Override
    public String save(boolean structured, boolean topLevel)
    {
        if (structured)
            return "@INVALIDOPS (" + 
                items.stream().map(item -> item.save(structured, false)).collect(Collectors.joining(", "))
                + ")";
        else
            return items.stream().map(item -> item.save(structured, false)).collect(Collectors.joining(""));
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        InvalidOperatorUnitExpression that = (InvalidOperatorUnitExpression) o;

        return items.equals(that.items);
    }

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    @Override
    public boolean isScalar()
    {
        return false;
    }

    @Override
    public int hashCode()
    {
        return items.hashCode();
    }

    @Override
    @SuppressWarnings("recorded") // Because the replaced version is immediately loaded again
    public UnitExpression replaceSubExpression(UnitExpression toReplace, UnitExpression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return new InvalidOperatorUnitExpression(Utility.mapListI(items, e -> e.replaceSubExpression(toReplace, replaceWith)));
    }
}
