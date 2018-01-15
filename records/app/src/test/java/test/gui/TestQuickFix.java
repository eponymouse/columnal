package test.gui;

import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;
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
import records.error.InternalException;
import records.error.UserException;
import records.transformations.Transform;
import records.transformations.TransformationInfo;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExFunction;
import utility.Utility;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;

import static org.junit.Assert.*;

@RunWith(JUnitQuickcheck.class)
@OnThread(Tag.Simulation)
public class TestQuickFix extends ApplicationTest implements EnterExpressionTrait, ScrollToTrait, ComboUtilTrait, ListUtilTrait
{
    @SuppressWarnings("nullness")
    private Stage windowToUse;

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void start(Stage stage) throws Exception
    {
        this.windowToUse = stage;
    }
    
    // Test that adding two strings suggests a quick fix to switch to string concatenation
    @Test
    @OnThread(Tag.Simulation)
    public void testStringAdditionFix1()
    {
        testFix("\"A\"+\"B\"", "A", "", "\"A\" ; \"B\"");
    }
    
    @Test
    @OnThread(Tag.Simulation)
    public void testStringAdditionFix2()
    {
        testFix("\"A\"+S1+\"C\"", "C", "", "\"A\" ; @column S1 ; \"C\"");
    }
    
    @Test
    public void testUnitLiteralFix1()
    {
        testFix("ACC1+6", "6", "", "ACC1 + 6{m/s^2}");
    }

    @Test
    public void testUnitLiteralFix2()
    {
        testFix("ACC1 > 6 > ACC3", "6", "", "ACC1 > 6{m/s^2} > ACC3");
    }

    /**
     * 
     * @param original Original expression
     * @param fixFieldContent Content of the field to focus on when looking for fix
     * @param fixId The CSS selector to use to look for the particular fix row
     * @param result The expected outcome expression after applying the fix
     */
    private void testFix(String original, String fixFieldContent, String fixId, String result)
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
            Node lhs = lookup(".entry-field").<Node>match((Predicate<Node>) (n -> TestUtil.fx(() -> ((TextField) n).getText().equals(fixFieldContent)))).<Node>query();
            assertNotNull(lhs);
            if (lhs == null) return;
            clickOn(lhs);
            assertEquals(1L, lookup(".expression-info-error").queryAll().stream().filter(Node::isVisible).count());
            assertTrue(!lookup(".quick-fix-row" + fixId).queryAll().isEmpty());
            clickOn(".quick-fix-row" + fixId);
            // Check that popup vanishes pretty much straight away:
            TestUtil.sleep(400);
            assertNull(lookup(".expression-info-popup").query());
            WaitForAsyncUtils.waitForFxEvents();
            //TODO really don't understand why I need to click OK twice:
            TestUtil.sleep(1000);
            clickOn(".ok-button");
            TestUtil.sleep(1000);
            WaitForAsyncUtils.waitForFxEvents();
            @Nullable Transform transform = Utility.filterClass(tableManager.getAllTables().stream(), Transform.class).findFirst().orElse(null);
            assertNotNull(transform);
            if (transform == null)
                return;
            assertEquals(1, transform.getCalculatedColumns().size());
            assertEquals(result, transform.getCalculatedColumns().get(0).getSecond().toString());
        }
        catch (Exception e)
        {
            // Test fails regardless, so no harm turning checked exception
            // into unchecked for simpler signatures:
            throw new RuntimeException(e);
        }
    }
}
