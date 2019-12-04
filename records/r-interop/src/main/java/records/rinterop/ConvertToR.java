package records.rinterop;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Booleans;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.data.datatype.DataTypeValue.DataTypeVisitorGet;
import records.data.datatype.DataTypeValue.GetValue;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeId;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.rinterop.ConvertFromR.TableType;
import utility.Either;
import utility.TaggedValue;
import utility.Utility;
import utility.Utility.ListEx;
import utility.Utility.Record;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;

public class ConvertToR
{
    public static RValue convertTableToR(RecordSet recordSet, TableType tableType) throws UserException, InternalException
    {
        // A table is a generic list of columns with class data.frame
        return RUtility.genericVector(Utility.mapListExI(recordSet.getColumns(), c -> convertColumnToR(c, tableType)),
            RUtility.makeClassAttributes(tableType == TableType.DATA_FRAME ? RUtility.CLASS_DATA_FRAME : RUtility.CLASS_TIBBLE, ImmutableMap.<String, RValue>of(
                "names", RUtility.stringVector(Utility.<Column, Optional<@Value String>>mapListExI(recordSet.getColumns(), c -> Optional.of(DataTypeUtility.value(usToRColumn(c.getName(), tableType, false)))), null),
                "row.names", RUtility.intVector(new int[] {RUtility.NA_AS_INTEGER, -recordSet.getLength()}, null)
            )), true);
    }

    public static String usToRColumn(ColumnId columnId, TableType tableType, boolean quotesIfNeeded)
    {
        return tableType == TableType.TIBBLE ? (columnId.getRaw().contains(" ") && quotesIfNeeded ? "\""+ columnId.getRaw() + "\"" : columnId.getRaw()) : columnId.getRaw().replace(" ", ".");
    }

    private static RValue convertColumnToR(Column column, TableType tableType) throws UserException, InternalException
    {
        DataTypeValue dataTypeValue = column.getType();
        int length = column.getLength();
        return convertListToR(dataTypeValue, length, tableType == TableType.TIBBLE);
    }

