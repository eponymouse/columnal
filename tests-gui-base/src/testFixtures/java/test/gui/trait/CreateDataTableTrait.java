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

package test.gui.trait;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import org.testfx.api.FxRobotInterface;
import test.gui.TFXUtil;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.Column;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.id.TableId;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.error.InvalidImmediateValueException;
import xyz.columnal.gui.MainWindow.MainWindowActions;
import xyz.columnal.gui.grid.RectangleBounds;
import xyz.columnal.gui.grid.VirtualGrid;
import xyz.columnal.importers.ClipboardUtils;
import xyz.columnal.importers.ClipboardUtils.RowRange;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertTrue;

public interface CreateDataTableTrait extends FxRobotInterface, ScrollToTrait, ClickTableLocationTrait, FocusOwnerTrait, EnterTypeTrait, PopupTrait, TextFieldTrait
{
    public static class ColumnDetails
    {
        public final ColumnId name;
        public final DataType dataType;
        public final ImmutableList<Either<String, @Value Object>> data;

        public ColumnDetails(ColumnId name, DataType dataType, ImmutableList<Either<String, @Value Object>> data)
        {
            this.name = name;
            this.dataType = dataType;
            this.data = data;
        }
    }
    
    @OnThread(Tag.Simulation)
    public default void createDataTable(MainWindowActions mainWindowActions, CellPosition targetPos, @ExpressionIdentifier String tableName, List<ColumnDetails> columns) throws Exception
    {
        VirtualGrid virtualGrid = mainWindowActions._test_getVirtualGrid();
        keyboardMoveTo(virtualGrid, targetPos);
        
        /* TODO support other methods of table creation
        
        // Only need to click once as already selected by keyboard:
        clickOnItemInBounds(lookup(".create-table-grid-button"), virtualGrid, new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
        correctTargetWindow().clickOn(".id-new-data");
        correctTargetWindow();
        write(tableName, 1);
        push(KeyCode.TAB);
        // Add first column in this dialog:
        write(columns.get(0).name.getRaw(), 1);
        push(KeyCode.TAB);
        enterType(TypeExpression.fromDataType(columns.get(0).dataType), new Random(1));
        moveAndDismissPopupsAtPos(point(".ok-button"));
        clickOn(".ok-button");
        TestUtil.delay(200);
        
        
        // Now need to add other columns, then put the data in clipboard and paste in.

        // Start at one, since we already added first column:
        for (int i = 1; i < columns.size(); i++)
        {
            clickOnItemInBounds(lookup(".expand-arrow"),virtualGrid, new RectangleBounds(targetPos.offsetByRowCols(2, 1), targetPos.offsetByRowCols(2, 1)));
            TestUtil.sleep(500);
            write(columns.get(i).name.getRaw(), 1);
            push(KeyCode.TAB);
            enterType(TypeExpression.fromDataType(columns.get(i).dataType), new Random(i));
            moveAndDismissPopupsAtPos(point(".ok-button"));
            clickOn(".ok-button");
            TestUtil.delay(500);
        }
        */

        CompletableFuture<Boolean> copyDone = new CompletableFuture<>();
        @SuppressWarnings("units")
        RowRange rowRange = new RowRange(0, columns.get(0).data.size() - 1);
        Platform.runLater(() -> {
            try
            {
                ClipboardUtils.copyValuesToClipboard(mainWindowActions._test_getTableManager().getUnitManager(), mainWindowActions._test_getTableManager().getTypeManager(), Utility.mapListExI(columns, c -> new Pair<>(c.name, c.dataType.fromCollapsed((i, prog) -> c.data.get(i).<@Value Object>eitherEx(invalid -> {throw new InvalidImmediateValueException(StyledString.s("cdtt_err"), invalid);
                }, valid -> valid)))), () -> rowRange, copyDone);
            }
            catch (Exception e)
            {
                copyDone.complete(false);
            }
        });
        
        boolean successfulCopy = copyDone.get();
        assertTrue(successfulCopy);
        
        // Now all columns should be in place, select table and hit paste:
        
        keyboardMoveTo(virtualGrid, targetPos);
        push(KeyCode.SHORTCUT, KeyCode.V);
        TFXUtil.sleep(3000);
        // Enter table name:
        clickOnItemInBounds(".table-display-table-title .table-name-text-field", virtualGrid, new RectangleBounds(targetPos, targetPos));
        selectAllCurrentTextField();
        write(tableName, 1);
        push(KeyCode.ENTER);
        // Give time for rename:
        TFXUtil.sleep(3000);

        // Double check the values via direct access:
        List<Column> actualColumns = mainWindowActions._test_getTableManager().getSingleTableOrThrow(new TableId(tableName)).getData().getColumns();

        for (int i = 0; i < columns.size(); i++)
        {
            TBasicUtil.assertValueListEitherEqual("Column " + columns.get(i).name, columns.get(i).data, TBasicUtil.getAllCollapsedData(actualColumns.get(i).getType(), columns.get(i).data.size()));
        }
    }
}
