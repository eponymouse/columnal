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

package test.gen;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.KeyFor;
import xyz.columnal.data.unit.SingleUnit;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.CallExpression;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.SingleUnitExpression;
import xyz.columnal.transformations.expression.UnitDivideExpression;
import xyz.columnal.transformations.expression.UnitExpression;
import xyz.columnal.transformations.expression.UnitExpressionIntLiteral;
import xyz.columnal.transformations.expression.UnitRaiseExpression;
import xyz.columnal.transformations.expression.UnitTimesExpression;
import xyz.columnal.transformations.function.FunctionList;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.stream.Collectors;

public abstract class GenValueBaseE<T> extends GenValueBase<T>
{
    protected GenValueBaseE(Class<T> type)
    {
        super(type);
    }

    @SuppressWarnings("recorded")
    public UnitExpression makeUnitExpression(Unit unit)
    {
        // TODO make more varied unit expressions which cancel out

        if (unit.getDetails().isEmpty())
            return new UnitExpressionIntLiteral(1);

        // TODO add UnitRaiseExpression more (don't always split units into single powers)

        // Flatten into a list of units, true for numerator, false for denom:
        List<Pair<SingleUnit, Boolean>> singleUnits = unit.getDetails().entrySet().stream().flatMap((Entry<@KeyFor("unit.getDetails()") SingleUnit, Integer> e) -> Utility.replicate(Math.abs(e.getValue()), new Pair<>((SingleUnit)e.getKey(), e.getValue() > 0)).stream()).collect(Collectors.toList());

        // Now shuffle them and form a compound expression:
        Collections.shuffle(singleUnits, new Random(r.nextLong()));

        // If just one, return it:

        UnitExpression u;

        if (singleUnits.get(0).getSecond())
            u = new SingleUnitExpression(singleUnits.get(0).getFirst().getName());
        else
        {
            if (r.nextBoolean())
                u = new UnitRaiseExpression(new SingleUnitExpression(singleUnits.get(0).getFirst().getName()), new UnitExpressionIntLiteral(-1));
            else
                u = new UnitDivideExpression(new UnitExpressionIntLiteral(1), new SingleUnitExpression(singleUnits.get(0).getFirst().getName()));
        }

        for (int i = 1; i < singleUnits.size(); i++)
        {
            Pair<SingleUnit, Boolean> s = singleUnits.get(i);
            SingleUnitExpression sExp = new SingleUnitExpression(s.getFirst().getName());
            if (s.getSecond())
            {
                // Times.  Could join it to existing one:
                if (u instanceof UnitTimesExpression && r.nextBoolean())
                {
                    List<@Recorded UnitExpression> prevOperands = new ArrayList<>(((UnitTimesExpression)u).getOperands());

                    prevOperands.add(sExp);
                    u = new UnitTimesExpression(ImmutableList.copyOf(prevOperands));
                }
                else
                {
                    // Make a new one:
                    ImmutableList<UnitExpression> operands;
                    if (r.nextBoolean())
                        operands = ImmutableList.of(u, sExp);
                    else
                        operands = ImmutableList.of(sExp, u);
                    u = new UnitTimesExpression(operands);
                }
            }
            else
            {
                // Divide.  Could join it to existing:
                if (u instanceof UnitDivideExpression && r.nextBoolean())
                {
                    UnitDivideExpression div = (UnitDivideExpression) u;
                    ImmutableList<UnitExpression> newDenom = ImmutableList.of(div.getDenominator(), sExp);
                    u = new UnitDivideExpression(div.getNumerator(), new UnitTimesExpression(newDenom));
                }
                else
                {
                    u = new UnitDivideExpression(u, sExp);
                }
            }
        }

        return u;
    }

    protected final CallExpression call(String name, Expression... args)
    {
        try
        {
            return new CallExpression(FunctionList.getFunctionLookup(new UnitManager()), name, args);
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }
}
