package test.gui;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import log.Log;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;
import records.data.CellPosition;
import records.data.ColumnId;
import records.data.KnownLengthRecordSet;
import records.data.Transformation;
import records.data.datatype.DataType;
import records.gui.MainWindow.MainWindowActions;
import records.gui.View;
import records.gui.expressioneditor.TopLevelEditor;
import records.gui.expressioneditor.TopLevelEditor.TopLevelEditorFlowPane;
import records.gui.grid.RectangleBounds;
import records.importers.ClipboardUtils;
import records.transformations.Calculate;
import records.transformations.expression.Expression;
import test.DummyManager;
import test.TestUtil;
import test.gen.ExpressionValue;
import test.gen.GenExpressionValueBackwards;
import test.gen.GenExpressionValueForwards;
import test.gen.GenRandom;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.Assert.*;

@RunWith(JUnitQuickcheck.class)
@OnThread(Tag.Simulation)
public class TestExpressionEditorDragPaste extends ApplicationTest implements ListUtilTrait, ScrollToTrait, EnterExpressionTrait, ClickTableLocationTrait
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
    private static enum MoveMethod { DRAG, CUT_PASTE }
    
    private void testMove(Expression expression,
                          int startNodeIndexIncl, int endNodeIndexIncl,
                          int destBeforeNodexIndexIncl,
                          Expression after,
                          MoveMethod moveMethod,
                          Random r) throws Exception
    {
        DummyManager typeManager = new DummyManager();
        MainWindowActions mainWindowActions = TestUtil.openDataAsTable(windowToUse, typeManager.getTypeManager(), new KnownLengthRecordSet(ImmutableList.of(), 0));
        try
        {
            View view = lookup(".view").query();
            if (view == null)
            {
                assertNotNull(view);
                return;
            }
            
            Region gridNode = TestUtil.fx(() -> mainWindowActions._test_getVirtualGrid().getNode());
            CellPosition targetPos = new CellPosition(CellPosition.row(1), CellPosition.col(1));
            keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), targetPos);
            // Only need to click once as already selected by keyboard:
            for (int i = 0; i < 1; i++)
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
            Log.normal("Entering expression:\n" + expression.toString() + "\n");
            enterExpression(expression, false, r);

            clickOk();
            Expression entered = getExpressionFromCalculate(view);
            // Check expressions match:
            assertEquals(expression, entered);
            // Just in case equals is wrong, check String comparison:
            assertEquals(expression.toString(), entered.toString());
            
            clickOn(".edit-calculate-column");
            TopLevelEditorFlowPane editorPane = lookup(".expression-editor").<TopLevelEditorFlowPane>query();
            assertNotNull(editorPane);
            if (editorPane == null) return;
            TopLevelEditor<?, ?> expressionEditor = editorPane._test_getEditor();
            ImmutableList<Label> headers = TestUtil.fx(() -> expressionEditor._test_getHeaders().map(Pair::getFirst).collect(ImmutableList.toImmutableList()));
            clickOn(headers.get(startNodeIndexIncl));
            press(KeyCode.SHIFT);
            clickOn(headers.get(endNodeIndexIncl));
            release(KeyCode.SHIFT);
            if (moveMethod == MoveMethod.CUT_PASTE)
            {
                push(KeyCode.SHORTCUT, KeyCode.X);
                headers = TestUtil.fx(() -> expressionEditor._test_getHeaders().map(Pair::getFirst).collect(ImmutableList.toImmutableList()));
                clickOn(headers.get(destBeforeNodexIndexIncl - (endNodeIndexIncl - startNodeIndexIncl + 1)));
                push(KeyCode.DOWN);
                push(KeyCode.LEFT);
                push(KeyCode.SHORTCUT, KeyCode.V);
            }
            else if (moveMethod == MoveMethod.DRAG)
            {
                drag(headers.get(startNodeIndexIncl), MouseButton.PRIMARY);
                Node target = headers.get(destBeforeNodexIndexIncl);
                dropTo(TestUtil.fx(() -> target.localToScreen(target.getBoundsInLocal()).getMinX() + 5), TestUtil.fx(() -> target.localToScreen(target.getBoundsInLocal()).getMinY() + 5));
            }
            clickOk();
            entered = getExpressionFromCalculate(view);
            // Check expressions match:
            assertEquals(after, entered);
            // Just in case equals is wrong, check String comparison:
            assertEquals(after.toString(), entered.toString());
            
            
            // If test is success, ignore exceptions (which seem to occur due to hiding error display popup):
            // Shouldn't really need this code but test is flaky without it due to some JavaFX animation-related exceptions:
            TestUtil.sleep(2000);
            WaitForAsyncUtils.clearExceptions();
        }
        finally
        {
            Stage s = windowToUse;
            Platform.runLater(() -> s.hide());
        }
    }

    private void clickOk()
    {
        moveTo(".ok-button");
        // Get rid of popups:
        clickOn(MouseButton.MIDDLE);
        clickOn(MouseButton.PRIMARY);
        // Now close dialog, and check for equality;

        TestUtil.sleep(500);
        assertNull(lookup(".ok-button").query());
    }

    private Expression getExpressionFromCalculate(View view)
    {
        Calculate calculate = (Calculate) view.getManager().getAllTables().stream().filter(t -> t instanceof Transformation).findFirst().orElseThrow(() -> new RuntimeException("No transformation found"));


        return calculate.getCalculatedColumns().values().iterator().next();
    }

    private void testSimple(String expressionSrc, 
                            int startNodeIndexIncl, int endNodeIndexIncl,
                            int destBeforeNodexIndexIncl,
                            String afterSrc) throws Exception
    {
        Expression expression = Expression.parse(null, expressionSrc, DummyManager.INSTANCE.getTypeManager());
        Expression after = Expression.parse(null, afterSrc, DummyManager.INSTANCE.getTypeManager());

        for (MoveMethod moveMethod : MoveMethod.values())
        {
            testMove(expression, startNodeIndexIncl, endNodeIndexIncl, destBeforeNodexIndexIncl, after, moveMethod, new Random(1L));
        }
    }

    @Test
    public void testTuple() throws Exception
    {
        testSimple("(1, 2, 3)", 3, 4, 1, "(2, 1, 3)");
    }

    /*
    @Test
    public void testCall() throws Exception
    {
        testSimple("@call @function first(1, 2)");
    }

    @Test
    public void testCall2() throws Exception
    {
        testSimple("@call @function first(1,(2+3))");
    }

    @Test
    public void testCall3() throws Exception
    {
        testSimple("@call @function first(1,(2/3))");
    }

    @Test
    public void testCall4() throws Exception
    {
        testSimple("@call @function abs(2/3)");
    }
    
    @Test
    public void testUnit() throws Exception
    {
        testSimple("35{m/s}");
    }
    
    @Test
    public void testUnit2() throws Exception
    {
        testSimple("35{m/(s*s)}");
    }
    
    @Test
    public void testMinus() throws Exception
    {
        testSimple("-2");
    }

    @Test
    public void testMinus2() throws Exception
    {
        testSimple("1-2");
    }
    @Test
    public void testMinus2b() throws Exception
    {
        testSimple("1 - 2");
    }

    @Test
    public void testMinus3() throws Exception
    {
        testSimple("1*-2");
    }
    @Test
    public void testMinus4() throws Exception
    {
        testSimple("1--2");
    }

    @Test
    public void testMinus5() throws Exception
    {
        testSimple("1*(-2+3)");
    }
    
    @Test
    public void testBracket() throws Exception
    {
        testSimple("1+(1.5+@call @function abs(2+(3*4)+6))-7");
    }
    
    @Test
    public void testDateLiteral() throws Exception
    {
        testSimple("date{2001-04-05}");
    }

    @Test
    public void testEmpties() throws Exception
    {
        testSimple("(\"\"=\"\") & ([] <> [])");
    }
    
    @Test
    public void testTupleType() throws Exception
    {
        testSimple("@call @function asType(type{(Number, Boolean)}, @call @function from text(\"(1, true)\"))");
    }

    @Test
    public void testNestedTupleType() throws Exception
    {
        testSimple("@call @function asType(type{((Number, Boolean), Text)}, @call @function from text(\"((1, true), ^qhi^q)\"))");
    }
    
    @Test
    public void testTupleAndListType() throws Exception
    {
        testSimple("@call @function asType(type{[[(Number, [Boolean])]]}, @call @function from text(\"[]\"))");
    }

    @Test
    public void testList() throws Exception
    {
        testSimple("[1, 2, 3]");
    }
    
    @Test
    public void testList2() throws Exception
    {
        testSimple("1 + 2 + [3 * 4] + 5");
    }

    @Test
    public void testList3() throws Exception
    {
        testSimple("1 + 2 + [@invalidops(3, @unfinished \"*\", 4, @unfinished \"/\", 5)] + 6");
    }
    
    @Test
    public void testMarch() throws Exception
    {
        testSimple("@match @call @function from text to(type{DateTime}, \"2047-12-23 10:50:09.094335028\") @case @call @function from text to(type{DateTime}, \"2024-06-09 13:26:01.165156525\") @given true @orcase @call @function datetime(date{8848-10-02}, time{14:57:00}) @given (true = @call @function from text to(type{Boolean}, \"true\") = true = @call @function from text to(type{Boolean}, \"true\")) @then @call @function from text to(type{(Number, Number)}, \"(7,242784)\") @case @anything @given true @orcase @call @function from text to(type{DateTime}, @call @function from text to(type{Text}, \"^q2914-03-04 09:00:00.753695607^q\")) @orcase @call @function from text to(type{DateTime}, \"\") @given true @orcase @newvar var11 @given (var11 = @call @function second(@call @function second(@call @function from text to(type{(Number {(USD*m)/s^2}, (DateTime, DateTime), [Text], Number)}, \"(-2147483649,(2047-09-04 22:11:00,2047-12-23 10:50:09.094335028),[^qUNITS^q,^qknr90rr9rra^q,^qX^q],1609257947333)\")))) @then (3, 4) @endmatch");
    }

    @Test
    public void testConcat() throws Exception
    {
        testSimple("\"\";(\"A\";\"B\")");
    }
    */
}
