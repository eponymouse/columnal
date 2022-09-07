package records.transformations.expression;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.jellytype.JellyUnit;
import records.transformations.expression.Expression.SaveDestination;
import xyz.columnal.styled.StyledString;

public class UnitRaiseExpression extends UnitExpression
{
    private final @Recorded UnitExpression unit;
    private final @Recorded UnitExpression power;

    public UnitRaiseExpression(@Recorded UnitExpression unit, @Recorded UnitExpression power)
    {
        this.unit = unit;
        this.power = power;
    }

    @Override
    public JellyUnit asUnit(@Recorded UnitRaiseExpression this, UnitManager unitManager) throws UnitLookupException
    {
        if (!(power instanceof UnitExpressionIntLiteral))
        {
            throw new UnitLookupException(StyledString.s("Units can only be raised to integer powers"), this, ImmutableList.of());
        }
        
        JellyUnit lhs = unit.asUnit(unitManager);

        return lhs.raiseBy(((UnitExpressionIntLiteral)power).getNumber());
    }

    @Override
    public String save(SaveDestination saveDestination, boolean topLevel)
    {
        return unit.save(saveDestination, false) + "^" + power.save(saveDestination, false);
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UnitRaiseExpression that = (UnitRaiseExpression) o;

        if (!power.equals(that.power)) return false;
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
        result = 31 * result + power.hashCode();
        return result;
    }

    @SuppressWarnings("recorded")
    @Override
    public UnitExpression replaceSubExpression(UnitExpression toReplace, UnitExpression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return new UnitRaiseExpression(unit.replaceSubExpression(toReplace, replaceWith), power.replaceSubExpression(toReplace, replaceWith));
    }
}
