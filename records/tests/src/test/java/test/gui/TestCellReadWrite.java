package test.gui;

import annotation.qual.Value;
import annotation.units.TableDataColIndex;
import annotation.units.TableDataRowIndex;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import log.Log;
import org.apache.commons.lang3.SystemUtils;
import org.junit.runner.RunWith;
import records.data.CellPosition;
import records.data.DataItemPosition;
import records.data.Table;
import records.data.TableId;
import records.data.TableManager;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.error.UserException;
import records.gui.MainWindow.MainWindowActions;
import records.gui.grid.VirtualGrid;
import test.TestUtil;
import test.gen.GenImmediateData;
import test.gen.GenImmediateData.NumTables;
import test.gen.GenRandom;
import test.gen.GenValueSpecifiedType;
import test.gui.trait.EnterStructuredValueTrait;
import test.gui.trait.FocusOwnerTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;

@RunWith(JUnitQuickcheck.class)
public class TestCellReadWrite extends FXApplicationTest implements ScrollToTrait, FocusOwnerTrait, EnterStructuredValueTrait
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

        MainWindowActions details = TestUtil.openDataAsTable(windowToUse, src.mgr).get();
        TestUtil.sleep(1000);
        tableManager = details._test_getTableManager();
        virtualGrid = details._test_getVirtualGrid();
        List<Table> allTables = tableManager.getAllTables();
        
        // Pick some random locations in random tables, scroll there, copy data and check value:
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
            keyboardMoveTo(virtualGrid, tableManager, table.getId(), row, column);
            // Clear clipboard to prevent tests interfering:
            TestUtil.fx_(() -> Clipboard.getSystemClipboard().setContent(Collections.singletonMap(DataFormat.PLAIN_TEXT, "@TEST")));
            pushCopy();
            // Need to wait for hop to simulation thread and back:
            TestUtil.delay(2000);
            String copiedFromTable = TestUtil.fx(() -> Clipboard.getSystemClipboard().getString());
            DataTypeValue columnDTV = table.getData().getColumns().get(column).getType();
            String valueFromData = DataTypeUtility.valueToString(columnDTV, columnDTV.getCollapsed(row), null);
            assertEquals(valueFromData, copiedFromTable);
        }
    }

    @Property(trials = 3)
    @OnThread(Tag.Simulation)
    public void propCheckDataWrite(
            @NumTables(minTables = 3, maxTables = 5) @From(GenImmediateData.class) GenImmediateData.ImmediateData_Mgr src,
            @From(GenRandom.class) Random r,
            @From(GenValueSpecifiedType.class) GenValueSpecifiedType.ValueGenerator valueGenerator) throws Exception
    {

        MainWindowActions details = TestUtil.openDataAsTable(windowToUse, src.mgr).get();
        TestUtil.sleep(3000);
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
        }
        Map<Location, String> writtenData = new HashMap<>();

        // Pick some random locations in random tables, scroll there, copy data and check value:
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
            keyboardMoveTo(virtualGrid, tableManager, table.getId(), row, column);
            push(KeyCode.ENTER);
            DataTypeValue columnDTV = table.getData().getColumns().get(column).getType();
            Log.debug("Making value for type " + columnDTV);
            @Value Object value = valueGenerator.makeValue(table.getData().getColumns().get(column).getType());
            enterStructuredValue(columnDTV, value, r, false);
            push(KeyCode.ESCAPE);

            Log.debug("Intending to copy column " + table.getData().getColumns().get(column).getName() + " from position " + row + ", " + column);
            // Clear clipboard to prevent tests interfering:
            TestUtil.fx_(() -> Clipboard.getSystemClipboard().setContent(Collections.singletonMap(DataFormat.PLAIN_TEXT, "@TEST")));
            pushCopy();
            // Need to wait for hop to simulation thread and back:
            TestUtil.delay(10000);
            String copiedFromTable = TestUtil.fx(() -> Clipboard.getSystemClipboard().getString());
            
            String valueEntered = DataTypeUtility.valueToString(columnDTV, value, null);
            assertEquals(valueEntered, copiedFromTable);
            writtenData.put(new Location(table.getId(), row, column), valueEntered);
        }
        
        // Go back to the data we wrote, and check the cells retained the value:
        writtenData.forEach((target, written) -> {
            try
            {
                keyboardMoveTo(virtualGrid, tableManager, target.tableId, target.row, target.col);
                TestUtil.fx_(() -> Clipboard.getSystemClipboard().setContent(Collections.singletonMap(DataFormat.PLAIN_TEXT, "@TEST")));
                pushCopy();
                // Need to wait for hop to simulation thread and back:
                TestUtil.delay(2000);
                String copiedFromTable = TestUtil.fx(() -> Clipboard.getSystemClipboard().getString());
                assertEquals(written, copiedFromTable);
            }
            catch (UserException e)
            {
                throw new RuntimeException(e);
            }
        });
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
            push(TestUtil.ctrlCmd(), KeyCode.C);
    }
}
