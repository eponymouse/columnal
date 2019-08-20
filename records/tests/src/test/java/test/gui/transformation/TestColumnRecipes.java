package test.gui.transformation;

import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.runner.RunWith;
import records.data.CellPosition;
import records.data.Column;
import records.data.Table;
import records.data.datatype.DataTypeUtility;
import records.data.unit.UnitManager;
import records.gui.MainWindow.MainWindowActions;
import records.gui.grid.RectangleBounds;
import records.transformations.Aggregate;
import records.transformations.expression.CallExpression;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.function.FunctionList;
import test.TestUtil;
import test.gen.GenImmediateData;
import test.gen.GenImmediateData.ImmediateData_Mgr;
import test.gen.GenImmediateData.MustIncludeNumber;
import test.gen.GenImmediateData.NumTables;
import test.gen.GenRandom;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.util.Random;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;

@RunWith(JUnitQuickcheck.class)
public class TestColumnRecipes extends FXApplicationTest implements ScrollToTrait, ClickTableLocationTrait
{
    @Property(trials=2)
    @OnThread(Tag.Simulation)
    public void testAverageSum(@From(GenImmediateData.class) @NumTables(maxTables = 1) @MustIncludeNumber ImmediateData_Mgr dataMgr, @From(GenRandom.class) Random r) throws Exception
    {
        // Test the average and sum recipes on numeric columns
        MainWindowActions mainWindowActions = TestUtil.openDataAsTable(windowToUse, dataMgr.mgr).get();
        
        // Find the numeric column and scroll to it:
        Table table = mainWindowActions._test_getTableManager().getAllTables().get(0);
        Column srcColumn = table.getData().getColumns().stream().filter(c -> TestUtil.checkedToRuntime(() -> DataTypeUtility.isNumber(c.getType().getType()))).findFirst().orElseGet((Supplier<Column>)(() -> {throw new AssertionError("No numeric column");}));
        
        CellPosition title = keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), mainWindowActions._test_getTableManager(), table.getId(), srcColumn.getName(), TableDataRowIndex.ZERO).offsetByRowCols(-2, 0);
        
        withItemInBounds(lookup(".column-title"), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(title, title), this::showContextMenu);
        boolean sum = r.nextBoolean();
        clickOn(sum ? ".id-recipe-sum" : ".id-recipe-average");
        sleep(1000);
        
        Aggregate agg = (Aggregate) mainWindowActions._test_getTableManager().getAllTables().stream().filter(t -> t instanceof Aggregate).findFirst().orElseThrow(() -> new RuntimeException("No aggregate"));
        assertEquals(ImmutableList.of(), agg.getSplitBy());
        assertEquals(ImmutableList.of(new CallExpression(FunctionList.getFunctionLookup(new UnitManager()), sum ? "sum" : "average", new ColumnReference(srcColumn.getName(), ColumnReferenceType.CORRESPONDING_ROW))), Utility.mapListI(agg.getColumnExpressions(), p -> p.getSecond()));
        
    }
}
