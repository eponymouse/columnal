package test.gui.expressionEditor;

import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import log.Log;
import org.junit.Test;
import records.data.CellPosition;
import records.data.ColumnId;
import records.data.EditableColumn;
import records.data.EditableRecordSet;
import records.data.MemoryNumericColumn;
import records.data.MemoryStringColumn;
import records.data.RecordSet;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.gui.MainWindow.MainWindowActions;
import records.gui.expressioneditor.TopLevelEditor;
import records.gui.expressioneditor.TopLevelEditor.TopLevelEditorFlowPane;
import records.gui.grid.RectangleBounds;
import records.gui.lexeditor.EditorDisplay;
import test.TestUtil;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.FocusOwnerTrait;
import test.gui.trait.ListUtilTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.SimulationFunction;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.ArrayList;
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
        testCaretPositions("@iftrue@thensum(3+\"az\")@elsefalse@endif");
    }

    @Test
    public void testPos1()
    {
        testCaretPositionsAndDisplay("1", "1", 0, 1);
    }

    @Test
    public void testPos2()
    {
        testCaretPositionsAndDisplay("1+2", "1 + 2", 0, 1, 2, 3);
    }

    @Test
    public void testPos3()
    {
        testCaretPositionsAndDisplay("@iftrue@thensum(3+\"az\")@elsefalse@endif", "@if true @then sum(3 + \"az\") @else false @endif ", 0, 3,4,5,6,7, 12,13,14,15,16,17,18,19,20,21,22,23,  28,29,30,31,32,33, 39);
    }

    @Test
    public void testPosIncomplete1()
    {
        testCaretPositionsAndDisplay("1+", "1 + ", 0, 1, 2);
    }
    
    @Test
    public void testPosIncomplete2()
    {
        testCaretPositionsAndDisplay("1<", "1 < ", 0, 1, 2);
    }
    
    @Test
    public void testPosIncomplete2b()
    {
        testCaretPositionsAndDisplay("1<=", "1 <= ", 0, 1, 3);
    }
    
    @Test
    public void testPosIncomplete3()
    {
        testCaretPositionsAndDisplay("@i", "@i", 0, 1, 2);
    }

    @Test
    public void testPosIncomplete4()
    {
        testCaretPositionsAndDisplay("@if", "@if ", 0, 3);
    }


    private void testCaretPositionsAndDisplay(String internalContent, String display, int... internalCaretPos)
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

            write(internalContent);

            TestUtil.fx_(() -> {
                Node n = getFocusOwner();
                if (n != null && n.getScene() != null)
                {
                    FXUtility.addChangeListenerPlatform(n.getScene().focusOwnerProperty(), o -> {
                        Log.logStackTrace("Focus owner now " + o);
                    });
                }
            });
            
            assertEquals(display, getDisplayText());
            
            push(KeyCode.HOME);
            int curIndex = 0;
            while (curIndex < internalCaretPos.length)
            {
                assertEquals("Index " + curIndex, internalCaretPos[curIndex], getPosition().getSecond().intValue());
                curIndex += 1;
                push(KeyCode.RIGHT);
            }
            push(KeyCode.END);
            curIndex = internalCaretPos.length - 1;
            while (curIndex >= 0)
            {
                assertEquals(internalCaretPos[curIndex], getPosition().getSecond().intValue());
                curIndex -= 1;
                push(KeyCode.LEFT);
            }

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
            
            
            // We check that if we go all-left then all-right, we reach a termination point
            // in each case (as opposed to looping forever somewhere in the middle)
            
            Pair<EditorDisplay, Integer> curPosition = getPosition();
            Pair<EditorDisplay, Integer> oldPosition = null;
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

    private Pair<EditorDisplay, Integer> getPosition()
    {
        Node focusOwner = getFocusOwner();
        if (!(focusOwner instanceof EditorDisplay))
            throw new RuntimeException("Focus owner is " + (focusOwner == null ? "null" : focusOwner.getClass().toString()));
        EditorDisplay textField = (EditorDisplay) focusOwner;
        return new Pair<>(textField, TestUtil.fx(() -> textField.getCaretPosition()));
    }
    
    private String getDisplayText()
    {
        Pair<EditorDisplay, Integer> pos = getPosition();
        return TestUtil.fx(() -> Utility.filterClass(((TextFlow) pos.getFirst().lookup(".document-text-flow")).getChildren().stream(), Text.class).map(t -> t.getText()).collect(Collectors.joining()));
    }
}
