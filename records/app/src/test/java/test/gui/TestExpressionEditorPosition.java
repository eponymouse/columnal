package test.gui;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;
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
import records.gui.expressioneditor.ExpressionEditor;
import records.gui.expressioneditor.TopLevelEditor;
import records.gui.expressioneditor.TopLevelEditor.TopLevelEditorFlowPane;
import records.transformations.TransformationInfo;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExFunction;
import utility.Pair;
import utility.SimulationFunction;
import utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@OnThread(Tag.Simulation)
public class TestExpressionEditorPosition extends ApplicationTest implements ScrollToTrait, ListUtilTrait
{
    @SuppressWarnings("nullness")
    private Stage windowToUse;

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void start(Stage stage) throws Exception
    {
        this.windowToUse = stage;
    }
    
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
            TableManager tableManager = TestUtil.openDataAsTable(windowToUse, typeManager, new EditableRecordSet(columns, () -> 0))._test_getTableManager();

            scrollTo(".id-tableDisplay-menu-button");
            clickOn(".id-tableDisplay-menu-button").clickOn(".id-tableDisplay-menu-addTransformation");
            //TODO fix this
            //selectGivenListViewItem(lookup(".transformation-list").query(), (TransformationInfo ti) -> ti.getDisplayName().toLowerCase().startsWith("calculate"));
            push(KeyCode.TAB);
            write("DestCol");
            // Focus expression editor:
            push(KeyCode.TAB);
            push(KeyCode.TAB);
            
            write(content);
            
            // Now we are at end, go backwards using left.  As we go,
            // reconstitute content in the textfields we visit
            // which should match the original text.
            Pair<TextField, Integer> curPosition = getPosition();
            Pair<TextField, Integer> oldPosition = null;
            int maxRemaining = 3 * content.length() + 5;
            ArrayList<String> accum = new ArrayList<>();
            while (!curPosition.equals(oldPosition) && --maxRemaining > 0)
            {
                Pair<TextField, Integer> curFinal = curPosition;
                // Add the character to the right of the caret:
                String toRight = TestUtil.fx(() -> curFinal.getFirst().getText(curFinal.getSecond(), Math.min(curFinal.getSecond() + 1, curFinal.getFirst().getText().length())));
                accum.add(toRight);
                
                push(KeyCode.LEFT);
                oldPosition = curPosition;
                curPosition = getPosition();
            }
            assertNotEquals(0, maxRemaining);
            Collections.reverse(accum);
            assertEquals(Utility.listToString(accum), content, accum.stream().collect(Collectors.joining()));

            curPosition = getPosition();
            oldPosition = null;
            maxRemaining = 3 * content.length() + 5;
            accum.clear();
            while (!curPosition.equals(oldPosition) && --maxRemaining > 0)
            {
                Pair<TextField, Integer> curFinal = curPosition;
                // Add the character to the right of the caret:
                String toRight = TestUtil.fx(() -> curFinal.getFirst().getText(Math.max(0, curFinal.getSecond() - 1), curFinal.getSecond()));
                accum.add(toRight);

                push(KeyCode.RIGHT);
                oldPosition = curPosition;
                curPosition = getPosition();
            }
            assertNotEquals(0, maxRemaining);
            assertEquals(Utility.listToString(accum), content, accum.stream().collect(Collectors.joining()));
            
            
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
        Window window = targetWindow();
        Node focusOwner = TestUtil.fx(() -> window.getScene().getFocusOwner());
        assertNotNull(focusOwner);
        assertTrue(focusOwner instanceof TextField);
        TextField textField = (TextField) focusOwner;
        return new Pair<>(textField, TestUtil.fx(() -> textField.getCaretPosition()));
    }
}
