package test.gen;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.generator.java.time.ZoneOffsetGenerator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataTypeUtility;
import utility.TaggedValue;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.NumberInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.TypeId;
import records.error.InternalException;
import records.error.UserException;
import test.TestUtil;
import utility.Utility;

import java.math.BigDecimal;
import java.time.OffsetTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import static test.TestUtil.distinctTypes;
import static test.TestUtil.generateNumberV;

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
                    return generateNumberV(r, gs);
            }

            @Override
            public @Value Object text() throws InternalException, UserException
            {
                return TestUtil.makeStringV(r, gs);
            }

            @Override
            public @Value Object date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                switch (dateTimeInfo.getType())
                {
                    case YEARMONTHDAY:
                        return TestUtil.generateDate(r, gs);
                    case YEARMONTH:
                        return YearMonth.of(r.nextInt(1, 9999), r.nextInt(1, 12));
                    case TIMEOFDAY:
                        return TestUtil.generateTime(r, gs);
                    case TIMEOFDAYZONED:
                        return OffsetTime.of(TestUtil.generateTime(r, gs), ZoneOffset.ofTotalSeconds(60 * r.nextInt(-18*60, 18*60)));
                    case DATETIME:
                        return TestUtil.generateDateTime(r, gs);
                    case DATETIMEZONED:
                        // Can be geographical or pure offset:
                        return ZonedDateTime.of(TestUtil.generateDateTime(r, gs),
                            r.nextBoolean() ?
                                new GenZoneId().generate(r, gs) :
                                ZoneId.ofOffset("", TestUtil.generateZoneOffset(r, gs))
                        ).withFixedOffsetZone();
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
            public @Value Object tagged(TypeId typeName, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                int tagIndex = r.nextInt(0, tags.size() - 1);
                @Nullable @Value Object o;
                @Nullable DataType inner = tags.get(tagIndex).getInner();
                if (inner != null)
                    o = makeValue(inner);
                else
                    o = null;
                return new TaggedValue(tagIndex, o);
            }

            @Override
            public @Value Object tuple(ImmutableList<DataType> inner) throws InternalException, UserException
            {
                return DataTypeUtility.value(Utility.mapListEx(inner, t -> makeValue(t)).toArray(new @Value Object[0]));
            }

            @Override
            public @Value Object array(@Nullable DataType inner) throws InternalException, UserException
            {
                if (inner == null)
                    return DataTypeUtility.value(Collections.emptyList());
                @NonNull DataType innerFinal = inner;
                return DataTypeUtility.value(TestUtil.<@Value Object>makeList(r, 1, 12, () -> makeValue(innerFinal)));
            }
        });
    }

    protected @Value long genInt()
    {
        @Value Number n;
        do
        {
            n = generateNumberV(r, gs);
        }
        while (n instanceof BigDecimal);
        return DataTypeUtility.value(n.longValue());
    }

    public static DataType makeType(SourceOfRandomness r)
    {
        return r.choose(distinctTypes);
    }
}
