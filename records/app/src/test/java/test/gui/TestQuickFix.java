package test.gui;

import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.controlsfx.control.PopOver;
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
import java.util.stream.Collectors;

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
        testFix("ACC1+6", "6", "", "@column ACC1 + 6{m/s^2}");
    }

    @Test
    public void testUnitLiteralFix1B()
    {
        testFix("6-ACC1", "6", "", "6{m/s^2} - @column ACC1");
    }

    @Test
    public void testUnitLiteralFix2()
    {
        testFix("ACC1>6>ACC3", "6", "", "@column ACC1 > 6{m/s^2} > @column ACC3");
    }

    @Test
    public void testUnitLiteralFix3()
    {
        testFix("ACC1<>103", "103", "", "@column ACC1 <> 103{m/s^2}");
    }

    @Test
    public void testUnitLiteralFix4()
    {
        testFix("@ifACC1=ACC2=32@then2@else7+6", "32", "", "@if (@column ACC1 = @column ACC2 = 32{m/s^2}) @then 2 @else (7 + 6)");
    }

    @Test
    public void testUnitLiteralFix5()
    {
        testFix("@matchACC1@case3@then5", "3", "", "@match @column ACC1 @case 3{m/s^2} @then 5");
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
            TestUtil.sleep(2000);
            @Nullable Window errorPopup = listWindows().stream().filter(w -> w instanceof PopOver).findFirst().orElse(null);
            assertNotNull(errorPopup);
            assertEquals(lookup(".expression-info-error").queryAll().stream().map(n -> textFlowToString(n)).collect(Collectors.joining(" /// ")),
                1L, lookup(".expression-info-error").queryAll().stream().filter(Node::isVisible).count());
            assertEquals(1, lookup(".quick-fix-row" + fixId).queryAll().size());
            // Get around issue with not being able to get the position of
            // items in the fix popup correctly, by using keyboard:
            //moveTo(".quick-fix-row" + fixId);
            //clickOn(".quick-fix-row" + fixId);
            push(KeyCode.SHIFT, KeyCode.F1);
            // Check that popup vanishes pretty much straight away:
            TestUtil.sleep(200);
            assertTrue(TestUtil.fx(() -> errorPopup != null && !errorPopup.isShowing()));
            WaitForAsyncUtils.waitForFxEvents();
            moveTo(".ok-button");
            TestUtil.sleep(3000);
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

    private String textFlowToString(Node n)
    {
        return TestUtil.fx(() -> n.toString() + " " + n.localToScreen(n.getBoundsInLocal().getMinX(), n.getBoundsInLocal().getMinY()) + ((TextFlow)n).getChildren().stream().map(c -> ((Text)c).getText()).collect(Collectors.joining(";")));
    }
}
