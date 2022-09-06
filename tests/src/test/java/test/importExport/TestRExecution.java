package test.importExport;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.hamcrest.Matchers;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import records.data.Column;
import records.data.ColumnId;
import records.data.DataTestUtil;
import records.data.EditableColumn;
import records.data.KnownLengthRecordSet;
import records.data.MemoryArrayColumn;
import records.data.MemoryNumericColumn;
import records.data.MemoryStringColumn;
import records.data.RecordSet;
import records.data.datatype.DataType;
import records.data.datatype.DataType.SpecificDataTypeVisitor;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import records.rinterop.ConvertFromR;
import records.rinterop.RPrettyPrint;
import records.rinterop.RValue;
import records.rinterop.RExecution;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.Pair;
import xyz.columnal.utility.SimulationFunction;
import xyz.columnal.utility.TaggedValue;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;
import xyz.columnal.utility.Utility.RecordMap;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

@SuppressWarnings("valuetype")
@RunWith(JUnitQuickcheck.class)
public class TestRExecution
{
    @Test
    public void testSimple() throws UserException, InternalException
    {
        TypeManager typeManager = new TypeManager(new UnitManager());
        Column column = ConvertFromR.convertRToTable(typeManager, RExecution.runRExpression("c(6, 8)"), false).get(0).getSecond().getColumns().get(0);
        DataTestUtil.assertValueListEqual("Column", ImmutableList.of(6, 8), DataTestUtil.getAllCollapsedDataValid(column.getType(), column.getLength()));
    }
    
    @Test
    public void testSimple2() throws UserException, InternalException
    {
        TypeManager typeManager = new TypeManager(new UnitManager());
        Column column = ConvertFromR.convertRToTable(typeManager, RExecution.runRExpression("seq(1,10,2)"), false).get(0).getSecond().getColumns().get(0);
        DataTestUtil.assertValueListEqual("Column", ImmutableList.of(1, 3, 5, 7, 9), DataTestUtil.getAllCollapsedDataValid(column.getType(), column.getLength()));
    }

    @Test
    public void testSimple3() throws UserException, InternalException
    {
        TypeManager typeManager = new TypeManager(new UnitManager());
        Column column = ConvertFromR.convertRToTable(typeManager, RExecution.runRExpression("\"Möøõsę!\""), false).get(0).getSecond().getColumns().get(0);
        DataTestUtil.assertValueListEqual("Column", ImmutableList.of("Möøõsę!"), DataTestUtil.getAllCollapsedDataValid(column.getType(), column.getLength()));
    }

    @Test
    public void testSimple4() throws UserException, InternalException
    {
        TypeManager typeManager = new TypeManager(new UnitManager());
        Column column = ConvertFromR.convertRToTable(typeManager, RExecution.runRExpression("4611686018427387904L"), false).get(0).getSecond().getColumns().get(0);
        DataTestUtil.assertValueListEqual("Column", ImmutableList.of(new BigDecimal("4611686018427387900")), DataTestUtil.getAllCollapsedDataValid(column.getType(), column.getLength()));
    }

    @SuppressWarnings("valuetype")
    @Test
    public void testRecord() throws UserException, InternalException
    {
        TypeManager typeManager = new TypeManager(new UnitManager());
        Column column = ConvertFromR.convertRToTable(typeManager, RExecution.runRExpression("list(x=5, y= 7)"), false).get(0).getSecond().getColumns().get(0);
        DataTestUtil.assertValueListEqual("Column", ImmutableList.of(new RecordMap(ImmutableMap.<@ExpressionIdentifier String, @Value Object>of("x", 5, "y", 7))), DataTestUtil.getAllCollapsedDataValid(column.getType(), column.getLength()));
    }

    @SuppressWarnings("valuetype")
    @Test
    public void testRecord2() throws UserException, InternalException
    {
        TypeManager typeManager = new TypeManager(new UnitManager());
        ImmutableList<@Value Object> expected = ImmutableList.of(new RecordMap(ImmutableMap.<@ExpressionIdentifier String, @Value Object>of("x", 5, "y", 7)), new RecordMap(ImmutableMap.<@ExpressionIdentifier String, @Value Object>of("x", new BigDecimal("1.2"), "y", -3)));
        Column column = ConvertFromR.convertRToTable(typeManager, RExecution.runRExpression("list(list(x=5, y= 7), list(x=1.2, y= -3))"), false).get(0).getSecond().getColumns().get(0);
        DataTestUtil.assertValueListEqual("Column", expected, DataTestUtil.getAllCollapsedDataValid(column.getType(), column.getLength()));
    }
    
