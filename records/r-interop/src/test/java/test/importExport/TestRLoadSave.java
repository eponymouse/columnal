package test.importExport;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.ImmediateValue;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import records.data.Column;
import records.data.ColumnId;
import records.data.DataTestUtil;
import records.data.KnownLengthRecordSet;
import records.data.RecordSet;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.DataType.FlatDataTypeVisitor;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue.DataTypeVisitorGet;
import records.data.datatype.DataTypeValue.GetValue;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.rinterop.RData;
import records.rinterop.RData.RValue;
import utility.Either;
import utility.Pair;
import utility.TaggedValue;
import utility.Utility;
import utility.Utility.ListEx;
import utility.Utility.Record;

import java.io.File;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;

import static org.junit.Assert.assertEquals;

@RunWith(JUnitQuickcheck.class)
public class TestRLoadSave
{
    @Test
    public void testImportRData1() throws Exception
    {
        @SuppressWarnings("nullness")
        @NonNull URL resource = getClass().getClassLoader().getResource("iris.rds");
        RValue loaded = RData.readRData(new File(resource.toURI()));
        System.out.println(RData.prettyPrint(loaded));
        /*
            @Nullable InputStream stream = ResourceUtility.getResourceAsStream("builtin_units.txt");
            if (stream == null)
                return new UnitManager(null);
            else */
        TypeManager typeManager = new TypeManager(new UnitManager());
        RecordSet rs = RData.convertRToTable(typeManager, loaded).get(0);
                
        assertEquals(ImmutableList.of(new ColumnId("Sepal Length"), new ColumnId("Sepal Width"), new ColumnId("Petal Length"), new ColumnId("Petal Width"), new ColumnId("Species")), rs.getColumnIds());
        
        DataTestUtil.assertValueListEqual("Row 0", ImmutableList.of(d("5.1"), d("3.5"), d("1.4"), d("0.2"), typeManager.lookupTag("setosa 3", "setosa").getRight("Tag not found").makeTag(null)), DataTestUtil.getRowVals(rs, 0));

        DataTestUtil.assertValueListEqual("Row 149", ImmutableList.of(d("5.9"), d("3.0"), d("5.1"), d("1.8"), typeManager.lookupTag("setosa 3", "virginica").getRight("Tag not found").makeTag(null)), DataTestUtil.getRowVals(rs, 149));
    }

    @Test
    public void testImportRData2() throws Exception
    {
        @SuppressWarnings("nullness")
        @NonNull URL resource = getClass().getClassLoader().getResource("mtcars.rds");
        RValue loaded = RData.readRData(new File(resource.toURI()));
        System.out.println(RData.prettyPrint(loaded));
        /*
            @Nullable InputStream stream = ResourceUtility.getResourceAsStream("builtin_units.txt");
            if (stream == null)
                return new UnitManager(null);
            else */
        TypeManager typeManager = new TypeManager(new UnitManager());
        RecordSet rs = RData.convertRToTable(typeManager, loaded).get(0);

        assertEquals(ImmutableList.of(new ColumnId("mpg"), new ColumnId("cyl"), new ColumnId("disp"), new ColumnId("hp"), new ColumnId("drat"), new ColumnId("wt"), new ColumnId("qsec"), new ColumnId("vs"), new ColumnId("am"), new ColumnId("gear"), new ColumnId("carb")), rs.getColumnIds());

        DataTestUtil.assertValueListEqual("Row 0", ImmutableList.of(d("21.0"), d("6"), d("160.0"), d("110"), d("3.90"), d("2.620"), d("16.46"), d("0"), d("1"), d("4"), d("4")), DataTestUtil.getRowVals(rs, 0));

        //DataTestUtil.assertValueListEqual("Row 149", ImmutableList.of(d("5.9"), d("3.0"), d("5.1"), d("1.8"), typeManager.lookupTag("setosa 3", "virginica").getRight("Tag not found").makeTag(null)), DataTestUtil.getRowVals(rs, 149));
    }

    @Test
    public void testImportRData3() throws Exception
    {
        @SuppressWarnings("nullness")
        @NonNull URL resource = getClass().getClassLoader().getResource("aggr_results.Rdata");
        RValue loaded = RData.readRData(new File(resource.toURI()));
        System.out.println(RData.prettyPrint(loaded));
        /*
            @Nullable InputStream stream = ResourceUtility.getResourceAsStream("builtin_units.txt");
            if (stream == null)
                return new UnitManager(null);
            else */
        TypeManager typeManager = new TypeManager(new UnitManager());
        ImmutableList<RecordSet> rs = RData.convertRToTable(typeManager, loaded);

    }