    private static RValue convertListToR(DataTypeValue dataTypeValue, int length, boolean allowSubLists) throws UserException, InternalException
    {
        return dataTypeValue.applyGet(new DataTypeVisitorGet<RValue>()
        {
            @Override
            public RValue number(GetValue<@Value Number> g, NumberInfo displayInfo) throws InternalException, UserException
            {
                // Need to work out if they are all ints, otherwise must use doubles.
                // Start with ints and go from there
                int[] ints = new int[length];
                int i;
                for (i = 0; i < length; i++)
                {
                    @Value Number n = g.get(i);
                    @Value Integer nInt = getIfInteger(n);
                    if (nInt != null)
                        ints[i] = nInt;
                    else
                        break;
                }
                if (i == length)
                    return RUtility.intVector(ints, null);
                
                // Convert all ints to doubles:
                double[] doubles = new double[length];
                for (int j = 0; j < i; j++)
                {
                    doubles[j] = ints[j];
                }
                for (; i < length; i++)
                {
                    doubles[i] = Utility.toBigDecimal(g.get(i)).doubleValue();
                }
                return RUtility.doubleVector(doubles, null);
            }

            @Override
            public RValue text(GetValue<@Value String> g) throws InternalException, UserException
            {
                ImmutableList.Builder<Optional<@Value String>> list = ImmutableList.builderWithExpectedSize(length);
                for (int i = 0; i < length; i++)
                {
                    list.add(Optional.of(g.get(i)));
                }
                return RUtility.stringVector(list.build(), null);
            }

            @Override
            public RValue bool(GetValue<@Value Boolean> g) throws InternalException, UserException
            {
                boolean[] bools = new boolean[length];
                for (int i = 0; i < length; i++)
                {
                    bools[i] = g.get(i);
                }
                return RUtility.logicalVector(bools, null, null);
            }

            @Override
            public RValue date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> g) throws InternalException, UserException
            {
                ImmutableList.Builder<Optional<@Value TemporalAccessor>> valueBuilder = ImmutableList.builderWithExpectedSize(length);
                for (int i = 0; i < length; i++)
                {
                    valueBuilder.add(Optional.of(g.get(i)));
                }
                ImmutableList<Optional<@Value TemporalAccessor>> values = valueBuilder.build();
                return temporalVector(dateTimeInfo, values);
            }

            @Override
            public RValue tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tagTypes, GetValue<@Value TaggedValue> g) throws InternalException, UserException
            {
                if (tagTypes.size() == 1)
                {
                    TagType<DataType> onlyTag = tagTypes.get(0);
                    if (onlyTag.getInner() != null)
                    {
                        // Flatten by ignoring taggedness:
                        return onlyTag.getInner().fromCollapsed((i, prog) -> {
                            @Value Object inner = g.getWithProgress(i, prog).getInner();
                            if (inner == null)
                                throw new InternalException("Null inner value on tag with inner type");
                            return inner;
                        }).applyGet(this);
                    }
                }
                if (tagTypes.size() == 2)
                {
                    @Nullable DataType onlyInner = null;
                    if (tagTypes.get(0).getInner() != null && tagTypes.get(1).getInner() == null)
                        onlyInner = tagTypes.get(0).getInner();
                    else if (tagTypes.get(0).getInner() == null && tagTypes.get(1).getInner() != null)
                        onlyInner = tagTypes.get(1).getInner();
                    if (onlyInner != null)
                    {
                        // Can convert to equivalent of maybe; inner plus missing values as NA:
                        ImmutableList.Builder<Optional<@Value Object>> b = ImmutableList.builderWithExpectedSize(length);
                        for (int i = 0; i < length; i++)
                        {
                            @Value TaggedValue taggedValue = g.get(i);
                            if (taggedValue.getInner() != null)
                                b.add(Optional.<@Value Object>of(taggedValue.getInner()));
                            else
                                b.add(Optional.empty());
                        }
                        ImmutableList<Optional<@Value Object>> inners = b.build();
                        return onlyInner.apply(new DataTypeVisitor<RValue>()
                        {
                            @Override
                            public RValue number(NumberInfo numberInfo) throws InternalException, UserException
                            {
                                return RUtility.doubleVector(inners.stream().mapToDouble(mn -> mn.<Double>map(n -> Utility.toBigDecimal((Number)n).doubleValue()).orElse(Double.NaN)).toArray(), null);
                            }

                            @Override
                            public RValue text() throws InternalException, UserException
                            {
                                return RUtility.stringVector(Utility.<Optional<@Value Object>, Optional<@Value String>>mapListI(inners, v -> v.<@Value String>map(o -> (String) o)), null);
                            }

                            @Override
                            public RValue date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
                            {
                                switch (dateTimeInfo.getType())
                                {
                                    case YEARMONTHDAY:
                                        return RUtility.dateVector(inners.stream().mapToDouble(md -> md.map(d -> (double)((LocalDate)d).toEpochDay()).orElse(Double.NaN)).toArray(), RUtility.makeClassAttributes("Date", ImmutableMap.of()));
                                    case YEARMONTH:
                                        return RUtility.dateVector(inners.stream().mapToDouble(md -> md.map(d -> (double)((YearMonth)d).atDay(1).toEpochDay()).orElse(Double.NaN)).toArray(), RUtility.makeClassAttributes("Date", ImmutableMap.of()));
                                    case TIMEOFDAY:
                                        return RUtility.doubleVector(inners.stream().mapToDouble(mn -> mn.<Double>map(n -> (double)((LocalTime)n).toNanoOfDay() / 1_000_000_000.0).orElse(Double.NaN)).toArray(), null);
                                    case DATETIMEZONED:
                                        return RUtility.dateTimeZonedVector(inners.stream().mapToDouble(md -> md.map(d -> {
                                            ZonedDateTime zdt = (ZonedDateTime) d;
                                            return (double) zdt.toEpochSecond() + ((double) zdt.getNano() / 1_000_000_000.0);
                                        }).orElse(Double.NaN)).toArray(), RUtility.makeClassAttributes("POSIXct", ImmutableMap.of()));
                                    case DATETIME:
                                        return RUtility.dateTimeZonedVector(inners.stream().mapToDouble(md -> md.map(d -> {
                                            LocalDateTime ldt = (LocalDateTime) d;
                                            double seconds = (double) ldt.toEpochSecond(ZoneOffset.UTC) + (double) ldt.getNano() / 1_000_000_000.0;
                                            return seconds;
                                        }).orElse(Double.NaN)).toArray(), RUtility.makeClassAttributes("POSIXct", ImmutableMap.of()));
                                }
                                throw new InternalException("Unsupported date-time type: " + dateTimeInfo.getType());
                            }

                            @Override
                            public RValue bool() throws InternalException, UserException
                            {
                                return RUtility.logicalVector(Booleans.toArray(inners.stream().<Boolean>map(mb -> DataTypeUtility.unvalue(((Boolean)mb.orElse(DataTypeUtility.value(false))))).collect(ImmutableList.<Boolean>toImmutableList())), Booleans.toArray(inners.stream().map(b -> !b.isPresent()).collect(ImmutableList.<Boolean>toImmutableList())), null);
                            }

                            @Override
                            public RValue tagged(TypeId innerTypeName, ImmutableList<Either<Unit, DataType>> innerTypeVars, ImmutableList<TagType<DataType>> innerTags) throws InternalException, UserException
                            {
                                if (innerTags.stream().allMatch(tt -> tt.getInner() == null))
                                {
                                    int[] vals = new int[length];
                                    for (int i = 0; i < length; i++)
                                    {
                                        @Value TaggedValue val = g.get(i);
                                        if (val.getTagIndex() == 0)
                                            vals[i] = RUtility.NA_AS_INTEGER;
                                        else
                                        {
                                            @Value Object valInner = val.getInner();
                                            if (valInner == null)
                                                throw new InternalException("Null inner value of present optional value in row " + i);
                                            vals[i] = Utility.cast(valInner, TaggedValue.class).getTagIndex() + 1;
                                        }
                                    }

                                    // Convert to factors:
                                    return new RValue()
                                    {
                                        @Override
                                        public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
                                        {
                                            return visitor.visitFactorList(vals, Utility.mapListI(innerTags, tt -> tt.getName()));
                                        }
                                    };
                                }
                                
                                throw new UserException("Nested tagged types are not supported");
                            }

                            @Override
                            public RValue record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, UserException
                            {
                                throw new UserException("Records are not supported in R");
                            }

                            @Override
                            public RValue array(DataType inner) throws InternalException, UserException
                            {
                                throw new UserException("Lists are not supported in R");
                            }
                        });
                    }
                }
                if (tagTypes.stream().allMatch(tt -> tt.getInner() == null))
                {
                    int[] vals = new int[length];
                    for (int i = 0; i < length; i++)
                    {
                        vals[i] = g.get(i).getTagIndex() + 1;
                    }    
                    
                    // Convert to factors:
                    return new RValue()
                    {
                        @Override
                        public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
                        {
                            return visitor.visitFactorList(vals, Utility.mapListI(tagTypes, tt -> tt.getName()));
                        }
                    };
                }
                
                throw new UserException("Cannot convert complex tagged type " + typeName.getRaw() + " to R");
            }

            @Override
            public RValue record(ImmutableMap<@ExpressionIdentifier String, DataType> types, GetValue<@Value Record> g) throws InternalException, UserException
            {
                throw new UserException("Cannot convert records to R");
            }

            @Override
            public RValue array(DataType inner, GetValue<@Value ListEx> g) throws InternalException, UserException
            {
                if (allowSubLists)
                {
                    ImmutableList.Builder<RValue> listOfLists = ImmutableList.builderWithExpectedSize(length);
                    for (int outer = 0; outer < length; outer++)
                    {
                        @Value ListEx outerValues = g.get(outer);
                        listOfLists.add(convertListToR(inner.fromCollapsed((innerIndex, prog) -> outerValues.get(innerIndex)), outerValues.size(), true));
                    }
                    return RUtility.genericVector(listOfLists.build(), null, false);
                }
                throw new UserException("Cannot convert lists to R");
            }
        });
    }

    static RValue temporalVector(DateTimeInfo dateTimeInfo, ImmutableList<Optional<@Value TemporalAccessor>> values)
    {
        return new RValue()
        {
            @Override
            public <T> T visit(RVisitor<T> visitor) throws InternalException, UserException
            {
                if (dateTimeInfo.getType() == DateTimeType.DATETIMEZONED && !values.isEmpty())
                {
                    // If no non-NA, won't matter:
                    ZoneId zone = values.stream().flatMap(v -> Utility.streamNullable(v.orElse(null))).findFirst().map(t -> ((ZonedDateTime) t).getZone()).orElse(ZoneId.systemDefault());

                    return visitor.visitTemporalList(dateTimeInfo.getType(), Utility.<Optional<@Value TemporalAccessor>, Optional<@Value TemporalAccessor>>mapListI(values, mv -> mv.<@Value TemporalAccessor>map(v -> DataTypeUtility.valueZonedDateTime(((ZonedDateTime) v).withZoneSameInstant(zone)))), RUtility.makeClassAttributes("POSIXct", ImmutableMap.of("tzone", RUtility.stringVector(DataTypeUtility.value(zone.toString())))));
                }
                else if (dateTimeInfo.getType() == DateTimeType.TIMEOFDAY)
                    return visitor.visitTemporalList(dateTimeInfo.getType(), values, null);
                else
                    return visitor.visitTemporalList(dateTimeInfo.getType(), values, RUtility.makeClassAttributes("Date", ImmutableMap.of()));
            }
        };
    }

    private static @Nullable @Value Integer getIfInteger(@Value Number n) throws InternalException
    {
        return Utility.<@Nullable @Value Integer>withNumberInt(n, l -> {
            if (l.longValue() != l.intValue())
                return null;
            return DataTypeUtility.value(l.intValue());
        }, bd -> {
            try
            {
                return DataTypeUtility.value(bd.intValueExact());
            }
            catch (ArithmeticException e)
            {
                return null;
            }
        });
    }
}
