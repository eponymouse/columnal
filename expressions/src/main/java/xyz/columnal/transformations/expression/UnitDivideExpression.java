/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.transformations.expression;

import annotation.recorded.qual.Recorded;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.jellytype.JellyUnit;
import xyz.columnal.transformations.expression.Expression.SaveDestination;

public class UnitDivideExpression extends UnitExpression
{
    private final @Recorded UnitExpression numerator;
    private final @Recorded UnitExpression denominator;

    public UnitDivideExpression(@Recorded UnitExpression numerator, @Recorded UnitExpression denominator)
    {
        this.numerator = numerator;
        this.denominator = denominator;
    }

    @Override
    public JellyUnit asUnit(UnitManager unitManager) throws UnitLookupException
    {
        JellyUnit num = numerator.asUnit(unitManager);
        JellyUnit den = denominator.asUnit(unitManager);

        return num.divideBy(den);
    }

    @Override
    public String save(SaveDestination saveDestination, boolean topLevel)
    {
        String core = numerator.save(saveDestination, false) + "/" + denominator.save(saveDestination, false);
        if (topLevel)
            return core;
        else
            return "(" + core + ")";
    }

    public UnitExpression getNumerator()
    {
        return numerator;
    }

    public UnitExpression getDenominator()
    {
        return denominator;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UnitDivideExpression that = (UnitDivideExpression) o;

        if (!numerator.equals(that.numerator)) return false;
        return denominator.equals(that.denominator);
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
        int result = numerator.hashCode();
        result = 31 * result + denominator.hashCode();
        return result;
    }

    @SuppressWarnings("recorded")
    @Override
    public UnitExpression replaceSubExpression(UnitExpression toReplace, UnitExpression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return new UnitDivideExpression(numerator.replaceSubExpression(toReplace, replaceWith), denominator.replaceSubExpression(toReplace, replaceWith));
    }
}
