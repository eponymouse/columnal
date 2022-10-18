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
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.DateTimeInfo.DateTimeType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.function.FunctionDefinition;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.simulation.SimulationFunction;
import xyz.columnal.utility.Utility;
import xyz.columnal.transformations.expression.function.ValueFunction;

import java.time.DateTimeException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;

public class AddDays extends FunctionDefinition
{
    public AddDays() throws InternalException
    {
        super("datetime:add days");
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
                @Value Number rhs = arg(1, Number.class);
                if (!Utility.isIntegral(rhs))
                    throw new UserException("Cannot add " + rhs.toString() + " days; number must be an integer (whole number)");
                try
                {
                    @Value TemporalAccessor value = DataTypeUtility.value(new DateTimeInfo(DateTimeType.YEARMONTHDAY), lhs.plus(rhs.longValue(), ChronoUnit.DAYS));
                    if (value != null)
                        return value;
                    else
                        throw new InternalException("Internal rrror adding " + rhs.longValue() + " days to " + lhs.toString());
                }
                catch (ArithmeticException | DateTimeException e)
                {
                    throw new UserException("Overflow while trying to add " + rhs.longValue() + " days to " + lhs.toString());
                }
            }
        };
    }
}
