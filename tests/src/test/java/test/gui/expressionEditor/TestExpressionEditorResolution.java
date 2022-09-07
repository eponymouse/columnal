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

package test.gui.expressionEditor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.junit.Test;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.ColumnId;
import xyz.columnal.data.EditableRecordSet;
import xyz.columnal.data.ImmediateDataSource;
import xyz.columnal.data.MemoryNumericColumn;
import xyz.columnal.data.Table;
import xyz.columnal.data.Table.InitialLoadDetails;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.gui.MainWindow.MainWindowActions;
import xyz.columnal.gui.grid.RectangleBounds;
import xyz.columnal.gui.lexeditor.EditorDisplay;
import xyz.columnal.transformations.Calculate;
import xyz.columnal.transformations.function.FunctionList;
import test.DummyManager;
import test.TestUtil;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.EnterExpressionTrait;
import test.gui.trait.PopupTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;

import static org.junit.Assert.assertEquals;

public class TestExpressionEditorResolution extends FXApplicationTest implements ScrollToTrait, ClickTableLocationTrait, EnterExpressionTrait, PopupTrait
{
    private void testLoadNameResolution(String expressionSrc, String expectedLoaded) throws Exception
    {
        TableManager orig = new DummyManager();
        Table data = new ImmediateDataSource(orig, TestUtil.ILD, new EditableRecordSet(ImmutableList.of(rs -> new MemoryNumericColumn(rs, new ColumnId("round"), NumberInfo.DEFAULT, ImmutableList.of(), 0L)), () -> 0));
        orig.record(data);
        Table calc = new Calculate(orig, new InitialLoadDetails(new CellPosition(CellPosition.row(4), CellPosition.col(4))), data.getId(), ImmutableMap.of(new ColumnId("Calc Col"), TestUtil.parseExpression(expressionSrc, orig.getTypeManager(), FunctionList.getFunctionLookup(orig.getUnitManager()))));
        orig.record(calc);
        
        MainWindowActions mainWindowActions = TestUtil.openDataAsTable(windowToUse, orig).get();
        sleep(1000);
        try
        {
            CellPosition targetPos = new CellPosition(CellPosition.row(5), CellPosition.col(5));
            
            // Not sure why this doesn't work:
            //clickOnItemInBounds(lookup(".create-table-grid-button"), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
            
            clickOnItemInBounds(lookup(".table-display-column-title"), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos));
            sleep(500);
            push(KeyCode.TAB);
            sleep(100);
            String content = getEditorDisplay()._test_getEditor()._test_getRawText();

            assertEquals(expectedLoaded, content);

            // Close dialog, ignoring errors:
            TestUtil.doubleOk(this);
        }
        finally
        {
            Stage s = windowToUse;
            Platform.runLater(() -> s.hide());
        }
    }

    private EditorDisplay getEditorDisplay()
    {
        Node focusOwner = getFocusOwner();
        if (!(focusOwner instanceof EditorDisplay))
            throw new RuntimeException("Focus owner is " + (focusOwner == null ? "null" : focusOwner.getClass().toString()));
        return (EditorDisplay) focusOwner;
    }

    @Test
    public void checkLoad1() throws Exception
    {
        testLoadNameResolution("var\\\\x", "x");
    }

    @Test
    public void checkLoad2() throws Exception
    {
        testLoadNameResolution("@call function\\\\number\\abs(3)", "abs(3)");
    }

    @Test
    public void checkLoad3() throws Exception
    {
        testLoadNameResolution("@call function\\\\number\\round(3)", "function\\\\round(3)");
    }

    @Test
    public void checkLoad3b() throws Exception
    {
        testLoadNameResolution("column\\\\round", "column\\\\round");
    }

    @Test
    public void checkLoad4() throws Exception
    {
        testLoadNameResolution("@define var\\\\round = 3 @then var\\\\round + column\\\\round @enddefine", "@definevar\\\\round=3@thenvar\\\\round+column\\\\round@enddefine");
    }

    @Test
    public void checkLoad4b() throws Exception
    {
        testLoadNameResolution("@define var\\\\round 2 = 3 @then var\\\\round 2 + column\\\\round @enddefine", "@defineround 2=3@thenround 2+column\\\\round@enddefine");
    }

    @Test
    public void checkLoad5() throws Exception
    {
        testLoadNameResolution("@define var\\\\abs = 2 @then var\\\\abs + @call function\\\\number\\abs(-4) @enddefine + @call function\\\\number\\abs(3)", "@definevar\\\\abs=2@thenvar\\\\abs+function\\\\abs(-4)@enddefine+abs(3)");
    }

    @Test
    public void checkLoad6() throws Exception
    {
        testLoadNameResolution("@if 2 =~ var\\\\abs @then var\\\\abs + @call function\\\\number\\abs(-4) @else @call function\\\\number\\abs(-5) @endif + @call function\\\\number\\abs(3)", "@if2=~var\\\\abs@thenvar\\\\abs+function\\\\abs(-4)@elseabs(-5)@endif+abs(3)");
    }
}
