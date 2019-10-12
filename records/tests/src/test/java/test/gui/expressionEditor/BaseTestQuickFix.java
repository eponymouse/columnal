package test.gui.expressionEditor;

import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Window;
import log.Log;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.controlsfx.control.PopOver;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testfx.service.query.NodeQuery;
import org.testfx.util.WaitForAsyncUtils;
import records.data.CellPosition;
import records.data.ColumnId;
import records.data.EditableColumn;
import records.data.EditableRecordSet;
import records.data.MemoryNumericColumn;
import records.data.MemoryStringColumn;
import records.data.RecordSet;
import records.data.Table.InitialLoadDetails;
import records.data.TableManager;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.MainWindow.MainWindowActions;
import records.gui.grid.RectangleBounds;
import records.gui.lexeditor.EditorDisplay;
import records.transformations.Calculate;
import records.transformations.expression.Expression;
import records.transformations.expression.ExpressionUtil;
import records.transformations.function.FunctionList;
import test.DummyManager;
import test.TestUtil;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.ComboUtilTrait;
import test.gui.trait.EnterExpressionTrait;
import test.gui.trait.ListUtilTrait;
import test.gui.trait.PopupTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformRunnable;
import utility.SimulationFunction;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@RunWith(JUnitQuickcheck.class)
@OnThread(Tag.Simulation)
public class BaseTestQuickFix extends FXApplicationTest implements EnterExpressionTrait, ScrollToTrait, ComboUtilTrait, ListUtilTrait, ClickTableLocationTrait, PopupTrait
{
    void testSimpleFix(String original, String fixFieldContent, String fixed)
    {
        try
        {
            testFix(original, fixFieldContent, dotCssClassFor(fixed), fixed);
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    String dotCssClassFor(String expression) throws InternalException, UserException
    {
        TypeManager typeManager = DummyManager.make().getTypeManager();
        return "." + ExpressionUtil.makeCssClass(TestUtil.parseExpression(expression, typeManager, FunctionList.getFunctionLookup(typeManager.getUnitManager())));
    }

    /**
     * 
     * @param original Original expression, as typed (NOT as parsed)
     * @param fixFieldContent Content of the field to focus on when looking for fix
     * @param fixId The CSS selector to use to look for the particular fix row
     * @param result The expected outcome expression after applying the fix
     */
    void testFix(String original, String fixFieldContent, String fixId, String result)
    {
        testFix(original, fixFieldContent, fixId, result, () -> {});
    }
    
    @SuppressWarnings("identifier")
    void testFix(String original, String fixFieldContent, String fixId, String result, Runnable afterClick)
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
            TableManager tableManager = mainWindowActions._test_getTableManager();

            CellPosition position = new CellPosition(CellPosition.row(7), CellPosition.col(1));
            tableManager.edit(null, () -> new Calculate(tableManager, new InitialLoadDetails(null, null, position, null), tableManager.getAllTables().get(0).getId(), ImmutableMap.of()), null);

            TestUtil.sleep(3000);
            NodeQuery arrowQuery = lookup(".expand-arrow").match(n -> TestUtil.fx(() -> FXUtility.hasPseudoclass(n, "expand-right")));
            clickOnItemInBounds(arrowQuery, mainWindowActions._test_getVirtualGrid(), new RectangleBounds(
                new CellPosition(CellPosition.row(8), CellPosition.col(1)),
                new CellPosition(CellPosition.row(8), CellPosition.col(20))
            ));
            TestUtil.sleep(1000);
            write("DestCol");
            // Focus expression editor:
            push(KeyCode.TAB);
            // Enter content:
            enterAndDeleteSmartBrackets(original);
            // Click OK so that errors show up (cancel masking on new fields):
            moveAndDismissPopupsAtPos(point(".ok-button"));
            clickOn(".ok-button");
            
            EditorDisplay targetField = lookup(".editor-display").<EditorDisplay>tryQuery().orElse(null);
            assertNotNull("Editor Display", targetField);
            if (targetField == null) return;
            @NonNull Node targetFinal = targetField;
            if (!TestUtil.fx(() -> targetFinal.isFocused()))
            {
                Log.debug("Focusing target field: " + targetFinal);
                // Get rid of any popups in the way:
                moveAndDismissPopupsAtPos(point(targetField));
                clickOn(point(targetField));
                assertTrue("Clicked " + point(targetField).query().toString() + " focus is: " + TestUtil.<@Nullable Node>fx(() -> getFocusOwner()), TestUtil.fx(() -> targetFinal.isFocused()));
            }
            // Now need to move to right position:
            int moveDist = TestUtil.fx(() -> targetField._test_getCaretMoveDistance(fixFieldContent));
            while (moveDist > 0)
            {
                push(KeyCode.RIGHT);
                moveDist -=1;
            }
            while (moveDist < 0)
            {
                push(KeyCode.LEFT);
                moveDist +=1;
            }
            // Check we're actually now in bounds:
            assertTrue(TestUtil.fx(() -> targetFinal.isFocused()));
            assertEquals(0, TestUtil.fx(() -> targetField._test_getCaretMoveDistance(fixFieldContent)).intValue());
            
            TestUtil.delay(500);
            List<Window> windows = listWindows();
            @Nullable Window errorPopup = windows.stream().filter(w -> w instanceof PopOver).findFirst().orElse(null);
            assertNotNull(Utility.listToString(windows), errorPopup);
            assertEquals(lookup(".expression-info-error").queryAll().stream().map(n -> textFlowToString(n)).collect(Collectors.joining(" /// ")),
                1L, lookup(".expression-info-error").queryAll().stream().filter(Node::isVisible).count());
            assertEquals("Looking for row that matches" + showId(fixId) + ", among: " + lookup(".quick-fix-row").<Node>queryAll().stream().map(n -> "{" + TestUtil.fx(() -> n.getStyleClass()).stream().map(s -> showId(s)).collect(Collectors.joining(", ")) + "}").collect(Collectors.joining(" and ")), 
                1, lookup(".quick-fix-row" + fixId).queryAll().size());
            // Get around issue with not being able to get the position of
            // items in the fix popup correctly, by using keyboard:
            //moveTo(".quick-fix-row" + fixId);
            //clickOn(".quick-fix-row" + fixId);
            Node fixRow = lookup(".quick-fix-row" + fixId).queryAll().iterator().next();
            List<String> fixStyles = TestUtil.fx(() -> fixRow.getStyleClass());
            String key = fixStyles.stream().filter(c -> c.startsWith("key-")).map(c -> c.substring("key-".length())).findFirst().orElse("");
            assertNotEquals(Utility.listToString(fixStyles), "", key);
            Log.debug("Pressing: SHIFT-" + key);
            push(KeyCode.SHIFT, KeyCode.valueOf(key));
            WaitForAsyncUtils.waitForFxEvents();
            afterClick.run();
            // Check that popup vanishes pretty much straight away:
            TestUtil.sleep(200);
            assertFalse("Popup still showing: " + errorPopup, isShowingErrorPopup());
            TestUtil.doubleOk(this);
            TestUtil.sleep(1000);
            WaitForAsyncUtils.waitForFxEvents();
            @Nullable Calculate calculate = Utility.filterClass(tableManager.getAllTables().stream(), Calculate.class).findFirst().orElse(null);
            assertNotNull(calculate);
            if (calculate == null)
                return;
            assertEquals(1, calculate.getCalculatedColumns().size());
            Expression actual = calculate.getCalculatedColumns().values().iterator().next();
            assertEquals(
                TestUtil.parseExpression(result, typeManager, FunctionList.getFunctionLookup(typeManager.getUnitManager())),
                actual);
        }
        catch (Exception e)
        {
            // Test fails regardless, so no harm turning checked exception
            // into unchecked for simpler signatures:
            throw new RuntimeException(e);
        }
        catch (Throwable t)
        {
            throw t;
        }
    }

    private String showId(String fixId)
    {
        int munge = fixId.indexOf("munged-");
        if (munge != -1)
        {
            return fixId + "[" + Arrays.stream(fixId.substring(munge + "munged-".length()).split("-")).map(n -> "" + (char)Integer.parseInt(n)).collect(Collectors.joining()) + "]";
        }
        return fixId;
    }

    private boolean isShowingErrorPopup()
    {
        // Important to check the .error part too, as it may be showing information or a prompt and that's fine:
        return lookup(".expression-info-popup.error").tryQuery().isPresent();
    }

    private String textFlowToString(Node n)
    {
        return TestUtil.fx(() -> n.toString() + " " + n.localToScreen(n.getBoundsInLocal().getMinX(), n.getBoundsInLocal().getMinY()) + ((TextFlow)n).getChildren().stream().map(c -> ((Text)c).getText()).collect(Collectors.joining(";")));
    }
}
