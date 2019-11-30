package records.rinterop;

import annotation.qual.ImmediateValue;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Booleans;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.UserException;
import records.rinterop.RVisitor.PairListEntry;
import utility.Pair;
import utility.Utility;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

class RUtility
{
    static final int STRING_SINGLE = 9;
    static final int LOGICAL_VECTOR = 10;
    static final int INT_VECTOR = 13;
    static final int DOUBLE_VECTOR = 14;
    static final int STRING_VECTOR = 16;
    static final int GENERIC_VECTOR = 19;
    static final int PAIR_LIST = 2;
    static final int NIL = 254;
    static final int NA_AS_INTEGER = 0x80000000;
    static final int SYMBOL = 1;
    static final String[] CLASS_DATA_FRAME = {"data.frame"};
    static final String[] CLASS_TIBBLE = {"tbl_df", "tbl", "data.frame"};

    public static RValue intVector(int[] values, @Nullable RValue attributes)
    {
        return new RValue()
        {
            @Override
            public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
            {
                return visitor.visitIntList(values, attributes);
            }
        };
    }

    public static RValue doubleVector(double[] values, @Nullable RValue attributes)
    {
        return new RValue()
        {
            @Override
            public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
            {
                return visitor.visitDoubleList(values, attributes);
            }
        };
    }

    public static RValue logicalVector(boolean[] values, boolean @Nullable [] isNA, @Nullable RValue attributes)
    {
        return new RValue()
        {
            @Override
            public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
            {
                return visitor.visitLogicalList(values, isNA, attributes);
            }
        };
    }

    public static RValue genericVector(ImmutableList<RValue> values, @Nullable RValue attributes, boolean isObject)
    {
        return new RValue()
        {
            @Override
            public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
            {
                return visitor.visitGenericList(values, attributes, isObject);
            }
        };
    }

    public static RValue stringVector(@Value @Nullable String singleValue)
    {
        return stringVector(ImmutableList.<Optional<@Value String>>of(Optional.<@Value String>ofNullable(singleValue)), null);
    }

    public static RValue stringVector(ImmutableList<Optional<@Value String>> values, @Nullable RValue attributes)
    {
        return new RValue()
        {
            @Override
            public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
            {
                return visitor.visitStringList(values, attributes);
            }
        };
    }

    public static RValue string(@Nullable String value, boolean isSymbol)
    {
        return new RValue()
        {
            @Override
            public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
            {
                return visitor.visitString(value == null ? null : DataTypeUtility.value(value), isSymbol);
            }
        };
    }

