package test.gui.transformation;

import annotation.identifier.qual.ExpressionIdentifier;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import org.junit.runner.RunWith;
import records.data.CellPosition;
import records.data.Column;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.Table;
import records.gui.MainWindow.MainWindowActions;
import records.gui.grid.RectangleBounds;
import records.transformations.HideColumns;
import test.TestUtil;
import test.gen.GenImmediateData;
import test.gen.GenImmediateData.MustIncludeNumber;
import test.gen.GenImmediateData.NumTables;
import test.gen.GenRandom;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.ListUtilTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(JUnitQuickcheck.class)
public class TestHideColumns extends FXApplicationTest implements ScrollToTrait, ClickTableLocationTrait, ListUtilTrait
{
    @Property(trials=3)
    @OnThread(Tag.Simulation)
    public void testHideColumns(@NumTables(minTables = 1, maxTables = 1) @From(GenImmediateData.class) GenImmediateData.ImmediateData_Mgr original,
            @From(GenRandom.class) Random r) throws Exception
    {
        // Save the table, then open GUI and load it, then add a filter transformation (rename to keeprows)
        MainWindowActions mainWindowActions = TestUtil.openDataAsTable(windowToUse, original.mgr).get();
        TestUtil.sleep(5000);
        Table src = mainWindowActions._test_getTableManager().getAllTables().get(0);
        RecordSet srcRS = src.getData();

        CellPosition targetPos = TestUtil.fx(() -> mainWindowActions._test_getTableManager().getNextInsertPosition(null));
        keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), targetPos);
        clickOnItemInBounds(from(TestUtil.fx(() -> mainWindowActions._test_getVirtualGrid().getNode())), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
        TestUtil.delay(100);
        clickOn(".id-new-transform");
        TestUtil.delay(100);
        scrollTo(".id-transform-hideColumns");
        clickOn(".id-transform-hideColumns");
        TestUtil.delay(100);
        write(src.getId().getRaw());
        push(KeyCode.ENTER);
        TestUtil.delay(100);

        ArrayList<ColumnId> toHide = new ArrayList<>();
        ImmutableList<ColumnId> originalColumnIds = srcRS.getColumnIds();
        ArrayList<ColumnId> columnIdsLeft = new ArrayList<>(originalColumnIds);
        int numToHide = r.nextInt(Math.min(4, columnIdsLeft.size()));
        for (int i = 0; i < numToHide; i++)
        {
            toHide.add(columnIdsLeft.remove(r.nextInt(columnIdsLeft.size())));
        }

        for (ColumnId columnId : toHide)
        {
            selectGivenListViewItem(lookup(".shown-columns-list-view").<ColumnId>queryListView(), c -> c.equals(columnId));
            clickOn(".add-button");
        }
        clickOn(".ok-button");
        sleep(500);

        HideColumns hide = (HideColumns)mainWindowActions._test_getTableManager().getAllTables().stream().filter(t -> t instanceof HideColumns).findFirst().orElseThrow(() -> new AssertionError("No HideColumns found"));
        
        assertEquals(new HashSet<>(toHide), new HashSet<>(hide.getHiddenColumns()));

        checkActualVisibleColumns(mainWindowActions, columnIdsLeft, hide);

        if (toHide.isEmpty())
            return;

        // Try editing to unhide a column and check it refreshes:
        clickOn(".edit-hide-columns");
        ColumnId unHide = toHide.remove(r.nextInt(toHide.size()));
        selectGivenListViewItem(lookup(".hidden-columns-list-view").<ColumnId>queryListView(), c -> c.equals(unHide));
        clickOn(".add-button");
        clickOn(".ok-button");
        sleep(500);
        hide = (HideColumns)mainWindowActions._test_getTableManager().getAllTables().stream().filter(t -> t instanceof HideColumns).findFirst().orElseThrow(() -> new AssertionError("No HideColumns found"));

        assertEquals(new HashSet<>(toHide), new HashSet<>(hide.getHiddenColumns()));
        columnIdsLeft = new ArrayList<>(originalColumnIds);
        columnIdsLeft.removeAll(toHide);
        checkActualVisibleColumns(mainWindowActions, columnIdsLeft, hide);
    }

    private void checkActualVisibleColumns(MainWindowActions mainWindowActions, ArrayList<ColumnId> columnIdsLeft, HideColumns hide)
    {
        if (columnIdsLeft.isEmpty())
        {
            fail("TODO check for no-columns error");
        }
        else
        {
            for (int i = 0; i < columnIdsLeft.size(); i++)
            {
                @SuppressWarnings("nullness")
                CellPosition pos = TestUtil.fx(() -> hide.getDisplay().getMostRecentPosition()).offsetByRowCols(1, i);
                ColumnId columnId = columnIdsLeft.get(i);
                keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), pos);
                withItemInBounds(lookup(".table-display-column-title"), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(pos, pos), (n, p) -> assertEquals(columnId.getRaw(), ((Label)n).getText()));
                
            }
        }
    }
}
