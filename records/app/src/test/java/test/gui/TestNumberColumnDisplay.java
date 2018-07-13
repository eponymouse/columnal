package test.gui;

import com.google.common.collect.ImmutableList;
import javafx.stage.Stage;
import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;
import records.data.ColumnId;
import records.data.KnownLengthRecordSet;
import records.data.MemoryNumericColumn;
import records.data.RecordSet;
import records.data.TableManager;
import records.data.datatype.NumberInfo;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import test.DummyManager;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.gui.FXUtility;

import static com.google.common.collect.ImmutableList.of;

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
        TestUtil.openDataAsTable(windowToUse, null, new KnownLengthRecordSet(ImmutableList.of((RecordSet rs) -> 
            new MemoryNumericColumn(rs, new ColumnId("C"), new NumberInfo(Unit.SCALAR), actualValues.stream())
        ), actualValues.size()));
        
        // TODO test GUI display
    }
    
    @Test
    public void testUnaltered() throws Exception
    {
        testNumbers(of("0.1", "1.1", "2.1"), of("0.1", "1.1", "2.1"));
    }
}
