package test.gui;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.Window;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;
import records.data.CellPosition;
import records.data.ColumnId;
import records.data.EditableColumn;
import records.data.EditableRecordSet;
import records.data.MemoryNumericColumn;
import records.data.MemoryStringColumn;
import records.data.RecordSet;
import records.data.TableManager;
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
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExFunction;
import utility.Pair;
import utility.SimulationFunction;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@OnThread(Tag.Simulation)
public class TestExpressionEditorPosition extends FXApplicationTest implements ScrollToTrait, ListUtilTrait, ClickTableLocationTrait, FocusOwnerTrait
{    
    @Test
    public void testPosition1()
    {
        testCaretPositions("1");
    }

    @Test
    public void testPosition2()
    {
        testCaretPositions("1+2");
    }

    @Test
    public void testPosition3()
    {
        testCaretPositions("\"ab\";\"yz\"");
    }
    
    @Test
    public void testPosition4()
    {
        testCaretPositions("@iftrue@thensum(3+\"az\")@elsefalse");
    }


    // Tests that you can get back to the caret positions 
    // that were seen during insertion, by using left and
    // right cursor keys
    private void testCaretPositions(String content)
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
            CellPosition targetPos = new CellPosition(CellPosition.row(5), CellPosition.col(5));
            keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), targetPos);
            // Only need to click once as already selected by keyboard:
            for (int i = 0; i < 1; i++)
                clickOnItemInBounds(from(gridNode), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
            // Not sure why this doesn't work:
            //clickOnItemInBounds(lookup(".create-table-grid-button"), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
            correctTargetWindow().clickOn(".id-new-transform");
            correctTargetWindow().clickOn(".id-transform-calculate");
            correctTargetWindow().write("Table1");
            push(KeyCode.ENTER);
            TestUtil.sleep(200);
            write("DestCol");
            // Focus expression editor:
            push(KeyCode.TAB);
            
            write(content);
            
            TestUtil.fx_(() -> {
                Node n = getFocusOwner();
                if (n != null && n.getScene() != null)
                {
                    FXUtility.addChangeListenerPlatform(n.getScene().focusOwnerProperty(), o -> {
                        Log.logStackTrace("Focus owner now " + o);
                    });
                }
            });
            
            
            // Now we are at end, go backwards using left.  As we go,
            // reconstitute content in the textfields we visit
            // which should match the original text.
            Pair<TextField, Integer> curPosition = getPosition();
            Pair<TextField, Integer> oldPosition = null;
            int maxRemaining = 3 * content.length() + 5;
            while (!curPosition.equals(oldPosition) && --maxRemaining > 0)
            {                
                push(KeyCode.LEFT);
                oldPosition = curPosition;
                curPosition = getPosition();
            }
            assertNotEquals(0, maxRemaining);

            curPosition = getPosition();
            oldPosition = null;
            maxRemaining = 3 * content.length() + 5;
            while (!curPosition.equals(oldPosition) && --maxRemaining > 0)
            {
                push(KeyCode.RIGHT);
                oldPosition = curPosition;
                curPosition = getPosition();
            }
            assertNotEquals(0, maxRemaining);
            
            
            TopLevelEditorFlowPane editorPane = lookup(".expression-editor").<TopLevelEditorFlowPane>query();
            assertNotNull(editorPane);
            if (editorPane == null) return;
            TopLevelEditor<?, ?> expressionEditor = editorPane._test_getEditor();

            // Dismiss dialog:
            push(KeyCode.ESCAPE);
            push(KeyCode.ESCAPE);
            clickOn(".ok-button");
        }
        catch (Exception e)
        {
            // Test fails regardless, so no harm turning checked exception
            // into unchecked for simpler signatures:
            throw new RuntimeException(e);
        }
    }

    private Pair<TextField, Integer> getPosition()
    {
        Node focusOwner = getFocusOwner();
        if (!(focusOwner instanceof TextField))
            throw new RuntimeException("Focus owner is " + (focusOwner == null ? "null" : focusOwner.getClass().toString()));
        TextField textField = (TextField) focusOwner;
        return new Pair<>(textField, TestUtil.fx(() -> textField.getCaretPosition()));
    }
}
