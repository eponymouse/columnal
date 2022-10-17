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

package test.gui.transformation;

import annotation.qual.Value;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.control.ListView;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import test.gui.TAppUtil;
import test.gui.TFXUtil;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.Column;
import xyz.columnal.data.ColumnUtility;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.EditableColumn;
import xyz.columnal.data.KnownLengthRecordSet;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.Table;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.DateTimeInfo.DateTimeType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.gui.MainWindow.MainWindowActions;
import xyz.columnal.gui.grid.RectangleBounds;
import xyz.columnal.gui.table.PickTypeTransformDialog.TypeTransform;
import xyz.columnal.transformations.Aggregate;
import xyz.columnal.transformations.Calculate;
import xyz.columnal.transformations.expression.*;
import xyz.columnal.transformations.expression.function.FunctionLookup;
import xyz.columnal.transformations.expression.type.TypePrimitiveLiteral;
import xyz.columnal.transformations.function.FunctionList;
import test.gen.GenImmediateData;
import test.gen.GenImmediateData.ImmediateData_Mgr;
import test.gen.GenImmediateData.MustIncludeNumber;
import test.gen.GenImmediateData.NumTables;
import test.gen.GenRandom;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.EnterStructuredValueTrait;
import test.gui.trait.ListUtilTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.simulation.SimulationFunction;
import xyz.columnal.utility.TaggedValue;
import xyz.columnal.utility.Utility;

import java.util.Random;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.Assert.*;

