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

package xyz.columnal.transformations.function.datetime;

import annotation.qual.Value;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.function.ValueFunction;
import xyz.columnal.transformations.function.FunctionDefinition;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.simulation.SimulationFunction;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;

public class YearsBetween extends FunctionDefinition
{
    public YearsBetween() throws InternalException
    {
        super("datetime:years between");
    }

    @Override
    public @OnThread(Tag.Simulation) ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException, UserException
    {
        return new ValueFunction()
        {
            @Override
            public @OnThread(Tag.Simulation) @Value Object _call() throws InternalException, UserException
            {
                @Value Temporal lhs = arg(0, Temporal.class);
                @Value Temporal rhs = arg(1, Temporal.class);
                long wholeYears = ChronoUnit.YEARS.between(lhs, rhs);
                LocalDate lhsMDInRhsYear = LocalDate.of(rhs.get(ChronoField.YEAR), lhs.get(ChronoField.MONTH_OF_YEAR), lhs.get(ChronoField.DAY_OF_MONTH));
                LocalDate lhsMDInRhsYearPlusOne = LocalDate.of(rhs.get(ChronoField.YEAR) + 1, lhs.get(ChronoField.MONTH_OF_YEAR), lhs.get(ChronoField.DAY_OF_MONTH));
                long daysBetween = ChronoUnit.DAYS.between(lhsMDInRhsYear, rhs);
                long daysThatYear = ChronoUnit.DAYS.between(lhsMDInRhsYear, lhsMDInRhsYearPlusOne);
                BigDecimal r = new BigDecimal(daysBetween).divide(new BigDecimal(daysThatYear), MathContext.DECIMAL128).add(new BigDecimal(wholeYears), MathContext.DECIMAL128);
                return DataTypeUtility.value(r);
            }
        };
    }
}
