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

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import javafx.application.Platform;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.testfx.util.WaitForAsyncUtils;
import test.gen.ExpressionValue;
import test.gui.TAppUtil;
import test.gui.TFXUtil;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.EnterExpressionTrait;
import test.gui.trait.PopupTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.data.Transformation;
import xyz.columnal.gui.MainWindow.MainWindowActions;
import xyz.columnal.gui.View;
import xyz.columnal.gui.grid.RectangleBounds;
import xyz.columnal.id.ColumnId;
import xyz.columnal.importers.ClipboardUtils;
import xyz.columnal.importers.ClipboardUtils.LoadedColumnInfo;
import xyz.columnal.log.Log;
import xyz.columnal.transformations.Calculate;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.Utility;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BaseTestExpressionEditorEntry extends FXApplicationTest implements ScrollToTrait, ClickTableLocationTrait, EnterExpressionTrait, PopupTrait
{
    @OnThread(Tag.Simulation)
    protected void testEntry_Impl(ExpressionValue expressionValue, Random r, String... qualifiedIdentsToEnterInFull) throws Exception
    {
        MainWindowActions mainWindowActions = TAppUtil.openDataAsTable(windowToUse, expressionValue.typeManager, expressionValue.recordSet, expressionValue.tableId);
        try
        {
            Region gridNode = TFXUtil.fx(() -> mainWindowActions._test_getVirtualGrid().getNode());
            CellPosition targetPos = new CellPosition(CellPosition.row(3), CellPosition.col(3 + expressionValue.recordSet.getColumns().size()));
            keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), targetPos);
            // Only need to click once as already selected by keyboard:
            for (int i = 0; i < 1; i++)
                clickOnItemInBounds(fromNode(gridNode), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
            // Not sure why this doesn't work:
            //clickOnItemInBounds(lookup(".create-table-grid-button"), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
            correctTargetWindow().clickOn(".id-new-transform");
            correctTargetWindow().clickOn(".id-transform-calculate");
            correctTargetWindow().write(expressionValue.tableId.getRaw());
            push(KeyCode.ENTER);
            TFXUtil.sleep(200);
            write("DestCol");
            // Focus expression editor:
            push(KeyCode.TAB);
            Log.normal("Entering expression:\n" + expressionValue.expression.toString() + "\n");
            enterExpression(mainWindowActions._test_getTableManager().getTypeManager(), expressionValue.expression, EntryBracketStatus.SURROUNDED_BY_KEYWORDS, r, qualifiedIdentsToEnterInFull);
            
            // We check this twice, once for original entry, once for no-op edit:
            for (int i = 0; i < 2; i++)
            {
                // Get rid of popups:
                TFXUtil.doubleOk(this);
                // Now close dialog, and check for equality;
                correctTargetWindow();
                View view = waitForOne(".view");
                TFXUtil.sleep(500);
                assertNotShowing("No OK buttong", ".ok-button");
                Calculate calculate = (Calculate) view.getManager().getAllTables().stream().filter(t -> t instanceof Transformation).findFirst().orElseThrow(() -> new RuntimeException("No transformation found"));
    
                // Check expressions match:
                Expression expression = calculate.getCalculatedColumns().values().iterator().next();
                assertEquals("Loop " + i, expressionValue.expression, expression);
                // Just in case equals is wrong, check String comparison:
                assertEquals("Loop " + i, expressionValue.expression.toString(), expression.toString());
    
                // Check that a no-op edit gives same expression:
                if (i == 0)
                {
                    @SuppressWarnings("units") // Declaration just to allow suppression
                    CellPosition _pos = keyboardMoveTo(view.getGrid(), view.getManager(), calculate.getId(), 0, expressionValue.recordSet.getColumns().size());
                    clickOn("DestCol");
                }
            }

            // Now check values match:
            TFXUtil.fx_(() -> Clipboard.getSystemClipboard().clear());
            showContextMenu(".table-display-table-title.transformation-table-title")
                .clickOn(".id-tableDisplay-menu-copyValues");
            TFXUtil.sleep(1000);
            Optional<ImmutableList<LoadedColumnInfo>> clip = TFXUtil.<Optional<ImmutableList<LoadedColumnInfo>>>fx(() -> ClipboardUtils.loadValuesFromClipboard(expressionValue.typeManager));
            assertTrue(clip.isPresent());
            // Need to fish out first column from clip, then compare item:
            //TestUtil.checkType(expressionValue.type, clip.get().get(0));
            List<Either<String, @Value Object>> actual = clip.get().stream().filter((LoadedColumnInfo p) -> Objects.equals(p.columnName, new ColumnId("DestCol"))).findFirst().orElseThrow(RuntimeException::new).dataValues;
            TBasicUtil.assertValueListEitherEqual("Transformed", Utility.<@Value Object, Either<String, @Value Object>>mapList(expressionValue.value, x -> Either.<String, @Value Object>right(x)), actual);
            
            // If test is success, ignore exceptions (which seem to occur due to hiding error display popup):
            // Shouldn't really need this code but test is flaky without it due to some JavaFX animation-related exceptions:
            TFXUtil.sleep(2000);
            WaitForAsyncUtils.clearExceptions();
        }
        finally
        {
            Stage s = windowToUse;
            Platform.runLater(() -> s.hide());
        }
    }
}
