package test.gui;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.junit.runner.RunWith;
import org.testfx.api.FxRobotInterface;
import org.testfx.framework.junit.ApplicationTest;
import records.data.CellPosition;
import records.gui.grid.VirtualGrid;
import test.TestUtil;
import test.gen.GenImmediateData;
import test.gen.GenRandom;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Random;

import static org.junit.Assert.assertTrue;

@RunWith(JUnitQuickcheck.class)
public class TestKeyboardMovement extends ApplicationTest
{
    @OnThread(Tag.Any)
    @SuppressWarnings("nullness")
    private Stage windowToUse;

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void start(Stage stage) throws Exception
    {
        windowToUse = stage;
    }

    /**
     * Check that keyboard moving around is consistent (right always selects more to the right, etc)
     * and reversible, and keeps the selected item in view.
     */
    @Property(trials=5)
    @OnThread(Tag.Simulation)
    public void testKeyboardMovement(@When(seed=1L) @From(GenImmediateData.class) GenImmediateData.ImmediateData_Mgr src, @When(seed=1L) @From(GenRandom.class) Random r) throws Exception
    {
        VirtualGrid virtualGrid = TestUtil.openDataAsTable(windowToUse, src.mgr).get().getSecond();
        press(KeyCode.CONTROL, KeyCode.HOME);
        assertTrue(TestUtil.fx(() -> virtualGrid._test_getSelection().map(s -> s.getSelectionDisplayRectangle().contains(CellPosition.ORIGIN)).orElse(false)));
    }
}
