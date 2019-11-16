package test.importExport;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.Assert;
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
import records.data.datatype.DataTypeUtility;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.rinterop.RData;
import records.rinterop.RData.RValue;
import utility.Pair;

import java.io.File;
import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;

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
    
    @Property(trials = 10)
    public void testRoundTrip(@When(seed=1L) @From(GenRCompatibleRecordSet.class) KnownLengthRecordSet original) throws Exception
    {
        // We can only test us->R->us, because to test R->us->R we'd still need to convert at start and end (i.e. us->R->us->R->us which is the same).
        File f = File.createTempFile("columnaltest", "rds");
        RData.writeRData(f, RData.convertTableToR(original));
        RecordSet reloaded = RData.convertRToTable(new TypeManager(new UnitManager()), RData.readRData(f)).get(0);
        f.delete();

        assertEquals(original.getColumnIds(), reloaded.getColumnIds());
        assertEquals(original.getLength(), reloaded.getLength());
        for (Column column : original.getColumns())
        {
            Column reloadedColumn = reloaded.getColumn(column.getName());
            for (int i = 0; i < original.getLength(); i++)
            {
                DataTestUtil.assertValueEqual("Row " + i + " column " + column.getName(), column.getType().getCollapsed(i), reloadedColumn.getType().getCollapsed(i));
            }
        }
        
        Assert.fail("TODO also load R in the interim to load and re-save ( separate test?");
    }

    private static @Value BigDecimal d(String s)
    {
        // We go through double to replicate the R value exactly
        return DataTypeUtility.value(new BigDecimal(Double.parseDouble(s)));
    }


}
