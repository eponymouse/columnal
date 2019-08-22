package test.gui.transformation;

import annotation.qual.Value;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.control.ListView;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import records.data.CellPosition;
import records.data.Column;
import records.data.ColumnId;
import records.data.EditableColumn;
import records.data.KnownLengthRecordSet;
import records.data.RecordSet;
import records.data.Table;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.MainWindow.MainWindowActions;
import records.gui.grid.RectangleBounds;
import records.gui.table.PickTypeTransformDialog.TypeTransform;
import records.transformations.Aggregate;
import records.transformations.Calculate;
import records.transformations.expression.CallExpression;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.EqualExpression;
import records.transformations.expression.Expression;
import records.transformations.expression.IfThenElseExpression;
import records.transformations.expression.NumericLiteral;
import records.transformations.expression.function.FunctionLookup;
import records.transformations.function.FunctionList;
import test.TestUtil;
import test.gen.GenImmediateData;
import test.gen.GenImmediateData.ImmediateData_Mgr;
import test.gen.GenImmediateData.MustIncludeNumber;
import test.gen.GenImmediateData.NumTables;
import test.gen.GenRandom;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.ListUtilTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.SimulationFunction;
import utility.Utility;

import java.util.Random;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.Assert.*;

@RunWith(JUnitQuickcheck.class)
public class TestColumnRecipes extends FXApplicationTest implements ScrollToTrait, ClickTableLocationTrait, ListUtilTrait
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
        assertEquals(ImmutableList.of(new CallExpression(getFunctionLookup(), sum ? "sum" : "average", new ColumnReference(srcColumn.getName(), ColumnReferenceType.CORRESPONDING_ROW))), Utility.mapListI(agg.getColumnExpressions(), p -> p.getSecond()));
    }
    
    private final Object ERR = new Object();

    /**
     * 
     * @param from
     * @param to If null, check that there are no transforms (expression will be ignored)
     * @param expression If null (and to is not null), check that the to type in specific is not shown.
     * @param values
     * @throws Exception
     */ 
    @OnThread(Tag.Simulation)
    @SuppressWarnings("value")
    private void testTypeTransform(DataType from, @Nullable DataType to, @Nullable Function<ColumnId, Expression> expression, ImmutableList<Object> values) throws Exception
    {
        MainWindowActions mainWindowActions = TestUtil.openDataAsTable(windowToUse, null, new KnownLengthRecordSet(ImmutableList.<SimulationFunction<RecordSet, EditableColumn>>of(from.makeImmediateColumn(new ColumnId("C"), Utility.mapListI(values, v -> v == ERR ? Either.<String, @Value Object>left("Error") : Either.<String, @Value Object>right(v)), DataTypeUtility.makeDefaultValue(from))), values.size()));

        // Find the column and scroll to it:
        Table table = mainWindowActions._test_getTableManager().getAllTables().get(0);
        Column srcColumn = table.getData().getColumns().get(0);

        CellPosition title = keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), mainWindowActions._test_getTableManager(), table.getId(), srcColumn.getName(), TableDataRowIndex.ZERO).offsetByRowCols(-2, 0);

        withItemInBounds(lookup(".column-title"), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(title, title), this::showContextMenu);
        clickOn(".id-recipe-transformType");
        sleep(1500);

        // Now check the options in the dialog:
        ListView<TypeTransform> listView = lookup(".destination-type-list").<TypeTransform>queryListView();
        if (to != null && expression != null)
        {
            selectGivenListViewItem(listView, t -> t._test_hasDestinationType(to));

            clickOn(".ok-button");
            sleep(1000);

            // Find calculate and check expression:
            Calculate calc = (Calculate) mainWindowActions._test_getTableManager().getAllTables().stream().filter(t -> t instanceof Calculate).findFirst().orElseThrow(() -> new RuntimeException("No calculate"));

            assertEquals(expression == null ? null : expression.apply(srcColumn.getName()), calc.getCalculatedColumns().get(srcColumn.getName()));
        }
        else if (to != null)
        {
            assertFalse(TestUtil.fx(() -> listView.getItems()).stream().anyMatch(t -> t._test_hasDestinationType(to)));
            clickOn(".cancel-button");
        }
        else 
        {
            assertEquals(ImmutableList.of(), TestUtil.fx(() -> listView.getItems()));
            assertTrue(TestUtil.fx(() -> lookup(".ok-button").queryButton().isDisabled()));
            clickOn(".cancel-button");
        }
    }
    
    
    
    @Test
    @OnThread(Tag.Simulation)
    public void testEmpty() throws Exception
    {
        testTypeTransform(DataType.TEXT, null, null, ImmutableList.of());
    }

    @Test
    @OnThread(Tag.Simulation)
    public void testNumToBoolean() throws Exception
    {
        testTypeTransform(DataType.number(new NumberInfo(Unit.SCALAR)), DataType.BOOLEAN, c -> new EqualExpression(ImmutableList.of(new ColumnReference(c, ColumnReferenceType.CORRESPONDING_ROW), new NumericLiteral(1, null)), false), ImmutableList.of(1, 0, 0, 1, ERR));
    }

    @Test
    @OnThread(Tag.Simulation)
    public void testNumOnlyZeroes() throws Exception
    {
        testTypeTransform(DataType.number(new NumberInfo(Unit.SCALAR)), DataType.BOOLEAN, null, ImmutableList.of(0, 0, 0, 0));
    }

    @Test
    @OnThread(Tag.Simulation)
    public void testDateToText() throws Exception
    {
        testTypeTransform(DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY)), DataType.TEXT, c -> new CallExpression(getFunctionLookup(), "to text", new ColumnReference(c, ColumnReferenceType.CORRESPONDING_ROW)), ImmutableList.of());
    }

    private static FunctionLookup getFunctionLookup()
    {
        try
        {
            return FunctionList.getFunctionLookup(new UnitManager());
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
}
