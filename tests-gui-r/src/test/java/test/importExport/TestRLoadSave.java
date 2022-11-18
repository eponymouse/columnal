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

package test.importExport;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.ImmediateValue;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.apache.commons.io.IOUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import xyz.columnal.data.Column;
import xyz.columnal.data.datatype.ProgressListener;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.data.EditableRecordSet;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.DateTimeInfo.DateTimeType;
import xyz.columnal.data.datatype.DataType.FlatDataTypeVisitor;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.DataTypeValue.DataTypeVisitorGet;
import xyz.columnal.data.datatype.DataTypeValue.GetValue;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.TaggedTypeDefinition;
import xyz.columnal.data.datatype.TaggedTypeDefinition.TaggedInstantiationException;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.jellytype.JellyType.UnknownTypeException;
import xyz.columnal.rinterop.ConvertToR;
import xyz.columnal.rinterop.ConvertFromR;
import xyz.columnal.rinterop.RPrettyPrint;
import xyz.columnal.rinterop.RRead;
import xyz.columnal.rinterop.RValue;
import xyz.columnal.rinterop.ConvertFromR.TableType;
import xyz.columnal.rinterop.RWrite;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.TaggedValue;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;
import xyz.columnal.utility.Utility.Record;
import xyz.columnal.utility.Utility.RecordMap;

import java.io.File;
import java.io.StringWriter;
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
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

@RunWith(JUnitQuickcheck.class)
public class TestRLoadSave
{
    @Test
    public void testImportRData1() throws Exception
    {
        @SuppressWarnings("nullness")
        @NonNull URL resource = getClass().getClassLoader().getResource("iris.rds");
        RValue loaded = RRead.readRData(new File(resource.toURI()));
        System.out.println(RPrettyPrint.prettyPrint(loaded));
        /*
            @Nullable InputStream stream = ResourceUtility.getResourceAsStream("builtin_units.txt");
            if (stream == null)
                return new UnitManager(null);
            else */
        TypeManager typeManager = new TypeManager(new UnitManager());
        RecordSet rs = ConvertFromR.convertRToTable(typeManager, loaded, false).get(0).getSecond();
                
        assertEquals(ImmutableList.of(new ColumnId("Sepal Length"), new ColumnId("Sepal Width"), new ColumnId("Petal Length"), new ColumnId("Petal Width"), new ColumnId("Species")), rs.getColumnIds());
        
        TBasicUtil.assertValueListEqual("Row 0", ImmutableList.of(d("5.1"), d("3.5"), d("1.4"), d("0.2"), typeManager.lookupTag("setosa 3", "setosa").getRight("Tag not found").makeTag(null)), getRowVals(rs, 0));

        TBasicUtil.assertValueListEqual("Row 149", ImmutableList.of(d("5.9"), d("3.0"), d("5.1"), d("1.8"), typeManager.lookupTag("setosa 3", "virginica").getRight("Tag not found").makeTag(null)), getRowVals(rs, 149));
    }

    @Test
    public void testImportRData2() throws Exception
    {
        @SuppressWarnings("nullness")
        @NonNull URL resource = getClass().getClassLoader().getResource("mtcars.rds");
        RValue loaded = RRead.readRData(new File(resource.toURI()));
        System.out.println(RPrettyPrint.prettyPrint(loaded));
        /*
            @Nullable InputStream stream = ResourceUtility.getResourceAsStream("builtin_units.txt");
            if (stream == null)
                return new UnitManager(null);
            else */
        TypeManager typeManager = new TypeManager(new UnitManager());
        RecordSet rs = ConvertFromR.convertRToTable(typeManager, loaded, false).get(0).getSecond();

        assertEquals(ImmutableList.of(new ColumnId("mpg"), new ColumnId("cyl"), new ColumnId("disp"), new ColumnId("hp"), new ColumnId("drat"), new ColumnId("wt"), new ColumnId("qsec"), new ColumnId("vs"), new ColumnId("am"), new ColumnId("gear"), new ColumnId("carb")), rs.getColumnIds());

        TBasicUtil.assertValueListEqual("Row 0", ImmutableList.of(d("21.0"), d("6"), d("160.0"), d("110"), d("3.90"), d("2.620"), d("16.46"), d("0"), d("1"), d("4"), d("4")), getRowVals(rs, 0));

        //DataTestUtil.assertValueListEqual("Row 149", ImmutableList.of(d("5.9"), d("3.0"), d("5.1"), d("1.8"), typeManager.lookupTag("setosa 3", "virginica").getRight("Tag not found").makeTag(null)), DataTestUtil.getRowVals(rs, 149));
    }

