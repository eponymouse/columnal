package test.importExport;

import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.Test;
import org.junit.runner.RunWith;
import records.data.Column;
import records.data.ColumnId;
import records.data.DataTestUtil;
import records.data.RecordSet;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.rinterop.RData;
import records.rinterop.RExecution;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;

@SuppressWarnings("valuetype")
@RunWith(JUnitQuickcheck.class)
public class TestRExecution
{
    @Test
    public void testSimple() throws UserException, InternalException
    {
        TypeManager typeManager = new TypeManager(new UnitManager());
        Column column = RData.convertRToTable(typeManager, RExecution.runRExpression("c(6, 8)")).get(0).getSecond().getColumns().get(0);
        DataTestUtil.assertValueListEqual("Column", ImmutableList.of(6, 8), DataTestUtil.getAllCollapsedDataValid(column.getType(), column.getLength()));
    }
    
    @Test
    public void testSimple2() throws UserException, InternalException
    {
        TypeManager typeManager = new TypeManager(new UnitManager());
        Column column = RData.convertRToTable(typeManager, RExecution.runRExpression("seq(1,10,2)")).get(0).getSecond().getColumns().get(0);
        DataTestUtil.assertValueListEqual("Column", ImmutableList.of(1, 3, 5, 7, 9), DataTestUtil.getAllCollapsedDataValid(column.getType(), column.getLength()));
    }
    
    @Test
    public void testAIC() throws InternalException, UserException
    {
        TypeManager typeManager = new TypeManager(new UnitManager());
        RecordSet recordSet = RData.convertRToTable(typeManager, RExecution.runRExpression(
        // From docs
                "lm1 <- lm(Fertility ~ . , data = swiss)\n" +
                "AIC(lm1)\n" +
                "stopifnot(all.equal(AIC(lm1),\n" +
                "                    AIC(logLik(lm1))))\n" +
                "BIC(lm1)\n" +
                "\n" +
                "lm2 <- update(lm1, . ~ . -Examination)\n" +
                "AIC(lm1, lm2)"
        , ImmutableList.of("stats"))).get(0).getSecond();
        assertEquals(ImmutableList.of(new ColumnId("df"), new ColumnId("AIC")), recordSet.getColumnIds());
        assertEquals(ImmutableList.of(new BigDecimal("326.07156844054867406157427467405796051025390625"), new BigDecimal("325.2408440639818536510574631392955780029296875")), DataTestUtil.getAllCollapsedDataValid(recordSet.getColumn(new ColumnId("AIC")).getType(), recordSet.getLength()));
    }
}
