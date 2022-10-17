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

package test.gui;

import annotation.qual.Value;
import annotation.units.TableDataColIndex;
import annotation.units.TableDataRowIndex;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.Node;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import org.junit.Assert;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.log.Log;
import org.apache.commons.lang3.SystemUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.runner.RunWith;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.Column;
import xyz.columnal.id.DataItemPosition;
import xyz.columnal.data.Table;
import xyz.columnal.id.TableId;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.error.UserException;
import xyz.columnal.gui.MainWindow.MainWindowActions;
import xyz.columnal.gui.grid.RectangleBounds;
import xyz.columnal.gui.grid.VirtualGrid;
import test.gen.GenImmediateData;
import test.gen.GenImmediateData.NumTables;
import test.gen.GenRandom;
import test.gen.GenValueSpecifiedType;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.EnterStructuredValueTrait;
import test.gui.trait.FocusOwnerTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(JUnitQuickcheck.class)
public class TestCellReadWrite extends FXApplicationTest implements ScrollToTrait, FocusOwnerTrait, EnterStructuredValueTrait, ClickTableLocationTrait
{
    @OnThread(Tag.Any)
    @SuppressWarnings("nullness")
    private VirtualGrid virtualGrid;
    @OnThread(Tag.Any)
    @SuppressWarnings("nullness")
    private TableManager tableManager;

    @Property(trials = 3)
    @OnThread(Tag.Simulation)
    public void propCheckDataRead(
            @NumTables(minTables = 2, maxTables = 4) @From(GenImmediateData.class) GenImmediateData.ImmediateData_Mgr src,
            @From(GenRandom.class) Random r) throws Exception
    {
        System.out.println("propCheckDataRead: opening table");
        System.out.flush();
        MainWindowActions details = TAppUtil.openDataAsTable(windowToUse, src.mgr).get();
        TFXUtil.sleep(1000);
        tableManager = details._test_getTableManager();
        virtualGrid = details._test_getVirtualGrid();
        List<Table> allTables = tableManager.getAllTables();
        
        // Pick some random locations in random tables, scroll there, copy data and check value:
        for (int i = 0; i < 5; i++)
        {
            // Random table:
            Table table = pickRandomTable(r, allTables);
            System.out.println("Copying from table: " + table.getId());
            // Random location in table:
            @TableDataColIndex int column = DataItemPosition.col(r.nextInt(table.getData().getColumns().size()));
            int tableLen = table.getData().getLength();
            if (tableLen == 0)
                continue;
            @TableDataRowIndex int row = DataItemPosition.row(r.nextInt(tableLen));
            CellPosition pos = keyboardMoveTo(virtualGrid, tableManager, table.getId(), row, column);
            // Clear clipboard to prevent tests interfering:
            TFXUtil.fx_(() -> Clipboard.getSystemClipboard().setContent(Collections.singletonMap(DataFormat.PLAIN_TEXT, "@TEST")));
            pushCopy();
            // Need to wait for hop to simulation thread and back:
            TFXUtil.sleep(2000);
            String copiedFromTable = TFXUtil.<@Nullable String>fx(() -> Clipboard.getSystemClipboard().getString());
            DataTypeValue columnDTV = table.getData().getColumns().get(column).getType();
            String valueFromData = DataTypeUtility.valueToString(columnDTV.getCollapsed(row));
            assertEquals("Location " + pos + " row : " + row + " col: " + column, valueFromData, copiedFromTable);
        }
    }

    @Property(trials = 3)
    @OnThread(Tag.Simulation)
    public void propCheckDataDelete(
            @NumTables(minTables = 2, maxTables = 4) @From(GenImmediateData.class) GenImmediateData.ImmediateData_Mgr src,
            @From(GenRandom.class) Random r) throws Exception
    {

        MainWindowActions details = TAppUtil.openDataAsTable(windowToUse, src.mgr).get();
        TFXUtil.sleep(1000);
        tableManager = details._test_getTableManager();
        virtualGrid = details._test_getVirtualGrid();
        List<Table> allTables = tableManager.getAllTables();

        // Pick some random locations in random tables, scroll there, press delete and check value:
        for (int i = 0; i < 5; i++)
        {
            // Random table:
            Table table = pickRandomTable(r, allTables);
            // Random location in table:
            @TableDataColIndex int column = DataItemPosition.col(r.nextInt(table.getData().getColumns().size()));
            int tableLen = table.getData().getLength();
            if (tableLen == 0)
                continue;
            @TableDataRowIndex int row = DataItemPosition.row(r.nextInt(tableLen));
            CellPosition pos = keyboardMoveTo(virtualGrid, tableManager, table.getId(), row, column);
            // Clear clipboard to prevent tests interfering:
            TFXUtil.fx_(() -> Clipboard.getSystemClipboard().setContent(Collections.singletonMap(DataFormat.PLAIN_TEXT, "@TEST")));
            push(r.nextBoolean() ? KeyCode.BACK_SPACE : KeyCode.DELETE);
            sleep(200);
            pushCopy();
            // Need to wait for hop to simulation thread and back:
            TFXUtil.sleep(2000);
            String copiedFromTable = TFXUtil.<@Nullable String>fx(() -> Clipboard.getSystemClipboard().getString());
            Column col = table.getData().getColumns().get(column);
            DataTypeValue columnDTV = col.getType();
            String valueFromData = DataTypeUtility.valueToString(TBasicUtil.checkNonNull(col.getDefaultValue()));
            assertEquals("Location " + pos + " row : " + row + " col: " + column,valueFromData, copiedFromTable);
        }
    }