    @Ignore // Empty tag name issue
    @Test
    public void testImportRData3() throws Exception
    {
        @SuppressWarnings("nullness")
        @NonNull URL resource = getClass().getClassLoader().getResource("aggr_results.Rdata");
        RValue loaded = RRead.readRData(new File(resource.toURI()));
        System.out.println(RPrettyPrint.prettyPrint(loaded));
        /*
            @Nullable InputStream stream = ResourceUtility.getResourceAsStream("builtin_units.txt");
            if (stream == null)
                return new UnitManager(null);
            else */
        TypeManager typeManager = new TypeManager(new UnitManager());
        ConvertFromR.convertRToTable(typeManager, loaded, false);
    }

    @Test
    public void testImportRData4() throws Exception
    {
        @SuppressWarnings("nullness")
        @NonNull URL resource = getClass().getClassLoader().getResource("date.rds");
        RValue loaded = RRead.readRData(new File(resource.toURI()));
        System.out.println(RPrettyPrint.prettyPrint(loaded));
        Pair<DataType, ImmutableList<@Value Object>> r = ConvertFromR.convertRToTypedValueList(new TypeManager(new UnitManager()), loaded);
        assertEquals(DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY)), r.getFirst());
        TBasicUtil.assertValueEqual("Date", LocalDate.of(1950, 2, 1), r.getSecond().get(0));
    }

    @Test
    public void testImportRData5() throws Exception
    {
        @SuppressWarnings("nullness")
        @NonNull URL resource = getClass().getClassLoader().getResource("datetimezoned.rds");
        RValue loaded = RRead.readRData(new File(resource.toURI()));
        System.out.println(RPrettyPrint.prettyPrint(loaded));
        Pair<DataType, ImmutableList<@Value Object>> r = ConvertFromR.convertRToTypedValueList(new TypeManager(new UnitManager()), loaded);
        assertEquals(DataType.date(new DateTimeInfo(DateTimeType.DATETIMEZONED)), r.getFirst());
        @SuppressWarnings("valuetype")
        @Value ZonedDateTime zdt = ZonedDateTime.of(2005, 10, 21, 18, 47, 22, 0, ZoneId.of("America/New_York"));
        TBasicUtil.assertValueEqual("DateTimeZoned", zdt, r.getSecond().get(0));
        assertEquals(zdt, r.getSecond().get(0));
    }

    @Test
    @SuppressWarnings("valuetype")
    public void testImportRData6() throws Exception
    {
        @SuppressWarnings("nullness")
        @NonNull URL resource = getClass().getClassLoader().getResource("na.rds");
        RValue loaded = RRead.readRData(new File(resource.toURI()));
        System.out.println(RPrettyPrint.prettyPrint(loaded));
        TypeManager typeManager = new TypeManager(new UnitManager());
        RecordSet r = ConvertFromR.convertRToTable(typeManager, loaded, false).get(0).getSecond();
        // df <- data.frame(c(TRUE, NA, FALSE), c(36, NA, -35.2), c(1, NA, 2), factor(c("A", NA, "B")), as.character(c("Hello", NA, "Bye")), c(ISOdate(2005,10,21,18,47,22,tz="America/New_York"), NA, NA), stringsAsFactors=FALSE)
        
        assertEquals(maybeType(typeManager, DataType.BOOLEAN), r.getColumns().get(0).getType().getType());
        TBasicUtil.assertValueListEqual("Bool column", ImmutableList.of(new TaggedValue(1, true, typeManager.getMaybeType()), new TaggedValue(0, null, typeManager.getMaybeType()), new TaggedValue(1, false, typeManager.getMaybeType())), asList(r.getColumns().get(0)));

        assertEquals(maybeType(typeManager, DataType.NUMBER), r.getColumns().get(1).getType().getType());
        TBasicUtil.assertValueListEqual("Double column", ImmutableList.of(new TaggedValue(1, 36, typeManager.getMaybeType()), new TaggedValue(0, null, typeManager.getMaybeType()), new TaggedValue(1, new BigDecimal("-35.2"), typeManager.getMaybeType())), asList(r.getColumns().get(1)));

        assertEquals(maybeType(typeManager, DataType.NUMBER), r.getColumns().get(2).getType().getType());
        TBasicUtil.assertValueListEqual("Int column", ImmutableList.of(new TaggedValue(1, 1, typeManager.getMaybeType()), new TaggedValue(0, null, typeManager.getMaybeType()), new TaggedValue(1, new BigDecimal(2), typeManager.getMaybeType())), asList(r.getColumns().get(2)));

        @SuppressWarnings("nullness")
        @NonNull TaggedTypeDefinition taggedTypeDefinition = typeManager.lookupDefinition(new TypeId("A 2"));
        assertEquals(maybeType(typeManager, taggedTypeDefinition.instantiate(ImmutableList.of(), typeManager)), r.getColumns().get(3).getType().getType());
        TBasicUtil.assertValueListEqual("Factor column", ImmutableList.of(typeManager.maybePresent(new TaggedValue(0, null, taggedTypeDefinition)), typeManager.maybeMissing(), typeManager.maybePresent(new TaggedValue(1, null, taggedTypeDefinition))), asList(r.getColumns().get(3)));

        assertEquals(maybeType(typeManager, DataType.date(new DateTimeInfo(DateTimeType.DATETIME))), r.getColumns().get(5).getType().getType());
        TBasicUtil.assertValueListEqual("Date column", ImmutableList.of(typeManager.maybePresent(LocalDateTime.of(2005, 10, 21, 22, 47, 22, 0)), typeManager.maybeMissing(), typeManager.maybeMissing()), asList(r.getColumns().get(5)));
    }

    @Test
    public void testImportRData7() throws Exception
    {
        @SuppressWarnings("nullness")
        @NonNull URL resource = getClass().getClassLoader().getResource("df-df2.Rdata");
        RValue loaded = RRead.readRData(new File(resource.toURI()));
        System.out.println(RPrettyPrint.prettyPrint(loaded));
        TypeManager typeManager = new TypeManager(new UnitManager());
        ImmutableList<Pair<String, EditableRecordSet>> rs = ConvertFromR.convertRToTable(typeManager, loaded, true);
        assertEquals(ImmutableSet.of("df", "df2", "datetimeZoned2"), rs.stream().map(p -> p.getFirst()).collect(ImmutableSet.<String>toImmutableSet()));
    }

    @Test
    public void testImportRData8() throws Exception
    {
        @SuppressWarnings("nullness")
        @NonNull URL resource = getClass().getClassLoader().getResource("simple345.rds");
        RValue loaded = RRead.readRData(new File(resource.toURI()));
        System.out.println(RPrettyPrint.prettyPrint(loaded));
        TypeManager typeManager = new TypeManager(new UnitManager());
        RecordSet rs = ConvertFromR.convertRToTable(typeManager, loaded, false).get(0).getSecond();

        assertEquals(ImmutableList.of(new ColumnId("bar")), rs.getColumnIds());

    }

    @Test
    @SuppressWarnings("valuetype")
    public void testImportRData9() throws Exception
    {
        @SuppressWarnings("nullness")
        @NonNull URL resource = getClass().getClassLoader().getResource("tibble.rds");
        RValue loaded = RRead.readRData(new File(resource.toURI()));
        System.out.println(RPrettyPrint.prettyPrint(loaded));
        TypeManager typeManager = new TypeManager(new UnitManager());
        RecordSet rs = ConvertFromR.convertRToTable(typeManager, loaded, false).get(0).getSecond();

        assertEquals(ImmutableList.of(new ColumnId("x"), new ColumnId("nested lists")), rs.getColumnIds());

        // t <- tibble(x = 1:3, "nested lists" = list(1:5, 1:10, 20:5))
        TBasicUtil.assertValueListEqual("Row 0", ImmutableList.<@Value Object>of(DataTypeUtility.value(1), ints(1,2,3,4,5)), getRowVals(rs, 0));
        TBasicUtil.assertValueListEqual("Row 2", ImmutableList.<@Value Object>of(DataTypeUtility.value(3), ints(20,19,18,17,16,15,14,13,12,11,10,9,8,7,6,5)), getRowVals(rs, 2));
    }

    private Object ints(int... values)
    {
        return DataTypeUtility.valueImmediate(IntStream.of(values).<@ImmediateValue Object>mapToObj(i -> DataTypeUtility.value(i)).collect(ImmutableList.<@ImmediateValue Object>toImmutableList()));
    }

    private ImmutableList<@Value Object> asList(Column column) throws InternalException, UserException
    {
        ImmutableList.Builder<@Value Object> r = ImmutableList.builderWithExpectedSize(column.getLength());
        for (int i = 0; i < column.getLength(); i++)
        {
            r.add(column.getType().getCollapsed(i));
        }
        return r.build();
    }

    private DataType maybeType(TypeManager typeManager, DataType dataType) throws TaggedInstantiationException, InternalException, UnknownTypeException
    {
        return typeManager.getMaybeType().instantiate(ImmutableList.<Either<Unit, DataType>>of(Either.<Unit, DataType>right(dataType)), typeManager);
    }

    @Property(trials = 100)
    public void testRoundTrip(@From(GenRCompatibleRecordSet.class) GenRCompatibleRecordSet.RCompatibleRecordSet original) throws Exception
    {
        // Need to get rid of any numbers which won't survive round trip:
        for (Column column : original.recordSet.getColumns())
        {
            column.getType().applyGet(new EnsureRoundTrip(column.getLength()));
        }
        
        // We can only test us->R->us, because to test R->us->R we'd still need to convert at start and end (i.e. us->R->us->R->us which is the same).
        for (TableType tableType : original.supportedTableTypes)
        {
            for (int kind = 0; kind < 3; kind++)
            {
                RValue tableAsR = ConvertToR.convertTableToR(original.recordSet, tableType);
                final RValue roundTripped;
                switch (kind)
                {
                    case 0:
                        roundTripped = tableAsR;
                        break;
                    case 1:
                    case 2:
                        File f = File.createTempFile("columnaltest", "rds");
                        RWrite.writeRData(f, tableAsR);
                        if (kind == 2)
                        {
                            long lenBefore = f.length();
                            String[] command = new String[]{"R", "-e", "'saveRDS(readRDS(\"" + f.getAbsolutePath().replace("\\", "\\\\") + "\"),\"" + f.getAbsolutePath().replace("\\", "\\\\") + "\");'"};
                            Process process = Runtime.getRuntime().exec(command);
                            StringWriter stdout = new StringWriter();
                            IOUtils.copy(process.getInputStream(), stdout, StandardCharsets.UTF_8);
                            StringWriter stderr = new StringWriter();
                            IOUtils.copy(process.getErrorStream(), stderr, StandardCharsets.UTF_8);
                            assertEquals("stdout: " + stdout.toString() + "stderr: " + stderr.toString(), 0, process.waitFor());
                            long lenAfter = f.length();
                            System.out.println("Length before R: " + lenBefore + " after: " + lenAfter);
                        }
                        roundTripped = RRead.readRData(f);
                        f.delete();
                        break;
                    default:
                        Assert.fail("Missing case");
                        return;
                }
                TypeManager typeManager = original.typeManager;
                System.out.println(RPrettyPrint.prettyPrint(roundTripped));
                RecordSet reloaded = ConvertFromR.convertRToTable(typeManager, roundTripped, false).get(0).getSecond();
                assertEquals(original.recordSet.getColumnIds(), reloaded.getColumnIds());
                assertEquals(original.recordSet.getLength(), reloaded.getLength());
                for (Column column : original.recordSet.getColumns())
                {
                    Column reloadedColumn = reloaded.getColumn(column.getName());

                    // Can't do this because it may need coercing:
                    //assertEquals(getTypeName(column.getType().getType()), getTypeName(reloadedColumn.getType().getType()));

                    for (int i = 0; i < original.recordSet.getLength(); i++)
                    {
                        final @Value Object reloadedVal = reloadedColumn.getType().getCollapsed(i);
                        // Not all date types survive, so need to coerce:
                        @Value Object reloadedValCoerced = column.getType().getType().apply(new CoerceValueToThisType(reloadedVal, typeManager));
                        TBasicUtil.assertValueEqual("Row " + i + " column " + column.getName(), column.getType().getCollapsed(i), reloadedValCoerced);
                    }
                }
            }
        }
    }

    private @Nullable String getTypeName(DataType type) throws InternalException
    {
        return type.apply(new FlatDataTypeVisitor<@Nullable String>(null) {
            @Override
            public @Nullable String tagged(TypeId typeName, ImmutableList typeVars, ImmutableList tags) throws InternalException, InternalException
            {
                return typeName.getRaw();
            }
        });
    }

    private static @Value BigDecimal d(String s)
    {
        return DataTypeUtility.value(new BigDecimal(s));
    }


    @OnThread(value = Tag.Simulation, ignoreParent = true)
    private static class EnsureRoundTrip implements DataTypeVisitorGet<@Nullable Void>
    {
        private final int length;

        public EnsureRoundTrip(int length)
        {
            this.length = length;
        }

        @Override
        public @Nullable Void number(GetValue<@Value Number> g, NumberInfo displayInfo) throws InternalException, UserException
        {
            for (int i = 0; i < length; i++)
            {
                @Value Number orig = g.get(i);
                double d = orig.doubleValue();
                BigDecimal bd = new BigDecimal(Double.toString(d));
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
            if (typeName.getRaw().equals("Optional"))
            {
                ArrayList<Pair<Integer, @Value Object>> inners = new ArrayList<>();
                for (int i = 0; i < length; i++)
                {
                    @Value TaggedValue taggedValue = g.get(i);
                    if (taggedValue.getInner() != null)
                        inners.add(new Pair<>(i, taggedValue.getInner()));
                }
                
                TypeManager typeManager = new TypeManager(new UnitManager());
                return typeVars.get(0).getRight("Err").fromCollapsed(new GetValue<@Value Object>()
                {
                    @Override
                    public @Value Object getWithProgress(int i, @Nullable ProgressListener prog) throws UserException, InternalException
                    {
                        return inners.get(i).getSecond();
                    }

                    @Override
                    public void set(int index, Either<String, @Value Object> value) throws InternalException, UserException
                    {
                        g.set(inners.get(index).getFirst(), Either.right(typeManager.maybePresent(value.getRight("Err"))));
                    }
                }).applyGet(new EnsureRoundTrip(inners.size()));
            }
            return null;
        }

        @Override
        public @Nullable Void record(ImmutableMap<@ExpressionIdentifier String, DataType> types, GetValue<@Value Record> g) throws InternalException, UserException
        {
            for (Entry<@ExpressionIdentifier String, DataType> fieldType : types.entrySet())
            {
                fieldType.getValue().fromCollapsed(new GetValue<@Value Object>()
                {
                    @Override
                    public @Value Object getWithProgress(int i, @Nullable ProgressListener prog) throws UserException, InternalException
                    {
                        return g.get(i).getField(fieldType.getKey());
                    }

                    @Override
                    public void set(int i, Either<String, @Value Object> value) throws InternalException, UserException
                    {
                        g.set(i, Either.<String, @Value Record>right(DataTypeUtility.value(new RecordMap(Utility.appendToMap(g.get(i).getFullContent(), fieldType.getKey(), value.getRight("Setting error"), null)))));
                    }
                }).applyGet(new EnsureRoundTrip(length));
            }
            return null;
        }

        @Override
        public @Nullable Void array(DataType inner, GetValue<@Value ListEx> g) throws InternalException, UserException
        {
            for (int i = 0; i < length; i++)
            {
                ListEx listEx = g.get(i);
                boolean[] anyModified = new boolean[] {false};
                ArrayList<@Value Object> modified = new ArrayList<>();
                for (int j = 0; j < listEx.size(); j++)
                {
                    modified.add(listEx.get(j));
                }
                inner.fromCollapsed(new GetValue<@Value Object>()
                {
                    @Override
                    public @NonNull @Value Object getWithProgress(int listIndex, @Nullable ProgressListener prog) throws UserException, InternalException
                    {
                        return modified.get(listIndex);
                    }

                    @Override
                    public void set(int index, Either<String, @Value Object> value) throws InternalException, UserException
                    {
                        modified.set(index, value.getRight("No errs"));
                        anyModified[0] = true;
                    }
                }).applyGet(new EnsureRoundTrip(listEx.size()));
                // Single item lists don't survive the trip, so double up:
                if (modified.size() == 1)
                {
                    modified.add(modified.get(0));
                    anyModified[0] = true;
                }
                
                if (anyModified[0])
                    g.set(i, Either.right(DataTypeUtility.value(modified)));
                
                
            }
            
            
            return null;
        }
    }

    private static class CoerceValueToThisType extends FlatDataTypeVisitor<@Value Object>
    {
        private final @Value Object reloadedVal;
        private final TypeManager typeManager;

        public CoerceValueToThisType(@Value Object reloadedVal, TypeManager typeManager)
        {
            super(reloadedVal);
            this.reloadedVal = reloadedVal;
            this.typeManager = typeManager;
        }

        @Override
        @SuppressWarnings("nullness")
        @OnThread(value = Tag.Simulation, ignoreParent = true)
        public @Value Object date(DateTimeInfo dateTimeInfo) throws InternalException, InternalException
        {
            if (dateTimeInfo.getType() == DateTimeType.TIMEOFDAY && !(reloadedVal instanceof LocalTime))
            {
                @Value BigDecimal bd = Utility.toBigDecimal((Number) reloadedVal);
                return DataTypeUtility.value(dateTimeInfo, LocalTime.ofNanoOfDay(bd.multiply(new BigDecimal("1000000000")).longValue()));
            }
            return DataTypeUtility.value(dateTimeInfo, (TemporalAccessor) reloadedVal);
        }

        @Override
        @SuppressWarnings("nullness")
        @OnThread(value = Tag.Simulation, ignoreParent = true)
        public @Value Object tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, InternalException
        {
            if (typeName.getRaw().equals("Optional") && !(reloadedVal instanceof TaggedValue))
                return typeManager.maybePresent(tags.get(1).getInner().apply(this));
            else
            {
                @Value TaggedValue taggedValue = (TaggedValue) this.reloadedVal;
                if (typeName.getRaw().equals("Optional") && taggedValue.getTagIndex() == 1)
                    return typeManager.maybePresent(tags.get(1).getInner().apply(new CoerceValueToThisType(taggedValue.getInner(), typeManager)));
                else
                    return taggedValue;
            }
        }

        @Override
        @OnThread(value = Tag.Simulation, ignoreParent = true)
        public @Value Object record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, InternalException
        {
            ImmutableMap.Builder<@ExpressionIdentifier String, @Value Object> mappedValues = ImmutableMap.builder();
            for (Entry<@ExpressionIdentifier String, @Value Object> field : Utility.cast(reloadedVal, Record.class).getFullContent().entrySet())
            {
                @SuppressWarnings("nullness") // This is test code
                @NonNull DataType fieldType = fields.get(field.getKey());
                mappedValues.put(field.getKey(), fieldType.apply(new CoerceValueToThisType(field.getValue(), typeManager)));
            }
            return DataTypeUtility.value(new RecordMap(mappedValues.build()));
        }

        @Override
        @OnThread(value = Tag.Simulation, ignoreParent = true)
        public @Value Object array(DataType inner) throws InternalException, InternalException
        {
            try
            {
                @Value ListEx list;
                if (reloadedVal instanceof ListEx)
                    list = Utility.cast(reloadedVal, ListEx.class);
                else
                    list = DataTypeUtility.value(ImmutableList.<@Value Object>of(reloadedVal));
                int length = list.size();
                ImmutableList.Builder<@Value Object> coercedList = ImmutableList.builderWithExpectedSize(length);
                for (int i = 0; i < length; i++)
                {
                    coercedList.add(inner.apply(new CoerceValueToThisType(list.get(i), typeManager)));
                }
                return DataTypeUtility.value(coercedList.build());
            }
            catch (UserException e)
            {
                // Fine because it's test code:
                throw new InternalException("UserEx", e);
            }
        }
    }

    @OnThread(Tag.Simulation)
    public static ImmutableList<@Value Object> getRowVals(RecordSet recordSet, int targetRow)
    {
        return recordSet.getColumns().stream().<@Value Object>map(c -> {
            try
            {
                return c.getType().getCollapsed(targetRow);
            }
            catch (InternalException | UserException e)
            {
                throw new RuntimeException(e);
            }
        }).collect(ImmutableList.<@Value Object>toImmutableList());
    }
}
