package xyz.columnal.transformations.expression;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.jellytype.JellyUnit;
import xyz.columnal.transformations.expression.Expression.SaveDestination;
import xyz.columnal.utility.Utility;

public class UnitTimesExpression extends UnitExpression
{
    private final ImmutableList<@Recorded UnitExpression> operands;

    public UnitTimesExpression(ImmutableList<@Recorded UnitExpression> operands)
    {
        this.operands = operands;
    }

    @Override
    public JellyUnit asUnit(UnitManager unitManager) throws UnitLookupException
    {
        JellyUnit r = JellyUnit.fromConcrete(Unit.SCALAR);
        for (@Recorded UnitExpression operand : operands)
        {
            r = r.times(operand.asUnit(unitManager));
        }
        return r;
    }

    @Override
    public String save(SaveDestination saveDestination, boolean topLevel)
    {
        StringBuilder b = new StringBuilder();
        if (!topLevel)
            b.append("(");
        for (int i = 0; i < operands.size(); i++)
        {
            b.append(operands.get(i).save(saveDestination, false));
            if (i < operands.size() - 1)
            {
                b.append("*");
            }
        }
        if (!topLevel)
            b.append(")");
        return b.toString();
    }

    public ImmutableList<@Recorded UnitExpression> getOperands()
    {
        return operands;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UnitTimesExpression that = (UnitTimesExpression) o;

        return operands.equals(that.operands);
    }

    @Override
    public boolean isEmpty()
    {
        return operands.isEmpty() || (operands.size() == 1 && operands.get(0).isEmpty());
    }

    @Override
    public boolean isScalar()
    {
        return operands.size() == 1 && operands.get(0).isScalar();
    }

    @Override
    public int hashCode()
    {
        return operands.hashCode();
    }

    @Override
    @SuppressWarnings("recorded") // Because the replaced version is immediately loaded again
    public UnitExpression replaceSubExpression(UnitExpression toReplace, UnitExpression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return new UnitTimesExpression(Utility.mapListI(operands, t -> t.replaceSubExpression(toReplace, replaceWith)));
    }
}
