package test.gui;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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
import records.data.EditableRecordSet;
import records.data.ImmediateDataSource;
import records.data.KnownLengthRecordSet;
import records.data.Table.InitialLoadDetails;
import records.data.TableId;
import records.data.TableManager;
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
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

import java.awt.*;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.Assert.*;

@RunWith(JUnitQuickcheck.class)
@OnThread(Tag.Simulation)
public class TestExpressionEditorDragPaste extends FXApplicationTest implements ListUtilTrait, ScrollToTrait, EnterExpressionTrait, ClickTableLocationTrait, PopupTrait
{    
    // We try dragging slightly to left or right of target divider
    @OnThread(Tag.Any)
    private static enum MoveMethod { DRAG_LEFT, DRAG_RIGHT, CUT_PASTE }
    
    private void testMove(Expression expression,
                          int startNodeIndexIncl, int endNodeIndexIncl,
                          int destBeforeNodexIndexIncl,
                          Expression after,
                          MoveMethod moveMethod,
                          Random r) throws Exception
    {
        TableManager tableManager = new DummyManager();
        ImmediateDataSource dummySource = new ImmediateDataSource(tableManager, new InitialLoadDetails(new TableId("Src"), CellPosition.ORIGIN, null), new EditableRecordSet(ImmutableList.of(), () -> 0));
        tableManager.record(dummySource);
        Calculate calculate = new Calculate(tableManager,
            new InitialLoadDetails(new TableId("Table1"),
                new CellPosition(CellPosition.row(1), CellPosition.col(2)),
                null),
            new TableId("Src"), ImmutableMap.of(new ColumnId("Col"), expression));
        tableManager.record(calculate);
        
        MainWindowActions mainWindowActions = TestUtil.openDataAsTable(windowToUse, tableManager).get();
        try
        {
            View view = lookup(".view").query();
            if (view == null)
            {
                assertNotNull(view);
                return;
            }
            
            Region gridNode = TestUtil.fx(() -> mainWindowActions._test_getVirtualGrid().getNode());
            
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
            ImmutableList<Label> headers = getHeaders(expressionEditor);
            clickOn(headers.get(startNodeIndexIncl));
            press(KeyCode.SHIFT);
            // Shift-click doesn't work headless because of
            // https://github.com/TestFX/Monocle/issues/13
            // so use shift-right instead:
            if (GraphicsEnvironment.isHeadless())
            {
                for (int i = startNodeIndexIncl; i < endNodeIndexIncl; i++)
                    push(KeyCode.RIGHT);
            }
            else
            {
                clickOn(headers.get(endNodeIndexIncl));
            }
            release(KeyCode.SHIFT);
            if (moveMethod == MoveMethod.CUT_PASTE)
            {
                push(KeyCode.SHORTCUT, KeyCode.X);
                headers = getHeaders(expressionEditor);
                int adjustedDest;
                if (destBeforeNodexIndexIncl < startNodeIndexIncl)
                    adjustedDest = destBeforeNodexIndexIncl;
                else
                    adjustedDest = destBeforeNodexIndexIncl - (endNodeIndexIncl - startNodeIndexIncl + 1);
                if (adjustedDest >= headers.size())
                {
                    clickOn(headers.get(headers.size() - 1));
                    push(KeyCode.DOWN);
                    push(KeyCode.END);
                }
                else
                {
                    clickOn(headers.get(adjustedDest));
                    push(KeyCode.DOWN);
                    push(KeyCode.LEFT);
                }
                push(KeyCode.SHORTCUT, KeyCode.V);
            }
            else
            {
                headers = getHeaders(expressionEditor);
                drag(headers.get(startNodeIndexIncl), MouseButton.PRIMARY);
                if (headers.size() == destBeforeNodexIndexIncl)
                {
                    Node target = headers.get(destBeforeNodexIndexIncl - 1);
                    assertNotNull(target);
                    dropTo(TestUtil.fx(() -> target.localToScreen(target.getBoundsInLocal()).getMaxX() + (moveMethod == MoveMethod.DRAG_LEFT ? -2 : 2)), TestUtil.fx(() -> target.localToScreen(target.getBoundsInLocal()).getMinY() + 1));
                }
                else
                {
                    Node target = headers.get(destBeforeNodexIndexIncl);
                    assertNotNull(target);
                    assertTrue("Target " + destBeforeNodexIndexIncl + " not in scene, total " + headers.size(), TestUtil.fx(() -> target.getScene() != null));
                    dropTo(TestUtil.fx(() -> target.localToScreen(target.getBoundsInLocal()).getMinX() + (moveMethod == MoveMethod.DRAG_LEFT ? -2 : 2)), TestUtil.fx(() -> target.localToScreen(target.getBoundsInLocal()).getMinY() + 1));
                }
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

    @OnThread(Tag.Any)
    private ImmutableList<Label> getHeaders(TopLevelEditor<?, ?> expressionEditor)
    {
        return TestUtil.fx(() -> expressionEditor._test_getHeaders().map(Pair::getFirst).collect(ImmutableList.toImmutableList()));
    }

    private void clickOk()
    {
        // Get rid of popups:
        moveAndDismissPopupsAtPos(point(".ok-button"));
        clickOn(MouseButton.PRIMARY);
        // Now close dialog, and check for equality;

        TestUtil.sleep(500);
        assertFalse(lookup(".ok-button").tryQuery().isPresent());
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
            // Don't try to drag to left of first item or right of last:
            // TODO we should probably allow this...
            if (destBeforeNodexIndexIncl > 0 || moveMethod != MoveMethod.DRAG_LEFT)
            {
                testMove(expression, startNodeIndexIncl, endNodeIndexIncl, destBeforeNodexIndexIncl, after, moveMethod, new Random(1L));
            }
        }
    }

    @Test
    public void testTuple() throws Exception
    {
        testSimple("(1, 2, 3)", 3, 4, 1, "(2, 1, 3)");
    }
    
    @Test
    public void testFuncArgToInvalid() throws Exception
    {
        testSimple("@call @function abs(3)",
            2, 3, 0, "@invalidops(3, @unfinished \")\", @function abs, @invalidops(@unfinished \"(\", @unfinished \"\"))");
    }

    @Test
    public void testFuncArgToValid() throws Exception
    {
        testSimple("@invalidops(3, @unfinished \")\", @function abs, @invalidops(@unfinished \"(\", @unfinished \"\"))",
                0, 1, 4, "@call @function abs(3)");
    }
    
    // TODO test mixed editors (types and units in expressions)

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

    @Override
    @OnThread(value = Tag.Any)
    public FxRobot write(String text, int sleepMillis)
    {
        Log.normal("Writing: {{{" + text + "}}}");
        return super.write(text, sleepMillis);
    }
}
