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

import com.google.common.collect.ImmutableList;
import xyz.columnal.log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.transformations.expression.function.FunctionLookup;
import xyz.columnal.transformations.expression.function.StandardFunctionDefinition;
import xyz.columnal.transformations.function.comparison.Max;
import xyz.columnal.transformations.function.comparison.MaxIndex;
import xyz.columnal.transformations.function.comparison.Min;
import xyz.columnal.transformations.function.comparison.MinIndex;
import xyz.columnal.transformations.function.conversion.ExtractNumber;
import xyz.columnal.transformations.function.conversion.ExtractNumberOrNone;
import xyz.columnal.transformations.function.core.AsType;
import xyz.columnal.transformations.function.core.AsUnit;
import xyz.columnal.transformations.function.core.TypeOf;
import xyz.columnal.transformations.function.datetime.AddDays;
import xyz.columnal.transformations.function.datetime.DaysBetween;
import xyz.columnal.transformations.function.datetime.SecondsBetween;
import xyz.columnal.transformations.function.datetime.YearsBetween;
import xyz.columnal.transformations.function.list.*;
import xyz.columnal.transformations.function.lookup.LookupFunctions;
import xyz.columnal.transformations.function.math.Logarithm;
import xyz.columnal.transformations.function.math.LogarithmNatural;
import xyz.columnal.transformations.function.number.Round;
import xyz.columnal.transformations.function.number.RoundDP;
import xyz.columnal.transformations.function.number.RoundSF;
import xyz.columnal.transformations.function.optional.GetOptionalOrDefault;
import xyz.columnal.transformations.function.optional.GetOptionalOrError;
import xyz.columnal.transformations.function.optional.GetOptionalsFromList;
import xyz.columnal.transformations.function.text.StringJoin;
import xyz.columnal.transformations.function.text.StringJoinWith;
import xyz.columnal.transformations.function.text.StringLowerCase;
import xyz.columnal.transformations.function.text.StringReplace;
import xyz.columnal.transformations.function.text.StringReplaceMany;
import xyz.columnal.transformations.function.text.StringSplit;
import xyz.columnal.utility.Utility;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by neil on 13/12/2016.
 */
public class FunctionList
{
    public static ImmutableList<FunctionDefinition> getAllFunctions(UnitManager unitManager) throws InternalException
    {
        return Utility.<FunctionDefinition>concatStreams(Arrays.<FunctionDefinition>asList(
            new Absolute(),
            new AddDays(),
            new AnyAllNone.Any(),
            new AnyAllNone.All(),
            new AnyAllNone.None(),
            new AsType(),
            new AsUnit(),
            new Combine(),
            new Count(),
            new CountWhere(),
            new DaysBetween(),
            new ExtractNumber(),
            new ExtractNumberOrNone(),
            new GetElement(),
            new GetElementOrDefault(),
            new GetOptionalOrError(),
            new GetOptionalOrDefault(),
            new GetOptionalsFromList(),
            new InList(),
            new JoinLists(),
            new KeepFunction(),
            new Logarithm(),
            new LogarithmNatural(),
            new MapFunction(),
            new Max(),
            new MaxIndex(),
            new Mean(),
            new Min(),
            new MinIndex(),
            new Not(),
            new Round(),
            new RoundDP(),
            new RoundSF(),
            new SecondsBetween(),
            new Single(),
            new StringJoin(),
            new StringJoinWith(),
            // TODO document and put back all these string functions:
            //new StringLeft(),
            new StringLength(),
            new StringLowerCase(),
            //new StringMid(),
            new StringReplace(),
            new StringReplaceMany(),
            new StringSplit(),
            //new StringRight(),
            new StringTrim(),
            //new StringWithin(),
            //new StringWithinIndex(),
            new Sum(),
            new ToString(),
            new TypeOf(),
            new Xor(),
            new YearsBetween()
        ).stream(),
            FromString.getFunctions().stream(),
            LookupFunctions.getLookupFunctions().stream(),
            Stream.<ToTemporalFunction>of(
                // TODO document and put back all these date conversion functions:
                new ToDate(),
                new ToTime(),
                new ToDateTime(),
                new ToDateTimeZone(),
                new ToYearMonth()
        ).<FunctionDefinition>flatMap(t -> {
            try
            {
                return t.getTemporalFunctions(unitManager).stream();
            }
            catch (InternalException e)
            {
                Log.log(e);
                return Stream.empty();
            }
        })).collect(ImmutableList.<FunctionDefinition>toImmutableList());
    }

    public static @Nullable FunctionDefinition lookup(UnitManager mgr, String functionName) throws InternalException
    {
        for (FunctionDefinition functionDefinition : getAllFunctions(mgr))
        {
            if (functionDefinition.getName().equals(functionName) || functionDefinition.getDocKey().equals(functionName) || functionDefinition.getFullName().stream().collect(Collectors.joining("\\")).equals(functionName))
                return functionDefinition;
        }
        return null;
    }

    public static FunctionLookup getFunctionLookup(UnitManager unitManager)
    {
        return new FunctionLookup()
        {
            @Override
            public @Nullable StandardFunctionDefinition lookup(String functionName) throws InternalException
            {
                return FunctionList.lookup(unitManager, functionName);
            }

            @Override
            public ImmutableList<StandardFunctionDefinition> getAllFunctions() throws InternalException
            {
                return Utility.mapListI(FunctionList.getAllFunctions(unitManager), f -> f);
            }
        };
    }
}
