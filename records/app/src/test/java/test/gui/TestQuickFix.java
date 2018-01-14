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
import records.data.EditableRecordSet;
import records.data.TableManager;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.Transform;
import records.transformations.TransformationInfo;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

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
    public void testStringAdditionFix() throws UserException, InternalException, InterruptedException, ExecutionException, InvocationTargetException, IOException
    {
        TypeManager typeManager = new TypeManager(new UnitManager());
        TableManager tableManager = TestUtil.openDataAsTable(windowToUse, typeManager, new EditableRecordSet(Collections.emptyList(), () -> 0));

        scrollTo(".id-tableDisplay-menu-button");
        clickOn(".id-tableDisplay-menu-button").clickOn(".id-tableDisplay-menu-addTransformation");
        selectGivenListViewItem(lookup(".transformation-list").query(), (TransformationInfo ti) -> ti.getDisplayName().toLowerCase().startsWith("calculate"));
        push(KeyCode.TAB);
        write("DestCol");
        // Focus expression editor:
        push(KeyCode.TAB);
        push(KeyCode.TAB);
        write("\"A\"+\"B\"");
        // TODO copy whole expression and check it matches
        @Nullable Node operator = lookup(".entry-field").<Node>match((Predicate<Node>) (n -> TestUtil.fx(() -> ((TextField) n).getText().equals("+")))).<Node>query();
        assertNotNull(operator);
        if (operator == null)
            return;
        moveTo(operator);
        TestUtil.sleep(2500);
        // Should be no quick fixes on the operator:
        assertEquals(0L, lookup(".expression-info-error").queryAll().stream().filter(Node::isVisible).count());
        assertEquals(0, lookup(".quick-fix-row").queryAll().size());
        Node lhs = lookup(".entry-field").<Node>match((Predicate<Node>) (n -> TestUtil.fx(() -> ((TextField) n).getText().equals("A")))).<Node>query();
        assertNotNull(lhs);
        if (lhs == null) return;
        clickOn(lhs);
        assertEquals(1L, lookup(".expression-info-error").queryAll().stream().filter(Node::isVisible).count());
        assertEquals(1, lookup(".quick-fix-row").queryAll().size());
        clickOn(".quick-fix-row");
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
        assertEquals("\"A\" ; \"B\"", transform.getCalculatedColumns().get(0).getSecond().toString());
    }
}
