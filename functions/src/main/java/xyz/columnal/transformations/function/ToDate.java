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

package xyz.columnal.transformations.function;

import annotation.funcdoc.qual.FuncDocKey;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.DateTimeInfo.DateTimeType;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.simulation.SimulationFunction;
import xyz.columnal.transformations.expression.function.ValueFunction;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;

/**
 * Created by neil on 14/12/2016.
 */
public class ToDate extends ToTemporalFunction
{


    public static final @FuncDocKey String DATE_FROM_DATETIME = "datetime:date from datetime";

    @Override
    public ImmutableList<FunctionDefinition> getTemporalFunctions(UnitManager mgr) throws InternalException
    {
        ImmutableList.Builder<FunctionDefinition> r = ImmutableList.builder();
        r.add(new FromTemporal(DATE_FROM_DATETIME));
        r.add(new FromTemporal("datetime:date from datetimezoned"));
        /* TODO
        r.add(fromString("date.from.string", "date.from.string.mini"));
        r.add(new FunctionDefinition("date.from.datetime", "date.from.datetime.mini", FromTemporalInstance::new, DataType.date(getResultType()), DataType.date(new DateTimeInfo(DateTimeType.DATETIME))));
        r.add(new FunctionDefinition("date.from.datetimezoned", "date.from.datetimezoned.mini", FromTemporalInstance::new, DataType.date(getResultType()), DataType.date(new DateTimeInfo(DateTimeType.DATETIMEZONED))));
        r.add(new FunctionDefinition("date.from.ym.day", "date.from.ym.day.mini", FromYearMonth_Day::new, DataType.date(getResultType()), DataType.tuple(DataType.date(new DateTimeInfo(DateTimeType.YEARMONTH)), DataType.number(new NumberInfo(mgr.loadBuiltIn("day"))))));
        */
        r.add(new FunctionDefinition("datetime:date from ymd") {
            @Override
            public @OnThread(Tag.Simulation) ValueFunction getInstance(TypeManager typeManager, SimulationFunction<String, Either<Unit, DataType>> paramTypes) throws InternalException, UserException
            {
                return new FromNumbers();
            }
        });
        return r.build();
    }

    @Override
    DateTimeInfo getResultType()
    {
        return new DataType.DateTimeInfo(DateTimeType.YEARMONTHDAY);
    }

    @Override
    @Value Temporal fromTemporal(TemporalAccessor temporalAccessor)
    {
        return LocalDate.from(temporalAccessor);
    }

    private class FromYearMonth_Day extends ValueFunction
    {
        @Override
        public @Value Object _call() throws UserException, InternalException
        {
            YearMonth ym = arg(0, YearMonth.class);
            int day = intArg(1);
            try
            {
                return LocalDate.of(ym.getYear(), ym.getMonth(), day);
            }
            catch (DateTimeException e)
            {
                throw new UserException("Invalid date: " + ym.getYear() + ", " + ym.getMonth() + ", " + day + " " + e.getLocalizedMessage(), e);
            }
        }
    }

    private class FromNumbers extends ValueFunction
    {
        @Override
        public @Value Object _call() throws UserException, InternalException
        {
            int year = intArg(0);
            int month = intArg(1);
            int day = intArg(2);
            try
            {
                return LocalDate.of(year, month, day);
            }
            catch (DateTimeException e)
            {
                throw new UserException("Invalid date: " + year + ", " + month + ", " + day + " " + e.getLocalizedMessage(), e);
            }
        }
    }
}
