package test.gui.expressionEditor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
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
import records.data.ImmediateDataSource;
import records.data.MemoryNumericColumn;
import records.data.MemoryStringColumn;
import records.data.RecordSet;
import records.data.Table.InitialLoadDetails;
import records.data.TableManager;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.data.unit.SingleUnit;
import records.data.unit.Unit;
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
import utility.Either;
import utility.SimulationFunction;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@RunWith(JUnitQuickcheck.class)
@OnThread(Tag.Simulation)
public class TestTypeQuickFix extends FXApplicationTest implements EnterExpressionTrait, ScrollToTrait, ComboUtilTrait, ListUtilTrait, ClickTableLocationTrait, PopupTrait
{
    @Test
    public void testTypo1()
    {
        testSimpleFix("Numbre", "Numbre", DataType.NUMBER);
    }

    @Test
    public void testTypo2()
    {
        testSimpleFix("DateYN", "DateYN", DataType.date(new DateTimeInfo(DateTimeType.YEARMONTH)));
    }

    @Test
    public void testTypo3()
    {
        testSimpleFix("Booleen", "Booleen", DataType.BOOLEAN);
    }

    @Test
    public void testTypo4() throws Exception
    {
        DummyManager dummyManager = TestUtil.managerWithTestTypes().getFirst();
        testSimpleFix("Optionl(Text)", "Optionl", dummyManager.getTypeManager().getMaybeType().instantiate(ImmutableList.of(Either.right(DataType.TEXT)), dummyManager.getTypeManager()));
    }

    @Test
    public void testTypeNameMixup() throws Exception
    {
        testSimpleFix("double", "double", DataType.NUMBER);
    }

    @Test
    public void testTypeNameMixup2() throws Exception
    {
        testSimpleFix("bool", "bool", DataType.BOOLEAN);
    }

    @Test
    public void testTypeNameMixup3() throws Exception
    {
        testSimpleFix("string", "string", DataType.TEXT);
    }

    @Test
    public void testTypeNameMixup4() throws Exception
    {
        testSimpleFix("int", "int", DataType.NUMBER);
    }
    
    @Test
    public void testUnitNameMixup() throws Exception
    {
        DummyManager dummyManager = TestUtil.managerWithTestTypes().getFirst();
        testFix("Number{second}", "second", dotCssClassFor("s"), DataType.number(new NumberInfo(dummyManager.getUnitManager().loadUse("s"))));
    }

    @Test
    public void testUnitNameMixup2() throws Exception
    {
        DummyManager dummyManager = TestUtil.managerWithTestTypes().getFirst();
        testFix("EitherNumUnit({second})({m})", "second", dotCssClassFor("s"), TestUtil.checkNonNull(dummyManager.getTypeManager().lookupType(new TypeId("EitherNumUnit"), ImmutableList.of(Either.left(dummyManager.getUnitManager().loadUse("s")), Either.left(dummyManager.getUnitManager().loadUse("m"))))));
    }

    @Test
    public void testUnitNameMixup3() throws Exception
    {
        DummyManager dummyManager = TestUtil.managerWithTestTypes().getFirst();
        UnitManager um = dummyManager.getUnitManager();
        testFix("Number{(s^6*meter)/kg}", "meter", dotCssClassFor("m"), DataType.number(new NumberInfo(um.loadUse("s").raisedTo(6).times(um.loadUse("m").divideBy(um.loadUse("kg"))))));
    }
    
    @Test
    public void testMissingTupleBrackets()
    {
        testSimpleFix("a:Number,b:Text", ",", DataType.record(ImmutableMap.of("a", DataType.NUMBER, "b",DataType.TEXT)));
    }
    
