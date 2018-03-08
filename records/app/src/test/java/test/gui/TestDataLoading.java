package test.gui;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.junit.runner.RunWith;
import org.testfx.framework.junit.ApplicationTest;
import records.data.Table;
import records.data.TableManager;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.gui.grid.VirtualGrid;
import test.TestUtil;
import test.gen.GenImmediateData;
import test.gen.GenImmediateData.NumTables;
import test.gen.GenRandom;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.gui.FXUtility;

import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;

@RunWith(JUnitQuickcheck.class)
public class TestDataLoading extends ApplicationTest implements ScrollToTrait
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

    @Property(trials = 5)
    @OnThread(Tag.Simulation)
    public void propCheckData(
            @When(seed=1L) @NumTables(minTables = 3, maxTables = 5) @From(GenImmediateData.class) GenImmediateData.ImmediateData_Mgr src,
            @When(seed=1L) @From(GenRandom.class) Random r) throws Exception
    {

        Pair<TableManager, VirtualGrid> details = TestUtil.openDataAsTable(windowToUse, src.mgr).get();
        TestUtil.sleep(1000);
        tableManager = details.getFirst();
        virtualGrid = details.getSecond();
        List<Table> allTables = tableManager.getAllTables();
        
        // Pick some random locations in random tables, scroll there, copy data and check value:
        for (int i = 0; i < 8; i++)
        {
            // Random table:
            Table table = allTables.get(r.nextInt(allTables.size()));
            // Random location in table:
            int column = r.nextInt(table.getData().getColumns().size());
            int tableLen = table.getData().getLength();
            if (tableLen == 0)
                continue;
            int row = r.nextInt(tableLen);
            keyboardMoveTo(virtualGrid, TestUtil.checkNonNull(TestUtil.fx(() -> table.getDisplay())).getMostRecentPosition().offsetByRowCols(row + 3, column));
            // Clear clipboard to prevent tests interfering:
            TestUtil.fx_(() -> Clipboard.getSystemClipboard().setContent(Collections.singletonMap(DataFormat.PLAIN_TEXT, "@TEST")));
            push(KeyCode.CONTROL, KeyCode.C);
            String copiedFromTable = TestUtil.fx(() -> Clipboard.getSystemClipboard().getString());
            DataTypeValue columnDTV = table.getData().getColumns().get(column).getType();
            String valueFromData = DataTypeUtility.valueToString(columnDTV, columnDTV.getCollapsed(row), null);
            assertEquals(valueFromData, copiedFromTable);
        }
    }
    
    // TODO also have a test for data saving
}