    @Property(trials = 3)
    @OnThread(Tag.Simulation)
    public void propCheckDataWrite(
            @NumTables(minTables = 3, maxTables = 5) @From(GenImmediateData.class) GenImmediateData.ImmediateData_Mgr src,
            @From(GenRandom.class) Random r,
            @From(GenValueSpecifiedType.class) GenValueSpecifiedType.ValueGenerator valueGenerator) throws Exception
    {

        MainWindowActions details = TAppUtil.openDataAsTable(windowToUse, src.mgr).get();
        TFXUtil.sleep(3000);
        tableManager = details._test_getTableManager();
        virtualGrid = details._test_getVirtualGrid();
        List<Table> allTables = tableManager.getAllTables();
        
        @OnThread(Tag.Any)
        class Location
        {
            private final TableId tableId;
            private final @TableDataRowIndex int row;
            private final @TableDataColIndex int col;

            public Location(TableId tableId, @TableDataRowIndex int row, @TableDataColIndex int col)
            {
                this.tableId = tableId;
                this.row = row;
                this.col = col;
            }

            @Override
            public boolean equals(@Nullable Object o)
            {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                Location location = (Location) o;
                return row == location.row &&
                    col == location.col &&
                    tableId.equals(location.tableId);
            }

            @Override
            public int hashCode()
            {
                return Objects.hash(tableId, row, col);
            }

            @Override
            public String toString()
            {
                return "Location{" +
                    "tableId=" + tableId +
                    ", row=" + row +
                    ", col=" + col +
                    '}';
            }
        }
        // The String content, and boolean for error (true means has error)
        Map<Location, Pair<String, Boolean>> writtenData = new HashMap<>();

        // Pick some random locations in random tables, scroll there, write new data:
        for (int i = 0; i < 8; i++)
        {
            // Random table:
            Table table = pickRandomTable(r, allTables);
            // Random location in table:
            @TableDataColIndex int column = DataItemPosition.col(r.nextInt(table.getData().getColumns().size()));
            int tableLen = table.getData().getLength();
            if (tableLen == 0)
                continue;
            @TableDataRowIndex int row = DataItemPosition.row(r.nextInt(tableLen));
            // Move to the location and edit:
            CellPosition target = keyboardMoveTo(virtualGrid, tableManager, table.getId(), row, column);
            push(KeyCode.ENTER);
            DataTypeValue columnDTV = table.getData().getColumns().get(column).getType();
            Log.debug("Making value for type " + columnDTV);
            Either<String, @Value Object> value;
            DataType type = table.getData().getColumns().get(column).getType().getType();
            if (r.nextInt(5) == 1 && !type.equals(DataType.TEXT))
            {
                value = Either.left("#" + r.nextInt());
            }
            else
            {
                
                value = Either.right(valueGenerator.makeValue(type));
            }
            value.eitherEx_(str -> {
                push(KeyCode.SHORTCUT, KeyCode.A);
                write(str);
            }, val -> {
                enterStructuredValue(columnDTV.getType(), val, r, true, false);
            });
            push(KeyCode.ENTER);
            push(KeyCode.UP);

            Log.debug("Intending to copy column " + table.getData().getColumns().get(column).getName() + " from position " + row + ", " + column);
            // Clear clipboard to prevent tests interfering:
            TFXUtil.fx_(() -> Clipboard.getSystemClipboard().setContent(Collections.singletonMap(DataFormat.PLAIN_TEXT, "@TEST")));
            pushCopy();
            // Need to wait for hop to simulation thread and back:
            TFXUtil.sleep(10000);
            String copiedFromTable = TFXUtil.<@Nullable String>fx(() -> Clipboard.getSystemClipboard().getString());
            
            String valueEntered = value.eitherEx(s -> "@INVALID\"" + s + "\"", v -> DataTypeUtility.valueToString(v));
            assertEquals(valueEntered, copiedFromTable);
            writtenData.put(new Location(table.getId(), row, column), new Pair<>(valueEntered, value.isLeft()));
            assertErrorShowing(target, value.isLeft());
        }
        
        // Go back to the data we wrote, and check the cells retained the value:
        writtenData.forEach((target, written) -> {
            try
            {
                CellPosition cellPos = keyboardMoveTo(virtualGrid, tableManager, target.tableId, target.row, target.col);
                assertErrorShowing(cellPos, written.getSecond());
                TFXUtil.fx_(() -> Clipboard.getSystemClipboard().setContent(Collections.singletonMap(DataFormat.PLAIN_TEXT, "@TEST")));
                pushCopy();
                // Need to wait for hop to simulation thread and back:
                TFXUtil.sleep(2000);
                String copiedFromTable = TFXUtil.<@Nullable String>fx(() -> Clipboard.getSystemClipboard().getString());
                Assert.assertEquals("Position " + target, written.getFirst(), copiedFromTable);
            }
            catch (UserException e)
            {
                throw new RuntimeException(e);
            }
        });
    }