    @SuppressWarnings("valuetype")
    @Test
    public void testRecord3() throws UserException, InternalException
    {
        TypeManager typeManager = new TypeManager(new UnitManager());
        ImmutableList<@Value Object> expected = ImmutableList.of(new RecordMap(ImmutableMap.<@ExpressionIdentifier String, @Value Object>of("x", 5, "y", typeManager.maybePresent(7), "z", typeManager.maybeMissing())), new RecordMap(ImmutableMap.<@ExpressionIdentifier String, @Value Object>of("x", new BigDecimal("1.2"), "y", typeManager.maybeMissing(), "z", typeManager.maybePresent(-3))));
        Column column = ConvertFromR.convertRToTable(typeManager, RExecution.runRExpression("list(list(x=5, y= 7), list(x=1.2, z = -3))"), false).get(0).getSecond().getColumns().get(0);
        DataTestUtil.assertValueListEqual("Column", expected, DataTestUtil.getAllCollapsedDataValid(column.getType(), column.getLength()));
    }
    
    @Test
    public void testAIC() throws InternalException, UserException
    {
        TypeManager typeManager = new TypeManager(new UnitManager());
        RecordSet recordSet = ConvertFromR.convertRToTable(typeManager, RExecution.runRExpression(
        // From docs
                "lm1 <- lm(Fertility ~ . , data = swiss)\n" +
                "AIC(lm1)\n" +
                "stopifnot(all.equal(AIC(lm1),\n" +
                "                    AIC(logLik(lm1))))\n" +
                "BIC(lm1)\n" +
                "\n" +
                "lm2 <- update(lm1, . ~ . -Examination)\n" +
                "AIC(lm1, lm2)"
        , ImmutableList.of("stats"), ImmutableMap.of()), false).get(0).getSecond();
        assertEquals(ImmutableList.of(new ColumnId("df"), new ColumnId("AIC")), recordSet.getColumnIds());
        assertEquals(ImmutableList.of(new BigDecimal("326.0715684405487"), new BigDecimal("325.2408440639819")), DataTestUtil.getAllCollapsedDataValid(recordSet.getColumn(new ColumnId("AIC")).getType(), recordSet.getLength()));
    }

    @Test
    public void testTable() throws InternalException, UserException
    {
        TypeManager typeManager = new TypeManager(new UnitManager());
        RecordSet recordSet = ConvertFromR.convertRToTable(typeManager, RExecution.runRExpression("foo$bar[2:3]", ImmutableList.of(), ImmutableMap.of("foo", new <EditableColumn>KnownLengthRecordSet(ImmutableList.<SimulationFunction<RecordSet, EditableColumn>>of(rs -> new MemoryNumericColumn(rs, new ColumnId("bar"), NumberInfo.DEFAULT, Stream.of("3", "4", "5"))), 3))), false).get(0).getSecond();
        DataTestUtil.assertValueListEqual("Column", ImmutableList.of(4, 5), DataTestUtil.getAllCollapsedDataValid(recordSet.getColumns().get(0).getType(), recordSet.getLength()));
    }

    @SuppressWarnings("valuetype")
    @Test
    public void testTable2() throws InternalException, UserException
    {
        TypeManager typeManager = new TypeManager(new UnitManager());
        RecordSet recordSet = ConvertFromR.convertRToTable(typeManager, RExecution.runRExpression("foo$baz[2:3]", ImmutableList.of(),
            ImmutableMap.of("foo", new <EditableColumn>KnownLengthRecordSet(ImmutableList.<SimulationFunction<RecordSet, EditableColumn>>of(
                rs -> new MemoryNumericColumn(rs, new ColumnId("bar"), NumberInfo.DEFAULT, Stream.of("3", "4", "5")),
                rs -> new MemoryStringColumn(rs, new ColumnId("baz"), ImmutableList.of(Either.<String, @Value String>right("A"), Either.<String, @Value String>right("B"), Either.<String, @Value String>right("C")), "Z")
            ), 3))), false).get(0).getSecond();
        DataTestUtil.assertValueListEqual("Column", ImmutableList.of("B", "C"), DataTestUtil.getAllCollapsedDataValid(recordSet.getColumns().get(0).getType(), recordSet.getLength()));
    }

    @SuppressWarnings("valuetype")
    @Test
    public void testTable2b() throws InternalException, UserException
    {
        TypeManager typeManager = new TypeManager(new UnitManager());
        RecordSet recordSet = ConvertFromR.convertRToTable(typeManager, RExecution.runRExpression("data.frame(foo)$baz[2:3]", ImmutableList.of(),
            ImmutableMap.of("foo", new <EditableColumn>KnownLengthRecordSet(ImmutableList.<SimulationFunction<RecordSet, EditableColumn>>of(
                rs -> new MemoryNumericColumn(rs, new ColumnId("bar"), NumberInfo.DEFAULT, Stream.of("3", "4", "5")),
                rs -> new MemoryStringColumn(rs, new ColumnId("baz"), ImmutableList.of(Either.<String, @Value String>right("A"), Either.<String, @Value String>right("B"), Either.<String, @Value String>right("C")), "Z")
            ), 3))), false).get(0).getSecond();
        DataTestUtil.assertValueListEqual("Column", ImmutableList.of("B", "C"), DataTestUtil.getAllCollapsedDataValid(recordSet.getColumns().get(0).getType(), recordSet.getLength()));
    }

