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

import annotation.identifier.qual.ExpressionIdentifier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.runner.RunWith;
import org.testfx.service.query.NodeQuery;
import test.gui.TAppUtil;
import test.gui.TFXUtil;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.EditableColumn;
import xyz.columnal.data.EditableRecordSet;
import xyz.columnal.data.MemoryBooleanColumn;
import xyz.columnal.data.MemoryNumericColumn;
import xyz.columnal.data.MemoryTemporalColumn;
import xyz.columnal.data.RecordSet;
import xyz.columnal.id.TableId;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.DateTimeInfo.DateTimeType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.gui.MainWindow.MainWindowActions;
import xyz.columnal.gui.grid.RectangleBounds;
import xyz.columnal.gui.grid.VirtualGrid;
import xyz.columnal.gui.lexeditor.EditorDisplay;
import xyz.columnal.transformations.Calculate;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.IdentExpression;
import test.gen.GenRandom;
import test.gui.trait.AutoCompleteTrait;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.PopupTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.function.simulation.SimulationFunction;
import xyz.columnal.utility.Utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.Assert.*;

@RunWith(JUnitQuickcheck.class)
public class TestCalculate extends FXApplicationTest implements ScrollToTrait, AutoCompleteTrait, PopupTrait, ClickTableLocationTrait
{
    private static enum Col
    {
        BOO, YM, BOOT_SIZE, YM_LOWER;
    }
    
    private @ExpressionIdentifier String getName(Col col)
    {
        switch (col)
        {
            case BOO: return "Boo";
            case YM: return "YM";
            case YM_LOWER: return "ym";
            case BOOT_SIZE: return "Boot Size";
        }
        throw new AssertionError("Unknown case: " + col);
    }
    
    @OnThread(Tag.Simulation)
    private EditableColumn makeColumn(Col column, RecordSet rs) throws InternalException, UserException
    {
        ColumnId columnId = new ColumnId(getName(column));
        switch (column)
        {
            case BOO:
                return new MemoryBooleanColumn(rs, columnId, ImmutableList.of(), false);
            case YM:
            case YM_LOWER:
                return new MemoryTemporalColumn(rs, columnId, new DateTimeInfo(DateTimeType.YEARMONTH), ImmutableList.of(), TBasicUtil.checkNonNull(DataTypeUtility.value(new DateTimeInfo(DateTimeType.YEARMONTH), DateTimeInfo.DEFAULT_VALUE)));
            case BOOT_SIZE:
                return new MemoryNumericColumn(rs, columnId, new NumberInfo(Unit.SCALAR), Stream.empty());
        }
        throw new AssertionError("Unknown case: " + column);
    }
    
    @Property(trials = 3)
    @OnThread(Tag.Simulation)
    public void testCalculate(@From(GenRandom.class) Random r) throws Exception
    {
        RecordSet orig = new EditableRecordSet(Utility.<Col, SimulationFunction<RecordSet, EditableColumn>>mapListI(ImmutableList.copyOf(Col.values()), (Col c) -> (RecordSet rs) -> makeColumn(c, rs)), () -> 0);
        MainWindowActions mainWindowActions = TAppUtil.openDataAsTable(windowToUse, null, orig);

        CellPosition calcPos = CellPosition.ORIGIN.offsetByRowCols(1, 6);
        VirtualGrid grid = mainWindowActions._test_getVirtualGrid();
        keyboardMoveTo(grid, calcPos);
        
        // We shuffle this list, then use item at indexes 0 and 1 as name of new calculated columns.
        // We delete columns at index 0 and 2
        List<Col> colList = new ArrayList<>(ImmutableList.copyOf(Col.values()));
        Collections.shuffle(colList, r);
        
        push(KeyCode.ENTER);
        clickOn(".id-new-transform");
        clickOn(".id-transform-calculate");
        write("Table1");
        push(KeyCode.ENTER);
        enterInfo(mainWindowActions, r, getName(colList.get(0)));
        
        if (r.nextBoolean())
        {
            // Add by adding new column with right name:
            showContextMenu(withItemInBounds(findColumnTitle(getName(colList.get(1))), grid, new RectangleBounds(calcPos, calcPos.offsetByRowCols(3, 3)), (n, p) -> {
            }), null);
            clickOn(r.nextBoolean() ? ".id-virtGrid-column-addBefore" : ".id-virtGrid-column-addAfter");
        }
        else
        {
            // Add by clicking column name:
            clickOnItemInBounds(findColumnTitle(getName(colList.get(1))), grid, new RectangleBounds(calcPos, calcPos.offsetByRowCols(3, 3)));
        }
        enterInfo(mainWindowActions, r, getName(colList.get(1)));

        // Now try deleting a column:
        showContextMenu(withItemInBounds(findColumnTitle(getName(colList.get(0))), grid, new RectangleBounds(calcPos, calcPos.offsetByRowCols(3, 3)), (n, p) -> {
        }), null);
        clickOn(".id-virtGrid-column-delete");
        sleep(300);
        assertFalse(getCalculate(mainWindowActions).getCalculatedColumns().containsKey(new ColumnId(getName(colList.get(0)))));

        // Delete on from original:
        showContextMenu(withItemInBounds(findColumnTitle(getName(colList.get(2))), grid, new RectangleBounds(CellPosition.ORIGIN, CellPosition.ORIGIN.offsetByRowCols(2, 4)), (n, p) -> {
        }), null);
        clickOn(".id-virtGrid-column-delete");
        sleep(300);
        assertFalse(getCalculate(mainWindowActions).getData().getColumnIds().contains(new ColumnId(getName(colList.get(2)))));
        
        // Now we rename column to either overlap another column, or be totally new, then we swap it back:
        clickOnItemInBounds(findColumnTitle(getName(colList.get(1))), grid, new RectangleBounds(calcPos, calcPos.offsetByRowCols(1, 4)));
        @ExpressionIdentifier String newColName = r.nextBoolean() ? getName(colList.get(3)) : "Arbitrary New Name";
        write(newColName);
        moveAndDismissPopupsAtPos(point(".ok-button"));
        clickOn(".ok-button");
        // Now check columns are right:
        ImmutableSet<ColumnId> expectedColumns = Stream.of(new ColumnId(getName(colList.get(0))), new ColumnId(getName(colList.get(1))), new ColumnId(getName(colList.get(3))), new ColumnId(newColName)).distinct().collect(ImmutableSet.toImmutableSet());
        assertEquals(expectedColumns, ImmutableSet.copyOf(getCalculate(mainWindowActions).getData().getColumnIds()));
        // Change it back to colList.get(1) and check again:
        clickOnItemInBounds(findColumnTitle(newColName), grid, new RectangleBounds(calcPos, calcPos.offsetByRowCols(1, 4)));
        write(getName(colList.get(1)));
        moveAndDismissPopupsAtPos(point(".ok-button"));
        clickOn(".ok-button");
        // Now check columns are right:
        expectedColumns = Stream.of(new ColumnId(getName(colList.get(0))), new ColumnId(getName(colList.get(1))), new ColumnId(getName(colList.get(3)))).distinct().collect(ImmutableSet.toImmutableSet());
        assertEquals(expectedColumns, ImmutableSet.copyOf(getCalculate(mainWindowActions).getData().getColumnIds()));
    }
    
