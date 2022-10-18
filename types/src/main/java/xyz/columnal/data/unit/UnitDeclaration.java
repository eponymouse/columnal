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

package xyz.columnal.data.unit;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import org.sosy_lab.common.rationals.Rational;
import xyz.columnal.utility.adt.Pair;

import java.util.Objects;

/**
 * Created by neil on 09/12/2016.
 */
public class UnitDeclaration
{
    private final SingleUnit definedUnit;
    private final @Nullable Pair<Rational, Unit> equivalentTo;
    private final Unit cachedSingleUnit;
    private final String category;

    public UnitDeclaration(SingleUnit definedUnit, @Nullable Pair<Rational, Unit> equivalentTo, String category)
    {
        this.definedUnit = definedUnit;
        this.equivalentTo = equivalentTo;
        cachedSingleUnit = new Unit(definedUnit);
        this.category = category;
    }

    public SingleUnit getDefined()
    {
        return definedUnit;
    }

    public Unit getUnit()
    {
        return cachedSingleUnit;
    }

    @Pure
    public @Nullable Pair<Rational, Unit> getEquivalentTo()
    {
        return equivalentTo;
    }

    // Needed for testing:
    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UnitDeclaration that = (UnitDeclaration) o;
        return Objects.equals(definedUnit, that.definedUnit) &&
                Objects.equals(equivalentTo, that.equivalentTo);
    }

    @Override
    public int hashCode()
    {

        return Objects.hash(definedUnit, equivalentTo);
    }

    @Override
    public String toString()
    {
        return "UnitDeclaration{" +
                "definedUnit=" + definedUnit +
                ", equivalentTo=" + equivalentTo +
                '}';
    }

    public String getCategory()
    {
        return category;
    }
}
