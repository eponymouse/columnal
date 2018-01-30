package test.gui;

import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testfx.framework.junit.ApplicationTest;
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
import records.gui.expressioneditor.ExpressionEditor;
import records.gui.expressioneditor.ExpressionEditor.ExpressionEditorFlowPane;
import records.transformations.TransformationInfo;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExFunction;
import utility.Pair;

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
public class TestExpressionEditorError extends ApplicationTest implements ScrollToTrait, ListUtilTrait
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
        testError("1", false, h(""));
    }

    @Test
    public void test2()
    {
        // Don't want an error if we're still in the slot::
        testError("1#", false, h(""));
    }

    @Test
    public void test2B()
    {
        // Error once we leave the slot:
        testError("1#+", false, e(), h(""), h(""));
    }

    private static State h(String s)
    {
        return new State(s, false);
    }
    
    private static State e()
    {
        return new State("error", true);
    }

    private void testError(String original, boolean errorPopupShowing, State... states)
    {
        try
        {
            UnitManager u = new UnitManager();
            TypeManager typeManager = new TypeManager(u);
            List<ExFunction<RecordSet, ? extends EditableColumn>> columns = new ArrayList<>();
            for (int i = 1; i <= 3; i++)
            {
                int iFinal = i;
                columns.add(rs -> new MemoryStringColumn(rs, new ColumnId("S" + iFinal), Collections.emptyList(), ""));
                columns.add(rs -> new MemoryNumericColumn(rs, new ColumnId("ACC" + iFinal), new NumberInfo(u.loadUse("m/s^2"), NumberDisplayInfo.SYSTEMWIDE_DEFAULT), Collections.emptyList(), 0));
            }
            TableManager tableManager = TestUtil.openDataAsTable(windowToUse, typeManager, new EditableRecordSet(columns, () -> 0));

            scrollTo(".id-tableDisplay-menu-button");
            clickOn(".id-tableDisplay-menu-button").clickOn(".id-tableDisplay-menu-addTransformation");
            selectGivenListViewItem(lookup(".transformation-list").query(), (TransformationInfo ti) -> ti.getDisplayName().toLowerCase().startsWith("calculate"));
            push(KeyCode.TAB);
            write("DestCol");
            // Focus expression editor:
            push(KeyCode.TAB);
            push(KeyCode.TAB);
            write(original);
            ExpressionEditorFlowPane editorPane = lookup(".expression-editor").<ExpressionEditorFlowPane>query();
            assertNotNull(editorPane);
            if (editorPane == null) return;
            ExpressionEditor expressionEditor = editorPane._test_getEditor();
            List<Pair<String, Boolean>> actualHeaders = TestUtil.fx(() -> expressionEditor._test_getHeaders()).collect(Collectors.toList());
            
            assertEquals(Arrays.stream(states).map(State::toPair).collect(Collectors.toList()), actualHeaders);
            // TODO check error popup
        }
        catch (Exception e)
        {
            // Test fails regardless, so no harm turning checked exception
            // into unchecked for simpler signatures:
            throw new RuntimeException(e);
        }
    }
}