    private void testSimpleFix(String original, String fixFieldContent, DataType dataType)
    {
        try
        {
            testFix(original, fixFieldContent, dotCssClassFor(dataType.toDisplay(false)), dataType);
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    private String dotCssClassFor(String type) throws InternalException, UserException
    {
        return "." + ExpressionUtil.makeCssClass(type);
    }

    /**
     * 
     * @param original Original expression, as typed (NOT as parsed)
     * @param fixFieldContent Content of the field to focus on when looking for fix
     * @param fixId The CSS selector to use to look for the particular fix row
     * @param result The expected outcome expression after applying the fix
     */
    private void testFix(String original, String fixFieldContent, String fixId, DataType expectedResult)
    {
        try
        {
            MainWindowActions mainWindowActions = TestUtil.openDataAsTable(windowToUse, TestUtil.managerWithTestTypes().getFirst()).get();

            Region gridNode = TestUtil.fx(() -> mainWindowActions._test_getVirtualGrid().getNode());
            CellPosition targetPos = new CellPosition(CellPosition.row(1), CellPosition.col(1));
            for (int i = 0; i < 2; i++)
                clickOnItemInBounds(from(gridNode), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
            clickOn(".id-new-data");
            write("Table1");
            push(KeyCode.TAB);
            write("Column1");
            // Focus type editor:
            push(KeyCode.TAB);
            // Enter content:
            for (char c : original.toCharArray())
            {
                write(c);
                // Delete auto-matched brackets:
                if ("({[".contains("" + c))
                    push(KeyCode.DELETE);
            }
            // Click OK so that errors show up (cancel masking on new fields):
            moveAndDismissPopupsAtPos(point(".ok-button"));
            clickOn(".ok-button");
            
            EditorDisplay targetField = lookup(".editor-display").<EditorDisplay>tryQuery().orElse(null);
            assertNotNull("Editor Display", targetField);
            if (targetField == null) return;
            @NonNull Node targetFinal = targetField;
            if (!TestUtil.fx(() -> targetFinal.isFocused()))
            {
                //TestUtil.fx_(() -> dumpScreenshot());
                Log.debug("Focusing target field: " + targetFinal);
                // Get rid of any popups in the way:
                moveAndDismissPopupsAtPos(point(targetField));
                clickOn(targetField);
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
            assertEquals(0, TestUtil.fx(() -> targetField._test_getCaretMoveDistance(fixFieldContent)).intValue());
            
            TestUtil.delay(500);
            List<Window> windows = listWindows();
            @Nullable Window errorPopup = windows.stream().filter(w -> w instanceof PopOver).findFirst().orElse(null);
            assertNotNull(Utility.listToString(windows), errorPopup);
            assertEquals(lookup(".expression-info-error").queryAll().stream().map(n -> textFlowToString(n)).collect(Collectors.joining(" /// ")),
                1L, lookup(".expression-info-error").queryAll().stream().filter(Node::isVisible).count());
            assertEquals("Looking for row that matches " + fixId + ", among: " + lookup(".quick-fix-row").<Node>queryAll().stream().flatMap(n -> TestUtil.fx(() -> n.getStyleClass()).stream()).collect(Collectors.joining(", ")), 
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
            // Check that popup vanishes pretty much straight away:
            TestUtil.sleep(200);
            assertTrue("Popup still showing: "+ errorPopup, TestUtil.fx(() -> errorPopup != null && !errorPopup.isShowing()));
            WaitForAsyncUtils.waitForFxEvents();
            TestUtil.doubleOk(this);
            TestUtil.sleep(1000);
            WaitForAsyncUtils.waitForFxEvents();
            @Nullable ImmediateDataSource dataSource = Utility.filterClass(mainWindowActions._test_getTableManager().getAllTables().stream(), ImmediateDataSource.class).findFirst().orElse(null);
            assertNotNull(dataSource);
            if (dataSource == null)
                return;
            assertEquals(1, dataSource.getData().getColumns().size());
            DataType actual = dataSource.getData().getColumns().get(0).getType().getType();
            assertEquals(
                expectedResult,
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

    private String textFlowToString(Node n)
    {
        return TestUtil.fx(() -> n.toString() + " " + n.localToScreen(n.getBoundsInLocal().getMinX(), n.getBoundsInLocal().getMinY()) + ((TextFlow)n).getChildren().stream().map(c -> ((Text)c).getText()).collect(Collectors.joining(";")));
    }
}