    @Test
    public void testImportRData4() throws Exception
    {
        @SuppressWarnings("nullness")
        @NonNull URL resource = getClass().getClassLoader().getResource("date.rds");
        RValue loaded = RData.readRData(new File(resource.toURI()));
        System.out.println(RData.prettyPrint(loaded));
        Pair<DataType, @Value Object> r = RData.convertRToTypedValue(new TypeManager(new UnitManager()), loaded);
        assertEquals(DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY)), r.getFirst());
        DataTestUtil.assertValueEqual("Date", LocalDate.of(1950, 2, 1), r.getSecond());
    }

    @Test
    public void testImportRData5() throws Exception
    {
        @SuppressWarnings("nullness")
        @NonNull URL resource = getClass().getClassLoader().getResource("datetimezoned.rds");
        RValue loaded = RData.readRData(new File(resource.toURI()));
        System.out.println(RData.prettyPrint(loaded));
        Pair<DataType, @Value Object> r = RData.convertRToTypedValue(new TypeManager(new UnitManager()), loaded);
        assertEquals(DataType.date(new DateTimeInfo(DateTimeType.DATETIMEZONED)), r.getFirst());
        @SuppressWarnings("valuetype")
        @Value ZonedDateTime zdt = ZonedDateTime.of(2005, 10, 21, 18, 47, 22, 0, ZoneId.of("America/New_York"));
        DataTestUtil.assertValueEqual("DateTimeZoned", zdt, r.getSecond());
        assertEquals(zdt, r.getSecond());
    }

    @Test
    @Ignore
    public void testImportRData6() throws Exception
    {
        @SuppressWarnings("nullness")
        @NonNull URL resource = getClass().getClassLoader().getResource("mpfr.rds");
        RValue loaded = RData.readRData(new File(resource.toURI()));
        System.out.println(RData.prettyPrint(loaded));
        Pair<DataType, @Value Object> r = RData.convertRToTypedValue(new TypeManager(new UnitManager()), loaded);
        assertEquals(DataType.NUMBER, r.getFirst());
        DataTestUtil.assertValueEqual("Big number", DataTypeUtility.value(new BigDecimal("-92233720368547758085295")), r.getSecond());
    }
    
    @Property(trials = 100)
    public void testRoundTrip(@From(GenRCompatibleRecordSet.class) KnownLengthRecordSet original) throws Exception
    {
        // Need to get rid of any numbers which won't survive round trip:
        for (Column column : original.getColumns())
        {
            column.getType().applyGet(new DataTypeVisitorGet<@Nullable Void>()
            {
                int length = column.getLength();
                @Override
                public @Nullable Void number(GetValue<@Value Number> g, NumberInfo displayInfo) throws InternalException, UserException
                {
                    for (int i = 0; i < length; i++)
                    {
                        @Value Number orig = g.get(i);
                        double d = orig.doubleValue();
                        BigDecimal bd = new BigDecimal(d);
                        if (Utility.compareNumbers(orig, bd) != 0)
                        {
                            g.set(i, Either.<String, @Value Number>right(DataTypeUtility.<BigDecimal>value(bd)));
                        }
                    }
                    return null;
                }

                @Override
                public @Nullable Void text(GetValue<@Value String> g) throws InternalException, UserException
                {
                    // Make sure it can round trip:
                    for (int i = 0; i < length; i++)
                    {
                        g.set(i, Either.<String, @Value String>right(DataTypeUtility.value(new String(g.get(i).getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8))));
                    }
                    return null;
                }

                @Override
                public @Nullable Void bool(GetValue<@Value Boolean> g) throws InternalException, UserException
                {
                    return null;
                }

                @Override
                public @Nullable Void date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> g) throws InternalException, UserException
                {
                    // R's double doesn't have enough precision for nanos, so we must only keep what can round trip through a double-valued instant:
                    switch (dateTimeInfo.getType())
                    {
                        case TIMEOFDAY:
                            for (int i = 0; i < length; i++)
                            {
                                @Value TemporalAccessor orig = g.get(i);
                                double secs = (double)((LocalTime)orig).toNanoOfDay() / 1_000_000_000.0;
                                @SuppressWarnings("nullness")
                                @NonNull @ImmediateValue TemporalAccessor value = DataTypeUtility.value(dateTimeInfo, LocalTime.ofNanoOfDay((long) (Math.round(secs) * 1_000_000_000.0)));
                                g.set(i, Either.right(value));
                            }
                            break;
                        case DATETIMEZONED:
                        case DATETIME:
                            for (int i = 0; i < length; i++)
                            {
                                @Value TemporalAccessor orig = g.get(i);
                                Instant origInstant = makeInstant(orig);
                                double seconds = origInstant.getEpochSecond() + ((double)origInstant.getNano()) / 1_000_000_000.0;
                                if (dateTimeInfo.getType() == DateTimeType.DATETIME)
                                    seconds = Math.round(seconds / (60.0 * 60.0 * 24.0)) * (60.0 * 60.0 * 24.0);
                                // From https://stackoverflow.com/a/38544355/412908
                                double secondsRoundTowardsZero = Math.signum(seconds) * Math.floor(Math.abs(seconds));
                                Instant instantFromDouble = Instant.ofEpochSecond((long) secondsRoundTowardsZero, (long) (1_000_000_000.0 * (seconds - secondsRoundTowardsZero)));
                                @ImmediateValue TemporalAccessor value = DataTypeUtility.value(dateTimeInfo, dateTimeInfo.getType() == DateTimeType.DATETIMEZONED ? ZonedDateTime.ofInstant(instantFromDouble, ((ZonedDateTime) orig).getZone()) : LocalDateTime.ofInstant(instantFromDouble, ZoneId.of("UTC")));
                                if (value == null)
                                    throw new InternalException("Date cannot convert: " + orig);
                                g.set(i, Either.<String, @Value TemporalAccessor>right(value));
                            }
                            break;
                    }
                    
                    if (dateTimeInfo.getType() == DateTimeType.DATETIMEZONED && length > 1)
                    {
                        // R can only have one zone for the whole column,
                        // so to round trip we must do same:
                        ZoneId zoneId = ((ZonedDateTime)g.get(0)).getZone();
                        for (int i = 1; i < length; i++)
                        {
                            g.set(i, Either.<String, @Value TemporalAccessor>right(DataTypeUtility.valueZonedDateTime(((ZonedDateTime) g.get(i)).withZoneSameInstant(zoneId))));
                        }
                    }
                    return null;
                }

                private Instant makeInstant(@Value TemporalAccessor orig) throws InternalException
                {
                    if (orig instanceof ZonedDateTime)
                        return Instant.from(orig);
                    else if (orig instanceof LocalDateTime)
                        return Instant.from(((LocalDateTime)orig).atOffset(ZoneOffset.UTC));
                    else
                        throw new InternalException("Cannot make instant from " + orig.getClass());
                }

                @Override
                public @Nullable Void tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tagTypes, GetValue<@Value TaggedValue> g) throws InternalException, UserException
                {
                    return null;
                }

                @Override
                public @Nullable Void record(ImmutableMap<@ExpressionIdentifier String, DataType> types, GetValue<@Value Record> g) throws InternalException, UserException
                {
                    return null;
                }

                @Override
                public @Nullable Void array(DataType inner, GetValue<@Value ListEx> g) throws InternalException, UserException
                {
                    return null;
                }
            });
        }
        
        // We can only test us->R->us, because to test R->us->R we'd still need to convert at start and end (i.e. us->R->us->R->us which is the same).
        for (int kind = 0; kind < 3; kind++)
        {
            RValue tableAsR = RData.convertTableToR(original);
            final RValue roundTripped;
            switch (kind)
            {
                case 0:
                    roundTripped = tableAsR;
                    break;
                case 1:
                case 2:
                    File f = File.createTempFile("columnaltest", "rds");
                    RData.writeRData(f, tableAsR);
                    if (kind == 2)
                    {
                        // TODO load R to load and save
                    }
                    roundTripped = RData.readRData(f);
                    f.delete();
                    break;
                default:
                    Assert.fail("Missing case");
                    return;
            }
            RecordSet reloaded = RData.convertRToTable(new TypeManager(new UnitManager()), roundTripped).get(0);
            System.out.println(RData.prettyPrint(roundTripped));
            assertEquals(original.getColumnIds(), reloaded.getColumnIds());
            assertEquals(original.getLength(), reloaded.getLength());
            for (Column column : original.getColumns())
            {
                Column reloadedColumn = reloaded.getColumn(column.getName());
                for (int i = 0; i < original.getLength(); i++)
                {
                    final @Value Object reloadedVal = reloadedColumn.getType().getCollapsed(i);
                    // Not all date types survive, so need to coerce:
                    @Value Object reloadedValCoerced = column.getType().getType().apply(new FlatDataTypeVisitor<@Value Object>(reloadedVal)
                    {
                        @Override
                        @SuppressWarnings("nullness")
                        public @Value Object date(DateTimeInfo dateTimeInfo) throws InternalException, InternalException
                        {
                            if (dateTimeInfo.getType() == DateTimeType.TIMEOFDAY && !(reloadedVal instanceof LocalTime))
                            {
                                @Value BigDecimal bd = Utility.toBigDecimal((Number)reloadedVal);
                                return DataTypeUtility.value(dateTimeInfo, LocalTime.ofNanoOfDay(bd.multiply(new BigDecimal("1000000000")).longValue()));
                            }
                            return DataTypeUtility.value(dateTimeInfo, (TemporalAccessor) reloadedVal);
                        }
                    });
                    DataTestUtil.assertValueEqual("Row " + i + " column " + column.getName(), column.getType().getCollapsed(i), reloadedValCoerced);
                }
            }
        }
    }

    private static @Value BigDecimal d(String s)
    {
        // We go through double to replicate the R value exactly
        return DataTypeUtility.value(new BigDecimal(Double.parseDouble(s)));
    }


}
