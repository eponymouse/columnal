package test.gui;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import log.Log;
import org.apache.commons.lang3.SystemUtils;
import org.junit.runner.RunWith;
import org.testfx.framework.junit.ApplicationTest;
import records.data.CellPosition;
import records.data.Table;
import records.data.TableManager;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.gui.MainWindow.MainWindowActions;
import records.gui.grid.VirtualGrid;
import test.DataEntryUtil;
import test.TestUtil;
import test.gen.GenImmediateData;
import test.gen.GenImmediateData.NumTables;
import test.gen.GenRandom;
import test.gen.GenValueSpecifiedType;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;

@RunWith(JUnitQuickcheck.class)
public class TestCellReadWrite extends ApplicationTest implements ScrollToTrait, FocusOwnerTrait
{
    @OnThread(Tag.Any)
    @SuppressWarnings("nullness")
    private Stage windowToUse;

    @OnThread(Tag.Any)
    @SuppressWarnings("nullness")
    private VirtualGrid virtualGrid;
    @OnThread(Tag.Any)
    @SuppressWarnings("nullness")
    private TableManager tableManager;

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void start(Stage stage) throws Exception
    {
        this.windowToUse = stage;
        FXUtility._test_setTestingMode();
    }

    @Property(trials = 3)
    @OnThread(Tag.Simulation)
    public void propCheckDataRead(
            @When(seed=1L) @NumTables(minTables = 2, maxTables = 4) @From(GenImmediateData.class) GenImmediateData.ImmediateData_Mgr src,
            @When(seed=1L) @From(GenRandom.class) Random r) throws Exception
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
            int column = r.nextInt(table.getData().getColumns().size());
            int tableLen = table.getData().getLength();
            if (tableLen == 0)
                continue;
            int row = r.nextInt(tableLen);
            keyboardMoveTo(virtualGrid, TestUtil.checkNonNull(TestUtil.fx(() -> table.getDisplay())).getMostRecentPosition().offsetByRowCols(row + 3, column));
            // Clear clipboard to prevent tests interfering:
            TestUtil.fx_(() -> Clipboard.getSystemClipboard().setContent(Collections.singletonMap(DataFormat.PLAIN_TEXT, "@TEST")));
            pushCopy();
            String copiedFromTable = TestUtil.fx(() -> Clipboard.getSystemClipboard().getString());
            DataTypeValue columnDTV = table.getData().getColumns().get(column).getType();
            String valueFromData = DataTypeUtility.valueToString(columnDTV, columnDTV.getCollapsed(row), null);
            assertEquals(valueFromData, copiedFromTable);
        }
    }

    @Property(trials = 3)
    @OnThread(Tag.Simulation)
    public void propCheckDataWrite(
            @When(seed=2L) @NumTables(minTables = 3, maxTables = 5) @From(GenImmediateData.class) GenImmediateData.ImmediateData_Mgr src,
            @When(seed=2L) @From(GenRandom.class) Random r,
            @When(seed=2L) @From(GenValueSpecifiedType.class) GenValueSpecifiedType.ValueGenerator valueGenerator) throws Exception
    {

        MainWindowActions details = TestUtil.openDataAsTable(windowToUse, src.mgr).get();
        TestUtil.sleep(3000);
        tableManager = details._test_getTableManager();
        virtualGrid = details._test_getVirtualGrid();
        List<Table> allTables = tableManager.getAllTables();
        
        Map<CellPosition, String> writtenData = new HashMap<>();

        // Pick some random locations in random tables, scroll there, copy data and check value:
        for (int i = 0; i < 8; i++)
        {
            // Random table:
            Table table = pickRandomTable(r, allTables);
            // Random location in table:
            int column = r.nextInt(table.getData().getColumns().size());
            int tableLen = table.getData().getLength();
            if (tableLen == 0)
                continue;
            int row = r.nextInt(tableLen);
            CellPosition target = TestUtil.checkNonNull(TestUtil.fx(() -> table.getDisplay())).getMostRecentPosition().offsetByRowCols(row + 3, column);
            keyboardMoveTo(virtualGrid, target);
            push(KeyCode.ENTER);
            DataTypeValue columnDTV = table.getData().getColumns().get(column).getType();
            Log.debug("Making value for type " + columnDTV);
            @Value Object value = valueGenerator.makeValue(table.getData().getColumns().get(column).getType());
            DataEntryUtil.enterValue(this, r, columnDTV, value, false);
            push(KeyCode.ENTER);

            Log.debug("Intending to copy column " + table.getData().getColumns().get(column).getName() + " from position " + target);
            // Clear clipboard to prevent tests interfering:
            TestUtil.fx_(() -> Clipboard.getSystemClipboard().setContent(Collections.singletonMap(DataFormat.PLAIN_TEXT, "@TEST")));
            pushCopy();
            String copiedFromTable = TestUtil.fx(() -> Clipboard.getSystemClipboard().getString());
            
            String valueEntered = DataTypeUtility.valueToString(columnDTV, value, null);
            assertEquals(valueEntered, copiedFromTable);
            writtenData.put(target, valueEntered);
        }
        
        // Go back to the data we wrote, and check the cells retained the value:
        writtenData.forEach((target, written) -> {
            keyboardMoveTo(virtualGrid, target);
            TestUtil.fx_(() -> Clipboard.getSystemClipboard().setContent(Collections.singletonMap(DataFormat.PLAIN_TEXT, "@TEST")));
            pushCopy();
            String copiedFromTable = TestUtil.fx(() -> Clipboard.getSystemClipboard().getString());
            assertEquals(written, copiedFromTable);
        });
    }

    @OnThread(Tag.Any)
    public Table pickRandomTable(@From(GenRandom.class) @When(seed = 2L) Random r, List<Table> allTables)
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
