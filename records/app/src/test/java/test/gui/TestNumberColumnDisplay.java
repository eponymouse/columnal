package test.gui;

import com.google.common.collect.ImmutableList;
import javafx.stage.Stage;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;
import records.data.CellPosition;
import records.data.ColumnId;
import records.data.KnownLengthRecordSet;
import records.data.MemoryNumericColumn;
import records.data.RecordSet;
import records.data.TableManager;
import records.data.datatype.NumberInfo;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.gui.DataCellSupplier.VersionedSTF;
import records.gui.MainWindow.MainWindowActions;
import test.DummyManager;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.gui.FXUtility;

import static com.google.common.collect.ImmutableList.of;
import static org.junit.Assert.assertEquals;

@OnThread(Tag.Simulation)
public class TestNumberColumnDisplay extends ApplicationTest
{
    @OnThread(Tag.Any)
    @SuppressWarnings("nullness")
    private Stage windowToUse;

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void start(Stage stage) throws Exception
    {
        this.windowToUse = stage;
        FXUtility._test_setTestingMode();
    }
    
    /**
     * Given a list of numbers (as strings), displays them in GUI and checks that:
     * (a) they all match the given truncated list when unfocused
     * (b) they switch to the full number when focused
     */
    private void testNumbers(ImmutableList<String> actualValues, ImmutableList<String> expectedGUI) throws Exception
    {
        MainWindowActions mwa = TestUtil.openDataAsTable(windowToUse, null, new KnownLengthRecordSet(ImmutableList.of((RecordSet rs) -> 
            new MemoryNumericColumn(rs, new ColumnId("C"), new NumberInfo(Unit.SCALAR), actualValues.stream())
        ), actualValues.size()));
        
        TestUtil.sleep(2000);

        for (int i = 0; i < expectedGUI.size(); i++)
        {
            final int iFinal = i;
            @Nullable String cellText = TestUtil.<@Nullable String>fx(() -> {
                @Nullable VersionedSTF cell = mwa._test_getDataCell(new CellPosition(CellPosition.row(3 + iFinal), CellPosition.col(0)));
                if (cell != null)
                    return cell.getText();
                else
                    return null;
            });
            assertEquals("Row " + i, expectedGUI.get(i), cellText);
        }
    }
    
    @Test
    public void testUnaltered() throws Exception
    {
        testNumbers(of("0.1", "1.1", "2.1"), of("0.1", "1.1", "2.1"));
    }

    // Note: need to have stylesheets in place or this will fail.
    @Test
    public void testAllTruncated() throws Exception
    {
        testNumbers(of("0.112233445566778899", "1.112233445566778899", "2.112233445566778899"), of("0.11223344\u2026", "1.11223344\u2026", "2.11223344\u2026"));
    }

    @Test
    public void testSomeTruncated() throws Exception
    {
        testNumbers(of("0.112233445566778899", "1.112233445", "2.1122334400", "3.11223344"), of("0.11223344\u2026", "1.112233445", "2.11223344", "3.11223344"));
    }
}
