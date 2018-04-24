package test.gui;

import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import log.Log;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;
import records.data.CellPosition;
import records.data.ColumnId;
import records.data.EditableColumn;
import records.data.EditableRecordSet;
import records.data.MemoryNumericColumn;
import records.data.MemoryStringColumn;
import records.data.RecordSet;
import records.data.TableManager;
import records.data.datatype.NumberDisplayInfo;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.gui.MainWindow.MainWindowActions;
import records.gui.expressioneditor.ExpressionEditor;
import records.gui.expressioneditor.TopLevelEditor;
import records.gui.expressioneditor.TopLevelEditor.TopLevelEditorFlowPane;
import records.gui.grid.RectangleBounds;
import records.transformations.TransformationInfo;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExFunction;
import utility.Pair;
import utility.SimulationFunction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

@RunWith(JUnitQuickcheck.class)
@OnThread(Tag.Simulation)
public class TestExpressionEditorError extends ApplicationTest implements ScrollToTrait, ListUtilTrait, ClickTableLocationTrait
{
    @SuppressWarnings("nullness")
    private Stage windowToUse;

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void start(Stage stage) throws Exception
    {
        this.windowToUse = stage;
    }
    
    @OnThread(Tag.Any)
    private static class State
    {
        public final String headerText;
        public final boolean errorColourHeader;

        private State(String headerText, boolean errorColourHeader)
        {
            this.headerText = headerText;
            this.errorColourHeader = errorColourHeader;
        }
        
        public Pair<String, Boolean> toPair()
        {
            return new Pair<>(headerText, errorColourHeader);
        }
    }
    
    @Test
    public void test1()
    {
        // Check basic:
        testError("1", false, h());
    }

    @Test
    public void test2()
    {
        // Don't want an error if we're still in the slot::
        testError("1#", false, h());
    }

    @Test
    public void test2B()
    {
        // Error once we leave the slot:
        // (but no error in the blank operand added at the end)
        testError("1#+", false, eRed(), h(), h());
    }

    @Test
    public void test2C()
    {
        // Error once we leave the slot:
        // (and error in the blank operand skipped)
        testError("1#+/", false, eRed(), h(), eRed(), h(), h());
    }
    
    @Test
    public void test3()
    {
        testError("@if # @then #", false,
            // if, condition
            h(), eRed(),
            // then, # (but focused)
            h(), h(),
            // else, blank (but unvisited)
            h(), e());
    }

    @Test
    public void test3B()
    {
        testError("@if 3 @then 4 @else 5", false,
                // if, condition (should be boolean)
                h(), red(""),
                // then, 4
                h(), h(),
                // else, 5
                h(), h());
    }

    @Test
    public void test3C()
    {
        testError("@if 3 @then #", false,
                // if, condition (type error)
                h(), red(""),
                // then, # (but focused)
                h(), h(),
                // else, blank (but unvisited)
                h(), e());
    }

    private static State h()
    {
        return new State("", false);
    }


    private static State h(String s)
    {
        return new State(s, false);
    }

    private static State red(String header)
    {
        return new State(header, true);
    }


    private static State eRed()
    {
        return new State("error", true);
    }

    private static State e()
    {
        return new State("error", false);
    }
    
    private void testError(String original, boolean errorPopupShowing, State... states)
    {
        try
        {
            UnitManager u = new UnitManager();
            TypeManager typeManager = new TypeManager(u);
            List<SimulationFunction<RecordSet, EditableColumn>> columns = new ArrayList<>();
            for (int i = 1; i <= 3; i++)
            {
                int iFinal = i;
                columns.add(rs -> new MemoryStringColumn(rs, new ColumnId("S" + iFinal), Collections.emptyList(), ""));
                columns.add(rs -> new MemoryNumericColumn(rs, new ColumnId("ACC" + iFinal), new NumberInfo(u.loadUse("m/s^2")), Collections.emptyList(), 0));
            }
            MainWindowActions mainWindowActions = TestUtil.openDataAsTable(windowToUse, typeManager, new EditableRecordSet(columns, () -> 0));

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
            write(original);
            TopLevelEditorFlowPane editorPane = lookup(".expression-editor").<TopLevelEditorFlowPane>query();
            assertNotNull(editorPane);
            if (editorPane == null) return;
            TopLevelEditor<?, ?> expressionEditor = editorPane._test_getEditor();
            List<Pair<String, Boolean>> actualHeaders = TestUtil.fx(() -> expressionEditor._test_getHeaders()).collect(Collectors.toList());

            // Dismiss before comparison to avoid issues with exception:
            push(KeyCode.ESCAPE);
            push(KeyCode.ESCAPE);
            //TestUtil.sleep(2000);
            moveTo(".cancel-button");
            // Shouldn't really need this delay but test is flaky without it due to some JavaFX animation-related exceptions:
            TestUtil.sleep(2000);
            clickOn(".cancel-button");
            
            
            Log.debug("Checking states");
            assertEquals(Arrays.stream(states).map(State::toPair).collect(Collectors.toList()), actualHeaders);
            Log.debug("Checked states");
            // TODO check error popup
            
            // If test is success, ignore exceptions (which seem to occur due to hiding error display popup):
            // Shouldn't really need this code but test is flaky without it due to some JavaFX animation-related exceptions:
            TestUtil.sleep(2000);
            WaitForAsyncUtils.clearExceptions();
        }
        catch (Exception e)
        {
            // Test fails regardless, so no harm turning checked exception
            // into unchecked for simpler signatures:
            throw new RuntimeException(e);
        }
    }
}
