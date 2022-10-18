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

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.DataTypeVisitor;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.transformations.expression.function.ValueFunction;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.simulation.SimulationFunction;
import xyz.columnal.utility.TaggedValue;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;
import xyz.columnal.utility.Utility.Record;
import xyz.columnal.utility.Utility.RecordMap;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.Collections;
import java.util.Map.Entry;

/**
 * Created by neil on 22/01/2017.
 */
@OnThread(Tag.Simulation)
public abstract class GenValueBase<T> extends Generator<T>
{
    // Easier than passing parameters around:
    protected SourceOfRandomness r;
    protected GenerationStatus gs;
    protected boolean numberOnlyInt;

    @SuppressWarnings("initialization")
    protected GenValueBase(Class<T> type)
    {
        super(type);
    }

    protected @Value Object makeValue(DataType t) throws UserException, InternalException
    {
        return t.apply(new DataTypeVisitor<@Value Object>()
        {
            @Override
            @OnThread(Tag.Simulation)
            public @Value Object number(NumberInfo displayInfo) throws InternalException, UserException
            {
                if (numberOnlyInt)
                    return genInt();
                else
                    return TBasicUtil.generateNumberV(r, gs);
            }

            @Override
            @OnThread(Tag.Simulation)
            public @Value Object text() throws InternalException, UserException
            {
                return TBasicUtil.makeStringV(r, gs);
            }

            @Override
            @OnThread(Tag.Simulation)
            public @Value Object date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                switch (dateTimeInfo.getType())
                {
                    case YEARMONTHDAY:
                        return TBasicUtil.generateDate(r, gs);
                    case YEARMONTH:
                        return YearMonth.of(r.nextInt(1, 9999), r.nextInt(1, 12));
                    case TIMEOFDAY:
                        return TBasicUtil.generateTime(r, gs);
                    //case TIMEOFDAYZONED:
                        //return OffsetTime.of(TestUtil.generateTime(r, gs), ZoneOffset.ofTotalSeconds(60 * r.nextInt(-18*60, 18*60)));
                    case DATETIME:
                        return TBasicUtil.generateDateTime(r, gs);
                    case DATETIMEZONED:
                        // Can be geographical or pure offset:
                        return ZonedDateTime.of(TBasicUtil.generateDateTime(r, gs),
                            r.nextBoolean() ?
                                new GenZoneId().generate(r, gs) :
                                ZoneId.ofOffset("", TBasicUtil.generateZoneOffset(r, gs))
                        );
                    default:
                        throw new InternalException("Unknown date type: " + dateTimeInfo.getType());
                }

            }

            @Override
            @OnThread(Tag.Simulation)
            public @Value Object bool() throws InternalException, UserException
            {
                return DataTypeUtility.value(r.nextBoolean());
            }

            @Override
            @OnThread(Tag.Simulation)
            public @Value Object tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                int tagIndex = r.nextInt(0, tags.size() - 1);
                @Nullable @Value Object o;
                @Nullable DataType inner = tags.get(tagIndex).getInner();
                if (inner != null)
                    o = makeValue(inner);
                else
                    o = null;
                return new TaggedValue(tagIndex, o, DataTypeUtility.fromTags(tags));
            }

            @Override
            @OnThread(Tag.Simulation)
            public @Value Object record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, UserException
            {
                return DataTypeUtility.value(new RecordMap(Utility.<@ExpressionIdentifier String, DataType, @Value Object>mapValuesEx(fields, t -> makeValue(t))));
            }

            @Override
            @OnThread(Tag.Simulation)
            public @Value Object array(@Nullable DataType inner) throws InternalException, UserException
            {
                if (inner == null)
                    return DataTypeUtility.<@Value Object>value(Collections.<@Value Object>emptyList());
                @NonNull DataType innerFinal = inner;
                return DataTypeUtility.value(TBasicUtil.<@Value Object>makeList(r, 1, 4, () -> makeValue(innerFinal)));
            }

            @Override
            @OnThread(Tag.Simulation)
            public @Value Object function(ImmutableList<DataType> argTypes, DataType resultType) throws InternalException, UserException
            {
                if (resultType.equals(DataType.BOOLEAN) && argTypes.size() == 1)
                {
                    return ValueFunction.value(argTypes.get(0).apply(new DataTypeVisitor<ValueFunction>()
                    {
                        private <T> ValueFunction f(Class<T> type, SimulationFunction<@Value T, Boolean> predicate)
                        {
                            return new ValueFunction()
                            {
                                @Override
                                public @OnThread(Tag.Simulation) @Value Object _call() throws InternalException, UserException
                                {
                                    return DataTypeUtility.value(predicate.apply(arg(0, type)));
                                }
                            };
                        }
                        
                        @Override
                        public ValueFunction number(NumberInfo numberInfo) throws InternalException, UserException
                        {
                            return r.choose(ImmutableList.<ValueFunction>of(
                                    this.<Number>f(Number.class, n -> n.doubleValue() > 0),
                                    this.<Number>f(Number.class, n -> n.longValue() <= 0),
                                    this.<Number>f(Number.class, Utility::isIntegral)
                            ));
                        }

                        @Override
                        public ValueFunction text() throws InternalException, UserException
                        {
                            return r.choose(ImmutableList.<ValueFunction>of(
                                    this.<String>f(String.class, String::isEmpty),
                                    this.<String>f(String.class, s -> s.contains("a"))
                            ));
                        }

                        @Override
                        public ValueFunction date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
                        {
                            return f(TemporalAccessor.class, t -> true);
                        }

                        @Override
                        public ValueFunction bool() throws InternalException, UserException
                        {
                            return r.choose(ImmutableList.<ValueFunction>of(
                                    this.<Boolean>f(Boolean.class, b -> b),
                                    this.<Boolean>f(Boolean.class, b -> !b)
                            ));
                        }

                        @Override
                        public ValueFunction tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
                        {
                            return f(TaggedValue.class, t -> t.getTagIndex() == 0);
                        }

                        @Override
                        public ValueFunction record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, UserException
                        {
                            Entry<@ExpressionIdentifier String, DataType> entry = fields.entrySet().iterator().next();
                            // Choose once outside function invocation:
                            ValueFunction innerFunc = entry.getValue().apply(this);
                            return f(Record.class, o -> Utility.cast(innerFunc.call(new @Value Object[] {o.getField(entry.getKey())}), Boolean.class));
                        }

                        @Override
                        public ValueFunction array(DataType inner) throws InternalException, UserException
                        {
                            return f(ListEx.class, l -> l.size() == 0);
                        }
                    }));
                }
                throw new InternalException("We only support functions with Boolean return type for testing");
            }
        });
    }

    protected @Value long genInt()
    {
        @Value Number n;
        do
        {
            n = TBasicUtil.generateNumberV(r, gs);
        }
        while (n instanceof BigDecimal);
        return DataTypeUtility.value(n.longValue());
    }
}
