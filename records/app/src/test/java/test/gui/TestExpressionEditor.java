package test.gui;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.application.Platform;
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
import records.error.InternalException;
import records.error.UserException;
import records.grammar.ExpressionLexer;
import records.grammar.ExpressionParser;
import records.gui.MainWindow.MainWindowActions;
import records.gui.View;
import records.gui.grid.RectangleBounds;
import records.importers.ClipboardUtils;
import records.transformations.Calculate;
import records.transformations.TransformationInfo;
import records.transformations.expression.*;
import test.DummyManager;
import test.TestUtil;
import test.gen.ExpressionValue;
import test.gen.GenExpressionValueBackwards;
import test.gen.GenExpressionValueForwards;
import test.gen.GenRandom;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(JUnitQuickcheck.class)
@OnThread(Tag.Simulation)
public class TestExpressionEditor extends ApplicationTest implements ListUtilTrait, ScrollToTrait, EnterExpressionTrait, ClickTableLocationTrait
{
    @SuppressWarnings("nullness")
    private Stage windowToUse;

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void start(Stage stage) throws Exception
    {
        this.windowToUse = stage;
    }

    @Override
    @OnThread(value = Tag.Any)
    public FxRobot write(char character)
    {
        Log.normal("Pressing: {{{" + character + "}}}");
        return super.write(character);
    }

    @Override
    @OnThread(value = Tag.Any)
    public FxRobot write(String text)
    {
        Log.normal("Writing: {{{" + text + "}}}");
        return super.write(text);
    }

    @Override
    @OnThread(value = Tag.Any)
    public FxRobot write(String text, int sleepMillis)
    {
        Log.normal("Writing: {{{" + text + "}}}");
        return super.write(text, sleepMillis);
    }

    // TODO this test seems to cause later tests to fail
    @Property(trials = 10)
    public void testEntry(@When(seed=2L) @From(GenExpressionValueForwards.class) @From(GenExpressionValueBackwards.class) ExpressionValue expressionValue, @When(seed=1L) @From(GenRandom.class) Random r) throws Exception
    {
        MainWindowActions mainWindowActions = TestUtil.openDataAsTable(windowToUse, expressionValue.typeManager, expressionValue.recordSet);
        try
        {
            Region gridNode = TestUtil.fx(() -> mainWindowActions._test_getVirtualGrid().getNode());
            CellPosition targetPos = new CellPosition(CellPosition.row(3), CellPosition.col(3 + expressionValue.recordSet.getColumns().size()));
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
            Log.normal("Entering expression:\n" + expressionValue.expression.toString() + "\n");
            enterExpression(expressionValue.expression, false, r);
            
            moveTo(".ok-button");
            // Get rid of popups:
            clickOn(MouseButton.MIDDLE);
            clickOn(MouseButton.PRIMARY);
            // Now close dialog, and check for equality;
            View view = lookup(".view").query();
            if (view == null)
            {
                assertNotNull(view);
                return;
            }
            TestUtil.sleep(500);
            assertNull(lookup(".ok-button").query());
            Calculate calculate = (Calculate) view.getManager().getAllTables().stream().filter(t -> t instanceof Transformation).findFirst().orElseThrow(() -> new RuntimeException("No transformation found"));

            // Check expressions match:
            Expression expression = calculate.getCalculatedColumns().values().iterator().next();
            assertEquals(expressionValue.expression, expression);
            // Just in case equals is wrong, check String comparison:
            assertEquals(expressionValue.expression.toString(), expression.toString());

            // Now check values match:
            clickOn(".table-display-table-title.transformation-table-title", MouseButton.SECONDARY)
                .clickOn(".id-tableDisplay-menu-copyValues");
            TestUtil.sleep(1000);
            Optional<List<Pair<ColumnId, List<@Value Object>>>> clip = TestUtil.<Optional<List<Pair<ColumnId, List<@Value Object>>>>>fx(() -> ClipboardUtils.loadValuesFromClipboard(expressionValue.typeManager));
            assertTrue(clip.isPresent());
            // Need to fish out first column from clip, then compare item:
            //TestUtil.checkType(expressionValue.type, clip.get().get(0));
            List<@Value Object> actual = clip.get().stream().filter((Pair<ColumnId, List<@Value Object>> p) -> p.getFirst().equals(new ColumnId("DestCol"))).findFirst().orElseThrow(RuntimeException::new).getSecond();
            TestUtil.assertValueListEqual("Transformed", expressionValue.value, actual);

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

    // TODO test that nonsense is preserved after load (which will change it all to invalid) -> save -> load (which should load invalid version)
    
    private void testSimple(String expressionSrc) throws Exception
    {
        Expression expression = Expression.parse(null, expressionSrc, DummyManager.INSTANCE.getTypeManager());
        
        testEntry(new ExpressionValue(
            DataType.BOOLEAN, // Type is unused here
            ImmutableList.of(),
            DummyManager.INSTANCE.getTypeManager(),
            new KnownLengthRecordSet(ImmutableList.of(), 0),
            expression
        ), new Random(0));
    }
    
    @Test
    public void testTuple() throws Exception
    {
        testSimple("(1, 2)");
    }

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
    public void testTempExtract() throws Exception
    {
        testSimple("(((32767{s} - @column GEV Col 1 - @column GEV Col 2 + 0{s} - 61815{s}) * (32767{kg} - @column GEV Col 3) * @call @function asType(type{Number {1/(kg*s)}}, @column GEV Col 4)), (@call @function asType(type{Text}, @column GEV Col 5) ; @column GEV Col 6 ; (\"=cMm\" ; \"prrrZ\" ; \"l'z*\uDB89\uDE8AB!?k^a\" ; @column GEV Col 7)))");
    }
    
    @Test
    public void testPrevFailure1() throws Exception
    {
        testSimple("[@call @function asType(type{(Number, (Text, Number))}, @column GEV Col 0), (((32767{s} - @column GEV Col 1 - @column GEV Col 2 + 0{s} - 61815{s}) * (32767{kg} - @column GEV Col 3) * @call @function asType(type{Number {1/(kg*s)}}, @column GEV Col 4)), ((@call @function asType(type{Text}, @column GEV Col 5) ; @column GEV Col 6 ; (\"=cMm\" ; \"prrrZ\" ; \"l'z*\uDB89\uDE8AB!?k^a\" ; @column GEV Col 7)), ((@if (@column GEV Col 9 = 0{1}) @then -7{1} @else (@column GEV Col 8 / @column GEV Col 9) @endif) - @call @function asType(type{Number}, @column GEV Col 10) + (@column GEV Col 11 * @column GEV Col 12) + 2147483648{1} - (3{1} - @column GEV Col 13 + @column GEV Col 14 - -32767{1} + @column GEV Col 15 + 25383101276006{1})))), @column GEV Col 16, @column GEV Col 17, @call @function asType(type{(Number, (Text, Number))}, @column GEV Col 18), @column GEV Col 19]");
    }
}
