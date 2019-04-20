package records.transformations.expression;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.jellytype.JellyUnit;
import styled.StyledString;
import utility.Either;
import utility.Pair;

import java.util.Collections;
import java.util.List;

public class UnitExpressionIntLiteral extends UnitExpression
{
    private final int number;

    public UnitExpressionIntLiteral(int number)
    {
        this.number = number;
    }

    @Override
    public JellyUnit asUnit(@Recorded UnitExpressionIntLiteral this, UnitManager unitManager) throws UnitLookupException
    {
        if (number == 1)
            return JellyUnit.fromConcrete(Unit.SCALAR);
        else
            throw new UnitLookupException(StyledString.s("Expected unit not number"), this, ImmutableList.of());
    }

    @Override
    public String save(boolean structured, boolean topLevel)
    {
        return Integer.toString(number);
    }

    public int getNumber()
    {
        return number;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UnitExpressionIntLiteral that = (UnitExpressionIntLiteral) o;

        return number == that.number;
    }

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    @Override
    public boolean isScalar()
    {
        return number == 1;
    }

    @Override
    public int hashCode()
    {
        return number;
    }

    @Override
    public UnitExpression replaceSubExpression(UnitExpression toReplace, UnitExpression replaceWith)
    {
        return this == toReplace ? replaceWith : this;
    }
}
