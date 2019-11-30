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
import records.data.DataTestUtil;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeId;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.function.ValueFunction;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.SimulationFunction;
import utility.TaggedValue;
import utility.Utility;
import utility.Utility.ListEx;
import utility.Utility.Record;
import utility.Utility.RecordMap;

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
            public @Value Object number(NumberInfo displayInfo) throws InternalException, UserException
            {
                if (numberOnlyInt)
                    return genInt();
                else
                    return DataTestUtil.generateNumberV(r, gs);
            }

            @Override
            public @Value Object text() throws InternalException, UserException
            {
                return DataTestUtil.makeStringV(r, gs);
            }

            @Override
            public @Value Object date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                switch (dateTimeInfo.getType())
                {
                    case YEARMONTHDAY:
                        return DataTestUtil.generateDate(r, gs);
                    case YEARMONTH:
                        return YearMonth.of(r.nextInt(1, 9999), r.nextInt(1, 12));
                    case TIMEOFDAY:
                        return DataTestUtil.generateTime(r, gs);
                    //case TIMEOFDAYZONED:
                        //return OffsetTime.of(TestUtil.generateTime(r, gs), ZoneOffset.ofTotalSeconds(60 * r.nextInt(-18*60, 18*60)));
                    case DATETIME:
                        return DataTestUtil.generateDateTime(r, gs);
                    case DATETIMEZONED:
                        // Can be geographical or pure offset:
                        return ZonedDateTime.of(DataTestUtil.generateDateTime(r, gs),
                            r.nextBoolean() ?
                                new GenZoneId().generate(r, gs) :
                                ZoneId.ofOffset("", DataTestUtil.generateZoneOffset(r, gs))
                        );
                    default:
                        throw new InternalException("Unknown date type: " + dateTimeInfo.getType());
                }

            }

            @Override
            public @Value Object bool() throws InternalException, UserException
            {
                return DataTypeUtility.value(r.nextBoolean());
            }

            @Override
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
            public @Value Object record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, UserException
            {
                return DataTypeUtility.value(new RecordMap(Utility.<@ExpressionIdentifier String, DataType, @Value Object>mapValuesEx(fields, t -> makeValue(t))));
            }

            @Override
            public @Value Object array(@Nullable DataType inner) throws InternalException, UserException
            {
                if (inner == null)
                    return DataTypeUtility.<@Value Object>value(Collections.<@Value Object>emptyList());
                @NonNull DataType innerFinal = inner;
                return DataTypeUtility.value(DataTestUtil.<@Value Object>makeList(r, 1, 4, () -> makeValue(innerFinal)));
            }

            @Override
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
            n = DataTestUtil.generateNumberV(r, gs);
        }
        while (n instanceof BigDecimal);
        return DataTypeUtility.value(n.longValue());
    }
}