    public static RValue pairListFromMap(ImmutableMap<String, RValue> values)
    {
        return makePairList(values.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey())).map(e -> new PairListEntry(null, string(e.getKey(), true), e.getValue())).collect(ImmutableList.<PairListEntry>toImmutableList()));
    }

    public static RValue makePairList(ImmutableList<PairListEntry> values)
    {
        return new RValue()
        {
            @Override
            public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
            {
                return visitor.visitPairList(values);
            }
        };
    }

    static @Value String getStringNN(RValue rValue) throws UserException, InternalException
    {
        return rValue.visit(new SpecificRVisitor<@Value String>()
        {
            @Override
            public @Value String visitString(@Nullable @Value String s, boolean isSymbol) throws InternalException, UserException
            {
                if (s != null)
                    return s;
                else
                    throw new UserException("Unexpected NA in internal String");
            }
        });
    }

    static @Nullable @Value String getString(RValue rValue) throws UserException, InternalException
    {
        return rValue.visit(new SpecificRVisitor<@Nullable @Value String>()
        {
            @Override
            public @Nullable @Value String visitString(@Nullable @Value String s, boolean isSymbol) throws InternalException, UserException
            {
                return s;
            }
        });
    }

    static RValue getListItem(RValue info, int index) throws UserException, InternalException
    {
        return info.visit(new SpecificRVisitor<RValue>() {

            @Override
            public RValue visitStringList(ImmutableList<Optional<@Value String>> values, @Nullable RValue attributes) throws InternalException, UserException
            {
                return string(values.get(index).orElse(null), false);
            }

            @Override
            public RValue visitGenericList(ImmutableList<RValue> values, @Nullable RValue attributes, boolean isObject) throws InternalException, UserException
            {
                return values.get(index);
            }

            @Override
            public RValue visitPairList(ImmutableList<PairListEntry> items) throws InternalException, UserException
            {
                return items.get(index).item;
            }
        });
    }

    static Utility.@ImmediateValue ListEx valueImmediate(int[] values)
    {
        return DataTypeUtility.valueImmediate(IntStream.of(values).<@ImmediateValue Object>mapToObj(i -> DataTypeUtility.value(i)).collect(ImmutableList.<@ImmediateValue Object>toImmutableList()));
    }

    static Utility.@ImmediateValue ListEx valueImmediate(boolean[] values)
    {
        return DataTypeUtility.valueImmediate(Booleans.asList(values).stream().<@ImmediateValue Object>map(b -> DataTypeUtility.value(b)).collect(ImmutableList.<@ImmediateValue Object>toImmutableList()));
    }

    static boolean isClass(ImmutableMap<String, RValue> attrMap, String... classNames) throws UserException, InternalException
    {
        RValue classRList = attrMap.get("class");
        if (classRList == null)
            return false;
        for (int i = 0; i < classNames.length; i++)
        {
            if (!classNames[i].equals(getString(getListItem(classRList, i))))
                return false;
        }
        return true;
    }

    static ImmutableMap<String, RValue> pairListToMap(@Nullable RValue attributes) throws UserException, InternalException
    {
        if (attributes == null)
            return ImmutableMap.of();
        return Utility.<String, RValue>pairListToMap(attributes.<ImmutableList<Pair<String, RValue>>>visit(new SpecificRVisitor<ImmutableList<Pair<String, RValue>>>()
        {
            @Override
            public ImmutableList<Pair<String, RValue>> visitPairList(ImmutableList<PairListEntry> items) throws InternalException, UserException
            {
                return Utility.mapListExI(items, e -> {
                    if (e.tag == null)
                        throw new UserException("Missing tag name ");
                    return new Pair<>(getStringNN(e.tag), e.item);
                });
            }
        }));
    }

    static RValue makeClassAttributes(String className, ImmutableMap<String, RValue> otherItems)
    {
        return pairListFromMap(Utility.appendToMap(otherItems, "class", stringVector(DataTypeUtility.value(className)), null));
    }

    static RValue makeClassAttributes(String[] className, ImmutableMap<String, RValue> otherItems)
    {
        return pairListFromMap(Utility.appendToMap(otherItems, "class", stringVector(Arrays.stream(className).<Optional<@Value String>>map(s -> Optional.<@Value String>of(DataTypeUtility.value(s))).collect(ImmutableList.<Optional<@Value String>>toImmutableList()), null), null));
    }

    static RValue dateTimeZonedVector(double[] values, @Nullable RValue attr) throws InternalException, UserException
    {
        ImmutableMap<String, RValue> attrMap = pairListToMap(attr);
        RValue tzone = attrMap.get("tzone");
        if (tzone != null)
        {
            ImmutableList.Builder<Optional<@Value TemporalAccessor>> b = ImmutableList.builderWithExpectedSize(values.length);
            for (double value : values)
            {
                if (Double.isNaN(value))
                    b.add(Optional.empty());
                else
                {
                    BigDecimal bd = doubleToValue(value);
                    @SuppressWarnings("valuetype")
                    @Value ZonedDateTime zdt = ZonedDateTime.ofInstant(Instant.ofEpochSecond(bd.longValue(), bd.remainder(BigDecimal.ONE).scaleByPowerOfTen(9).longValue()), ZoneId.of(getStringNN(getListItem(tzone, 0))));
                    b.add(Optional.of(zdt));
                }
            }
            return ConvertToR.temporalVector(new DateTimeInfo(DateTimeType.DATETIMEZONED), b.build());
        }
        else
        {
            ImmutableList.Builder<Optional<@Value TemporalAccessor>> b = ImmutableList.builderWithExpectedSize(values.length);
            for (double value : values)
            {
                if (Double.isNaN(value))
                    b.add(Optional.empty());
                else
                {
                    BigDecimal bd = doubleToValue(value);
                    @SuppressWarnings("valuetype")
                    @Value LocalDateTime ldt = LocalDateTime.ofInstant(Instant.ofEpochSecond(bd.longValue(), bd.remainder(BigDecimal.ONE).scaleByPowerOfTen(9).longValue()), ZoneId.of("UTC"));
                    b.add(Optional.of(ldt));
                }
            }
            return ConvertToR.temporalVector(new DateTimeInfo(DateTimeType.DATETIME), b.build());
        }
    }

    static RValue dateVector(double[] values, @Nullable RValue attr) throws InternalException
    {
        if (DoubleStream.of(values).allMatch(d -> Double.isNaN(d) || d == (int)d))
        {
            ImmutableList<Optional<@Value TemporalAccessor>> dates = DoubleStream.of(values).<Optional<@Value TemporalAccessor>>mapToObj(d -> {
                if (Double.isNaN(d))
                    return Optional.empty();
                @SuppressWarnings("valuetype")
                @Value LocalDate date = LocalDate.ofEpochDay((int) d);
                return Optional.of(date);
            }).collect(ImmutableList.<Optional<@Value TemporalAccessor>>toImmutableList());
            return new RValue()
            {
                @Override
                public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
                {
                    return visitor.visitTemporalList(DateTimeType.YEARMONTHDAY, dates, attr);
                }
            };
        }
        else
        {
            ImmutableList<Optional<@Value TemporalAccessor>> dates = DoubleStream.of(values).<Optional<@Value TemporalAccessor>>mapToObj(d -> {
                if (Double.isNaN(d))
                    return Optional.empty();
                BigDecimal bd = doubleToValue(d).multiply(new BigDecimal(60.0 * 60.0 * 24.0));
                @SuppressWarnings("valuetype")
                @Value LocalDateTime date = LocalDateTime.ofEpochSecond(bd.longValue(), bd.remainder(BigDecimal.ONE).abs().scaleByPowerOfTen(9).intValue(), ZoneOffset.UTC);
                return Optional.of(date);
            }).collect(ImmutableList.<Optional<@Value TemporalAccessor>>toImmutableList());
            return new RValue()
            {
                @Override
                public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
                {
                    return visitor.visitTemporalList(DateTimeType.DATETIME, dates, attr);
                }
            };
        }
    }

    static @ImmediateValue BigDecimal doubleToValue(double value)
    {
        // Go through Double.toString which zeroes out the boring end part:
        return DataTypeUtility.value(new BigDecimal(Double.toString(value)));
    }
}
