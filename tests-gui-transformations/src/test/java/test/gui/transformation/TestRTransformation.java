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

import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import org.junit.Test;
import org.junit.runner.RunWith;
import test.gui.TAppUtil;
import test.gui.TFXUtil;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.ColumnUtility;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.KnownLengthRecordSet;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.gui.MainWindow.MainWindowActions;
import xyz.columnal.gui.grid.RectangleBounds;
import xyz.columnal.transformations.RTransformation;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;

import static org.junit.Assert.assertEquals;

@RunWith(JUnitQuickcheck.class)
public class TestRTransformation extends FXApplicationTest implements ScrollToTrait, ClickTableLocationTrait
{
    // The R functionality is tested more thoroughly in other tests,
    // this is about testing the GUI, and the package installation.
    
    @Test
    @OnThread(Tag.Simulation)
    public void testR() throws Exception
    {
        // Uninstall one package first to check installation works:
        Runtime.getRuntime().exec(new String[] {"R", "CMD", "REMOVE", "digest"});
        
        RecordSet original = new KnownLengthRecordSet(ImmutableList.of(ColumnUtility.makeImmediateColumn(DataType.TEXT, new ColumnId("The Strings"), ImmutableList.of(Either.right("Hello"), Either.right("There")), "")), 2);
        
        MainWindowActions mainWindowActions = TAppUtil.openDataAsTable(windowToUse, null, original);
        TFXUtil.sleep(5000);
        CellPosition targetPos = TFXUtil.fx(() -> mainWindowActions._test_getTableManager().getNextInsertPosition(null));
        keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), targetPos);
        clickOnItemInBounds(fromNode(TFXUtil.fx(() -> mainWindowActions._test_getVirtualGrid().getNode())), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
        TFXUtil.sleep(500);
        clickOn(".id-new-transform");
        TFXUtil.sleep(500);
        scrollTo(".id-transform-runr");
        clickOn(".id-transform-runr");
        // First run can take quite a while:
        TFXUtil.sleep(200_000);
        // Focus should begin in the expression, so let's do that first:
        write("digest(str_c(Table1$\"The Strings\"))");
        
        clickOn(".id-fancylist-add");
        write("Ta");
        push(KeyCode.DOWN);
        push(KeyCode.ENTER);

        clickOn(".r-package-field");
        write("digest, stringr");
        
        clickOn(".ok-button");
        
        // Since we're installing packages, wait a bit longer than usual:
        sleep(200_000);
        
        // Now check table exists, with correct output
        assertEquals(2, mainWindowActions._test_getTableManager().getAllTables().size());
        RTransformation rTransformation = (RTransformation) TBasicUtil.checkNonNull(mainWindowActions._test_getTableManager().getAllTables().stream().filter(t -> t instanceof RTransformation).findFirst().orElse(null));
        assertEquals(ImmutableList.of(new ColumnId("Result")), rTransformation.getData().getColumnIds());
        assertEquals("497859090e5f950944a1e8cf3989dd8d", rTransformation.getData().getColumns().get(0).getType().getCollapsed(0));
    }
}