    private void enterInfo(MainWindowActions mainWindowActions, Random r, @ExpressionIdentifier String columnNameToReplace)
    {
        sleep(300);
        assertShowing("Expression editor", ".expression-editor");
        
        boolean clickedExistingForName = r.nextBoolean(); 
        if (clickedExistingForName)
        {
            assertTrue(getFocusOwner() instanceof TextField);
            // Click on existing column name to use it
            clickOnItemInBounds(findColumnTitle(columnNameToReplace), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(CellPosition.ORIGIN.offsetByRowCols(1, 1), CellPosition.ORIGIN.offsetByRowCols(1+2, 1+3)));
            moveAndDismissPopupsAtPos(point(".expression-editor .editor-display"));
            clickOn(".expression-editor .editor-display");
            Node focusOwner = getFocusOwner();
            assertTrue("Focus owner: " + (focusOwner == null ? "null" : focusOwner.getClass().toString()), focusOwner instanceof EditorDisplay);
        }
        else
        {
            // Use autocomplete, but need to write one char for it to show up:
            write(columnNameToReplace.substring(0, 1));
            autoComplete(columnNameToReplace, true);
        }
        
        push(KeyCode.SHORTCUT, KeyCode.A);
        push(KeyCode.DELETE);
        TFXUtil.sleep(200);
        
        // Copy existing column:
        boolean clickedExistingForExpression = r.nextBoolean();
        if (clickedExistingForExpression)
        {
            clickOnItemInBounds(findColumnTitle(columnNameToReplace), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(CellPosition.ORIGIN, CellPosition.ORIGIN.offsetByRowCols(2, 4)));
        }
        else
        {
            lexComplete(columnNameToReplace);
        }
        
        moveAndDismissPopupsAtPos(point(".ok-button"));
        clickOn(".ok-button");
        assertNotShowing("Exp Editor should be hidden, after: " + clickedExistingForName + " and " + clickedExistingForExpression, ".expression-editor");
        sleep(500);
        Calculate calculate = getCalculate(mainWindowActions);
        Expression expression = TBasicUtil.checkNonNull(calculate.getCalculatedColumns().get(new ColumnId(columnNameToReplace)));
        MatcherAssert.assertThat(expression, Matchers.is(Matchers.in(ImmutableList.of(IdentExpression.column(new TableId("Table1"), new ColumnId(columnNameToReplace)), IdentExpression.column(new ColumnId(columnNameToReplace))))));
        
    }

    private Calculate getCalculate(MainWindowActions mainWindowActions)
    {
        return TBasicUtil.checkNonNull(Utility.filterClass(mainWindowActions._test_getTableManager().getAllTables().stream(), Calculate.class).findFirst().orElse(null));
    }

    private NodeQuery findColumnTitle(@ExpressionIdentifier String columnNameToReplace)
    {
        return TFXUtil.fx(() -> lookup(".column-title").match((Label l) -> l.getText().equals(columnNameToReplace)));
    }
}