@RunWith(JUnitQuickcheck.class)
public class TestColumnRecipes extends FXApplicationTest implements ScrollToTrait, ClickTableLocationTrait, ListUtilTrait, EnterStructuredValueTrait
{
    @Property(trials=2)
    @OnThread(Tag.Simulation)
    public void testAverageSum(@From(GenImmediateData.class) @NumTables(maxTables = 1) @MustIncludeNumber ImmediateData_Mgr dataMgr, @From(GenRandom.class) Random r) throws Exception
    {
        // Test the average and sum recipes on numeric columns
        MainWindowActions mainWindowActions = TAppUtil.openDataAsTable(windowToUse, dataMgr.mgr).get();
        
        // Find the numeric column and scroll to it:
        Table table = mainWindowActions._test_getTableManager().getAllTables().get(0);
        Column srcColumn = table.getData().getColumns().stream().filter(c -> TBasicUtil.checkedToRuntime(() -> DataTypeUtility.isNumber(c.getType().getType()))).findFirst().orElseGet((Supplier<Column>)(() -> {throw new AssertionError("No numeric column");}));
        
        CellPosition title = keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), mainWindowActions._test_getTableManager(), table.getId(), srcColumn.getName(), TableDataRowIndex.ZERO).offsetByRowCols(-2, 0);
        
        withItemInBounds(".column-title", mainWindowActions._test_getVirtualGrid(), new RectangleBounds(title, title), this::showContextMenu);
        boolean sum = r.nextBoolean();
        clickOn(sum ? ".id-recipe-sum" : ".id-recipe-average");
        sleep(1000);
        
        Aggregate agg = (Aggregate) mainWindowActions._test_getTableManager().getAllTables().stream().filter(t -> t instanceof Aggregate).findFirst().orElseThrow(() -> new RuntimeException("No aggregate"));
        assertEquals(ImmutableList.of(), agg.getSplitBy());
        assertEquals(ImmutableList.of(new CallExpression(getFunctionLookup(), sum ? "sum" : "average", IdentExpression.column(table.getId(), srcColumn.getName()))), Utility.mapListI(agg.getColumnExpressions(), p -> p.getSecond()));
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
    @SuppressWarnings("valuetype")
    private void testTypeTransform(DataType from, @Nullable DataType to, @Nullable Function<ColumnId, Expression> expression, ImmutableList<Object> values, @Nullable Runnable execAfterTypeSelection) throws Exception
    {
        MainWindowActions mainWindowActions = TAppUtil.openDataAsTable(windowToUse, null, new KnownLengthRecordSet(ImmutableList.<SimulationFunction<RecordSet, EditableColumn>>of(ColumnUtility.makeImmediateColumn(from, new ColumnId("C"), Utility.mapListI(values, v -> v == ERR ? Either.<String, @Value Object>left("Error") : Either.<String, @Value Object>right(v)), DataTypeUtility.makeDefaultValue(from))), values.size()));

        // Find the column and scroll to it:
        Table table = mainWindowActions._test_getTableManager().getAllTables().get(0);
        Column srcColumn = table.getData().getColumns().get(0);

        CellPosition title = keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), mainWindowActions._test_getTableManager(), table.getId(), srcColumn.getName(), TableDataRowIndex.ZERO).offsetByRowCols(-2, 0);

        withItemInBounds(".column-title", mainWindowActions._test_getVirtualGrid(), new RectangleBounds(title, title), this::showContextMenu);
        clickOn(".id-recipe-transformType");
        sleep(1500);

        // Now check the options in the dialog:
        ListView<TypeTransform> listView = waitForOne(".destination-type-list");
        if (to != null && expression != null)
        {
            selectGivenListViewItem(listView, t -> t._test_hasDestinationType(to));

            clickOn(".ok-button");
            sleep(1000);
            if (execAfterTypeSelection != null)
            {
                execAfterTypeSelection.run();
                sleep(1000);
            }

            // Find calculate and check expression:
            Calculate calc = (Calculate) mainWindowActions._test_getTableManager().getAllTables().stream().filter(t -> t instanceof Calculate).findFirst().orElseThrow(() -> new RuntimeException("No calculate"));

            assertEquals(expression == null ? null : expression.apply(srcColumn.getName()), calc.getCalculatedColumns().get(srcColumn.getName()));
        }
        else if (to != null)
        {
            assertFalse(TFXUtil.fx(() -> listView.getItems()).stream().anyMatch(t -> t._test_hasDestinationType(to)));
            clickOn(".cancel-button");
        }
        else 
        {
            assertEquals(ImmutableList.of(), TFXUtil.fx(() -> listView.getItems()));
            assertTrue(TFXUtil.fx(() -> lookup(".ok-button").queryButton().isDisabled()));
            clickOn(".cancel-button");
        }
    }
    
    
    
    @Test
    @OnThread(Tag.Simulation)
    public void testEmpty() throws Exception
    {
        testTypeTransform(DataType.TEXT, null, null, ImmutableList.of(), null);
    }

    @Test
    @OnThread(Tag.Simulation)
    public void testNumToBoolean() throws Exception
    {
        testTypeTransform(DataType.number(new NumberInfo(Unit.SCALAR)), DataType.BOOLEAN, c -> new EqualExpression(ImmutableList.of(IdentExpression.column(c), new NumericLiteral(1, null)), false), ImmutableList.of(1, 0, 0, 1, ERR), null);
    }

    @Test
    @OnThread(Tag.Simulation)
    public void testNumOnlyZeroes() throws Exception
    {
        testTypeTransform(DataType.number(new NumberInfo(Unit.SCALAR)), DataType.BOOLEAN, null, ImmutableList.of(0, 0, 0, 0), null);
    }

    @Test
    @OnThread(Tag.Simulation)
    public void testDateToText() throws Exception
    {
        testTypeTransform(DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY)), DataType.TEXT, c -> new CallExpression(getFunctionLookup(), "to text", IdentExpression.column(c)), ImmutableList.of(), null);
    }

    @Test
    @OnThread(Tag.Simulation)
    public void testTextualNumberToNumber() throws Exception
    {
        testTypeTransform(DataType.TEXT, DataType.NUMBER, c -> new CallExpression(getFunctionLookup(), "extract number", IdentExpression.column(c)), ImmutableList.of("37", "0", "1.65", "-3.562", "none"), null);
    }

    @Test
    @OnThread(Tag.Simulation)
    public void testTextualNumberToNumber2() throws Exception
    {
        testTypeTransform(DataType.TEXT, DataType.NUMBER, c -> new CallExpression(getFunctionLookup(), "extract number", IdentExpression.column(c)), ImmutableList.of("37m", "0m", "1.65m", "-3.562 metres", "n/a"), null);
    }

    @Test
    @OnThread(Tag.Simulation)
    public void testTextualNumberToNumber3() throws Exception
    {
        TypeManager typeManager = new TypeManager(new UnitManager());
        testTypeTransform(DataType.TEXT, typeManager.getMaybeType().instantiate(ImmutableList.of(Either.right(DataType.NUMBER)), typeManager), c -> new CallExpression(getFunctionLookup(), "extract number or none", IdentExpression.column(c)), ImmutableList.of("37m", "0m", "1.65m", "-3.562 metres", "n/a"), null);
    }

    @Test
    @OnThread(Tag.Simulation)
    public void testNonDateToText() throws Exception
    {
        testTypeTransform(DataType.TEXT, DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY)), null, ImmutableList.of("37", "0", "1.65", "-3.562"), null);
    }

    @Test
    @OnThread(Tag.Simulation)
    public void testTextualTimeToTime() throws Exception
    {
        DataType time = DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAY));
        testTypeTransform(DataType.TEXT, time, c -> new CallExpression(getFunctionLookup(), "from text to", new TypeLiteralExpression(new TypePrimitiveLiteral(time)), IdentExpression.column(c)), ImmutableList.of("4:51", "16:05", "5:21PM", "0:00:03.435346346 AM"), null);
    }

    @Test
    @OnThread(Tag.Simulation)
    public void testTextualDateToDate() throws Exception
    {
        DataType date = DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY));
        testTypeTransform(DataType.TEXT, date, c -> new CallExpression(getFunctionLookup(), "from text to", new TypeLiteralExpression(new TypePrimitiveLiteral(date)), IdentExpression.column(c)), ImmutableList.of(ERR, "May 12 2018", "30-04-17", "21 Jun 2018", ERR, "2018-03-04"), null);
    }

    @Test
    @OnThread(Tag.Simulation)
    public void testTextualNumberToBoolean() throws Exception
    {
        testTypeTransform(DataType.TEXT, DataType.BOOLEAN, c -> new CallExpression(getFunctionLookup(), "list contains", new ArrayExpression(Utility.mapListI(ImmutableList.of("true", "t", "yes", "y", "on"), StringLiteral::new)), new CallExpression(getFunctionLookup(), "lower case", IdentExpression.column(c))), ImmutableList.of("T", "F", "T", "T", "F"), null);
    }

    @Test
    @OnThread(Tag.Simulation)
    public void testTextualNumberToBoolean2() throws Exception
    {
        testTypeTransform(DataType.TEXT, DataType.BOOLEAN, c ->
            new CallExpression(getFunctionLookup(), "list contains", new ArrayExpression(Utility.mapListI(ImmutableList.of("true", "t", "yes", "y", "on"), StringLiteral::new)), new CallExpression(getFunctionLookup(), "lower case", IdentExpression.column(c))), ImmutableList.of("yes", "no", "Yes", "No", "Y"), null);
    }

    @Test
    @OnThread(Tag.Simulation)
    public void testDateTimeToDate() throws Exception
    {
        DataType datetime = DataType.date(new DateTimeInfo(DateTimeType.DATETIME));
        DataType date = DataType.date(new DateTimeInfo(DateTimeType.YEARMONTHDAY));
        testTypeTransform(datetime, date, c -> new CallExpression(getFunctionLookup(), "date from datetime", IdentExpression.column(c)), ImmutableList.of(ERR), null);
    }

    @Test
    @OnThread(Tag.Simulation)
    public void testDateTimeZonedToYearYM() throws Exception
    {
        DataType datetime = DataType.date(new DateTimeInfo(DateTimeType.DATETIMEZONED));
        DataType date = DataType.date(new DateTimeInfo(DateTimeType.YEARMONTH));
        testTypeTransform(datetime, date, c -> new CallExpression(getFunctionLookup(), "dateym from date", new CallExpression(getFunctionLookup(), "date from datetime", new CallExpression(getFunctionLookup(), "datetime from datetimezoned", IdentExpression.column(c)))), ImmutableList.of(ERR), null);
    }
    
    @Test
    @OnThread(Tag.Simulation)
    public void testOptionalNumberToNumber() throws Exception
    {
        UnitManager unitManager = new UnitManager();
        Unit s = unitManager.loadUse("s");
        TypeManager typeManager = new TypeManager(unitManager);
        testTypeTransform(optional(DataType.number(new NumberInfo(s))), DataType.number(new NumberInfo(s)), c -> new CallExpression(getFunctionLookup(), "get optional or", IdentExpression.column(c), new NumericLiteral(34, UnitExpression.load(s))), ImmutableList.of(new TaggedValue(0, null, typeManager.getMaybeType())), () -> {
            write("34");
            clickOn(".ok-button");
        });
    }

    private DataType optional(DataType inner) throws UserException, InternalException
    {
        TypeManager typeManager = new TypeManager(new UnitManager());
        return typeManager.getMaybeType().instantiate(ImmutableList.of(Either.right(inner)), typeManager);
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