    @Property(trials = 3)
    @OnThread(Tag.Simulation)
    public void propCheckDataUndo(
            @NumTables(minTables = 2, maxTables = 4) @From(GenImmediateData.class) GenImmediateData.ImmediateData_Mgr src,
            @From(GenRandom.class) Random r) throws Exception
    {
        MainWindowActions details = TAppUtil.openDataAsTable(windowToUse, src.mgr).get();
        TFXUtil.sleep(1000);
        tableManager = details._test_getTableManager();
        virtualGrid = details._test_getVirtualGrid();
        List<Table> allTables = tableManager.getAllTables();

        // Pick some random locations in random tables, scroll there, write text, then escape or undo:
        for (int i = 0; i < 5; i++)
        {
            // Random table:
            Table table = pickRandomTable(r, allTables);
            // Random location in table:
            @TableDataColIndex int column = DataItemPosition.col(r.nextInt(table.getData().getColumns().size()));
            int tableLen = table.getData().getLength();
            if (tableLen == 0)
                continue;
            Column col = table.getData().getColumns().get(column);
            @TableDataRowIndex int row = DataItemPosition.row(r.nextInt(tableLen));
            CellPosition pos = keyboardMoveTo(virtualGrid, tableManager, table.getId(), row, column);
            String valueFromData = DataTypeUtility.valueToString(col.getType().getCollapsed(row));
            // Clear clipboard to prevent tests interfering:
            TFXUtil.fx_(() -> Clipboard.getSystemClipboard().setContent(Collections.singletonMap(DataFormat.PLAIN_TEXT, "@TEST")));
            // Check original has the right string:
            pushCopy();
            // Need to wait for hop to simulation thread and back:
            TFXUtil.sleep(2000);
            String copiedFromTable = TFXUtil.<@Nullable String>fx(() -> Clipboard.getSystemClipboard().getString());
            assertEquals("Location " + pos + " row : " + row + " col: " + column, valueFromData, copiedFromTable);
            // Now start editing:
            write("" + r.nextLong());
            
            boolean escape = r.nextBoolean();
            if (escape)
            {
                // Press escape:
                push(KeyCode.ESCAPE);
            }
            else
            {
                push(KeyCode.SHORTCUT, KeyCode.Z);
                push(KeyCode.ENTER);
                push(KeyCode.UP);
            }
            sleep(200);
            pushCopy();
            // Need to wait for hop to simulation thread and back:
            TFXUtil.sleep(2000);
            copiedFromTable = TFXUtil.<@Nullable String>fx(() -> Clipboard.getSystemClipboard().getString());
            
            valueFromData = DataTypeUtility.valueToString(col.getType().getCollapsed(row));
            assertEquals("Location " + pos + " row : " + row + " col: " + column + " escape: " + escape, valueFromData, copiedFromTable);
        }
    }

    @OnThread(Tag.Any)
    private void assertErrorShowing(CellPosition cellPos, boolean expectError)
    {
        Node field = withItemInBounds(".document-text-field", virtualGrid, new RectangleBounds(cellPos, cellPos), (n, p) -> {});
        Assert.assertEquals(expectError, TFXUtil.fx(() -> FXUtility.hasPseudoclass(field, "has-error")));
    }

    @OnThread(Tag.Any)
    public Table pickRandomTable(Random r, List<Table> allTables)
    {
        allTables = new ArrayList<>(allTables);
        Collections.sort(allTables, Comparator.comparing(t -> t.getId().getRaw()));
        return allTables.get(r.nextInt(allTables.size()));
    }

    @OnThread(Tag.Any)
    public void pushCopy()
    {
        if (SystemUtils.IS_OS_MAC_OSX)
            push(KeyCode.F11);
        else
            push(TFXUtil.ctrlCmd(), KeyCode.C);
    }
}