    // Test tibble features: spaces in column names, lists in cells
    @SuppressWarnings("valuetype")
    @Test
    public void testTable3() throws InternalException, UserException
    {
        TypeManager typeManager = new TypeManager(new UnitManager());
        RecordSet recordSet = ConvertFromR.convertRToTable(typeManager, RExecution.runRExpression("foo$\"bar bar black sheep\"[2:3]", ImmutableList.of(),
            ImmutableMap.of("foo", new <EditableColumn>KnownLengthRecordSet(ImmutableList.<SimulationFunction<RecordSet, EditableColumn>>of(
                rs -> new MemoryArrayColumn(rs, new ColumnId("bar bar black sheep"), DataType.NUMBER, ImmutableList.of(numberList("3"), numberList("4", "4.1"), numberList("5", "5.2", "5.21")), DataTypeUtility.value(ImmutableList.<@Value Object>of())),
                rs -> new MemoryStringColumn(rs, new ColumnId("baz"), ImmutableList.of(Either.<String, @Value String>right("A"), Either.<String, @Value String>right("B"), Either.<String, @Value String>right("C")), "Z")
            ), 3))), false).get(0).getSecond();
        DataTestUtil.assertValueListEqual("Column", ImmutableList.of(DataTypeUtility.value(ImmutableList.of(new BigDecimal("4"), new BigDecimal("4.1"))), DataTypeUtility.value(ImmutableList.of(new BigDecimal("5.0"), new BigDecimal("5.2"), new BigDecimal("5.21")))), DataTestUtil.getAllCollapsedDataValid(recordSet.getColumns().get(0).getType(), recordSet.getLength()));
    }

    @Test
    public void testAOV() throws InternalException, UserException
    {
        TypeManager typeManager = new TypeManager(new UnitManager());
        RecordSet recordSet = ConvertFromR.convertRToTable(typeManager, RExecution.runRExpression(
                // From docs
                "aov(Petal.Width~Species, data = iris)"
                , ImmutableList.of(), ImmutableMap.of()), false).get(0).getSecond();
        
        assertEquals(ImmutableList.of(new ColumnId("Object")), recordSet.getColumnIds());
        ImmutableSet.Builder<@ExpressionIdentifier String> exp = ImmutableSet.builder();
        exp.add("assign", "call", "coefficients", "contrasts", "df residual", "effects", "fitted values", "model", "qr", "rank", "residuals", "terms", "xlevels");
        ImmutableSet<@ExpressionIdentifier String> expected = exp.build();
        assertEquals(expected, recordSet.getColumns().get(0).getType().getType().apply(new SpecificDataTypeVisitor<ImmutableSet<@ExpressionIdentifier String>>() {
            @Override
            public ImmutableSet<@ExpressionIdentifier String> record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, InternalException
            {
                return fields.keySet();
            }
        }));
        // TODO compare values
    }

    @Test
    public void testAOVSummary() throws InternalException, UserException
    {
        TypeManager typeManager = new TypeManager(new UnitManager());
        RecordSet recordSet = ConvertFromR.convertRToTable(typeManager, RExecution.runRExpression(
                // From docs
                "summary(aov(Petal.Width~Species, data = iris))"
                , ImmutableList.of(), ImmutableMap.of()), false).get(0).getSecond();
        assertEquals(ImmutableList.of(new ColumnId("Df"), new ColumnId("F value"), new ColumnId("Mean Sq"), new ColumnId("Pr F"), new ColumnId("Sum Sq")), recordSet.getColumnIds());
        assertEquals(2, recordSet.getLength());
        // TODO values
    }

    private Either<String, @Value ListEx> numberList(String... numbers)
    {
        return Either.right(DataTypeUtility.value(Arrays.stream(numbers).map(s -> DataTypeUtility.value(new BigDecimal(s))).collect(ImmutableList.<@Value Object>toImmutableList())));
    }

    @Test
    public void testCO2() throws InternalException, UserException
    {
        TypeManager typeManager = new TypeManager(new UnitManager());
        RValue rValue = RExecution.runRExpression("data.frame(CO2)");
        System.out.println(RPrettyPrint.prettyPrint(rValue));
        RecordSet recordSet = ConvertFromR.convertRToTable(typeManager, rValue, false).get(0).getSecond();
        TaggedValue taggedValue = Utility.cast(recordSet.getColumn(new ColumnId("Plant")).getType().getCollapsed(0), TaggedValue.class);
        assertEquals("Qn1", taggedValue.getTagName());
    }
    
    @Test
    public void testTimeout() throws InternalException
    {
        long start = System.currentTimeMillis();
        try
        {
            RExecution.runRExpression("Sys.sleep(60)");
            Assert.fail("Should have thrown timeout exception");
        }
        catch (UserException e)
        {
            // This is the expected outcome.  Check it did actually cut off, and give the right reason:
            MatcherAssert.assertThat(System.currentTimeMillis(), Matchers.<Long>lessThan(start + 20 * 1000));
            MatcherAssert.assertThat(e.getLocalizedMessage(), CoreMatchers.containsString("too long"));
        }
    }
}
