package test.gui;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import log.Log;
import org.junit.runner.RunWith;
import org.testfx.framework.junit.ApplicationTest;
import records.data.CellPosition;
import records.data.ColumnId;
import records.data.Transformation;
import records.gui.MainWindow.MainWindowActions;
import records.gui.View;
import records.gui.grid.RectangleBounds;
import records.importers.ClipboardUtils;
import records.transformations.Calculate;
import records.transformations.TransformationInfo;
import records.transformations.expression.*;
import test.TestUtil;
import test.gen.ExpressionValue;
import test.gen.GenExpressionValueBackwards;
import test.gen.GenExpressionValueForwards;
import test.gen.GenRandom;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(JUnitQuickcheck.class)
@OnThread(Tag.Simulation)
public class TestExpressionEditor extends ApplicationTest implements ListUtilTrait, ScrollToTrait, EnterExpressionTrait, ClickTableLocationTrait
{
    @SuppressWarnings("nullness")
    private Stage windowToUse;

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void start(Stage stage) throws Exception
    {
        this.windowToUse = stage;
    }

    // TODO this test seems to cause later tests to fail
    @Property(trials = 10)
    public void testEntry(@When(seed=1L) @From(GenExpressionValueForwards.class) @From(GenExpressionValueBackwards.class) ExpressionValue expressionValue, @When(seed=1L) @From(GenRandom.class) Random r) throws Exception
    {
        MainWindowActions mainWindowActions = TestUtil.openDataAsTable(windowToUse, expressionValue.typeManager, expressionValue.recordSet);
        try
        {
            Region gridNode = TestUtil.fx(() -> mainWindowActions._test_getVirtualGrid().getNode());
            CellPosition targetPos = new CellPosition(CellPosition.row(6), CellPosition.col(3));
            for (int i = 0; i < 2; i++)
                clickOnItemInBounds(from(gridNode), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
            // Not sure why this doesn't work:
            //clickOnItemInBounds(lookup(".create-table-grid-button"), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
            clickOn(".id-new-transform");
            clickOn(".id-transform-calculate");
            write("Table1");
            push(KeyCode.ENTER);
            TestUtil.sleep(200);
            write("DestCol");
            // Focus expression editor:
            push(KeyCode.TAB);
            Log.normal("Entering expression:\n" + expressionValue.expression.toString() + "\n");
            enterExpression(expressionValue.expression, false, r);
            // Finish any final column completion:
            push(KeyCode.TAB);
            // Hide any code completion (also: check it doesn't dismiss dialog)
            push(KeyCode.ESCAPE);
            push(KeyCode.ESCAPE);
            //TEMP:
            moveTo(".ok-button");
            TestUtil.sleep(2000);
            clickOn(".ok-button");
            // Now close dialog, and check for equality;
            View view = lookup(".view").query();
            if (view == null)
            {
                assertNotNull(view);
                return;
            }
            TestUtil.sleep(500);
            assertNull(lookup(".ok-button").query());
            Calculate calculate = (Calculate) view.getManager().getAllTables().stream().filter(t -> t instanceof Transformation).findFirst().orElseThrow(() -> new RuntimeException("No transformation found"));

            // Check expressions match:
            Expression expression = calculate.getCalculatedColumns().get(0).getSecond();
            assertEquals(expressionValue.expression, expression);
            // Just in case equals is wrong, check String comparison:
            assertEquals(expressionValue.expression.toString(), expression.toString());

            // Now check values match:
            // TODO make sure we pick the right table!
            clickOn(".table-display-table-title", MouseButton.SECONDARY)
                .clickOn(".id-tableDisplay-menu-copyValues");
            TestUtil.sleep(1000);
            Optional<List<Pair<ColumnId, List<@Value Object>>>> clip = TestUtil.<Optional<List<Pair<ColumnId, List<@Value Object>>>>>fx(() -> ClipboardUtils.loadValuesFromClipboard(expressionValue.typeManager));
            assertTrue(clip.isPresent());
            // Need to fish out first column from clip, then compare item:
            //TestUtil.checkType(expressionValue.type, clip.get().get(0));
            List<@Value Object> actual = clip.get().stream().filter((Pair<ColumnId, List<@Value Object>> p) -> p.getFirst().equals(new ColumnId("DestCol"))).findFirst().orElseThrow(RuntimeException::new).getSecond();
            TestUtil.assertValueListEqual("Transformed", expressionValue.value, actual);
        }
        finally
        {
            Stage s = windowToUse;
            Platform.runLater(() -> s.hide());
        }
    }

    // TODO test that nonsense is preserved after load (which will change it all to invalid) -> save -> load (which should load invalid version)
}
