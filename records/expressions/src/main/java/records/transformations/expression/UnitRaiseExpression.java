package records.transformations.expression;

import annotation.recorded.qual.Recorded;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.UnitManager;
import records.jellytype.JellyUnit;
import styled.StyledString;
import utility.Either;
import utility.Pair;

import java.util.List;

public class UnitRaiseExpression extends UnitExpression
{
    private final @Recorded UnitExpression unit;
    private final int power;

    public UnitRaiseExpression(@Recorded UnitExpression unit, int power)
    {
        this.unit = unit;
        this.power = power;
    }

    @Override
    public Either<Pair<@Nullable StyledString, List<UnitExpression>>, JellyUnit> asUnit(UnitManager unitManager)
    {
        Either<Pair<@Nullable StyledString, List<UnitExpression>>, JellyUnit> lhs = unit.asUnit(unitManager);

        return lhs.map(u -> u.raiseBy(power));
    }

    @Override
    public String save(boolean structured, boolean topLevel)
    {
        return unit.save(structured, false) + "^" + power;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UnitRaiseExpression that = (UnitRaiseExpression) o;

        if (power != that.power) return false;
        return unit.equals(that.unit);
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
        int result = unit.hashCode();
        result = 31 * result + power;
        return result;
    }

    @SuppressWarnings("recorded")
    @Override
    public UnitExpression replaceSubExpression(UnitExpression toReplace, UnitExpression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return new UnitRaiseExpression(unit.replaceSubExpression(toReplace, replaceWith), power);
    }
}
