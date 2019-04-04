package test.gui.expressionEditor;

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
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testfx.api.FxRobot;
import org.testfx.util.WaitForAsyncUtils;
import records.data.CellPosition;
import records.data.ColumnId;
import records.data.KnownLengthRecordSet;
import records.data.Transformation;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.gui.MainWindow.MainWindowActions;
import records.gui.View;
import records.gui.grid.RectangleBounds;
import records.importers.ClipboardUtils;
import records.importers.ClipboardUtils.LoadedColumnInfo;
import records.transformations.Calculate;
import records.transformations.expression.*;
import records.transformations.function.FunctionList;
import test.DummyManager;
import test.TestUtil;
import test.gen.ExpressionValue;
import test.gen.GenExpressionValueBackwards;
import test.gen.GenExpressionValueForwards;
import test.gen.GenRandom;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.EnterExpressionTrait;
import test.gui.trait.ListUtilTrait;
import test.gui.trait.PopupTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.Utility;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(JUnitQuickcheck.class)
@OnThread(Tag.Simulation)
public class TestExpressionEditor extends FXApplicationTest implements ListUtilTrait, ScrollToTrait, EnterExpressionTrait, ClickTableLocationTrait, PopupTrait
{
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
        //Log.normal("Writing: {{{" + text + "}}}");
        return super.write(text);
    }

    @Override
    @OnThread(value = Tag.Any)
    public FxRobot write(String text, int sleepMillis)
    {
        Log.normal("Writing: {{{" + text + "}}}");
        return super.write(text, sleepMillis);
    }

    @Property(trials = 10)
    public void testEntry(@When(satisfies = "#_.expressionLength < 500") @From(GenExpressionValueForwards.class) @From(GenExpressionValueBackwards.class) ExpressionValue expressionValue, @From(GenRandom.class) Random r) throws Exception
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
            correctTargetWindow().clickOn(".id-new-transform");
            correctTargetWindow().clickOn(".id-transform-calculate");
            correctTargetWindow().write("Table1");
            push(KeyCode.ENTER);
            TestUtil.sleep(200);
            write("DestCol");
            // Focus expression editor:
            push(KeyCode.TAB);
            Log.normal("Entering expression:\n" + expressionValue.expression.toString() + "\n");
            enterExpression(mainWindowActions._test_getTableManager().getTypeManager(), expressionValue.expression, EntryBracketStatus.SURROUNDED_BY_KEYWORDS, r);
            
            // We check this twice, once for original entry, once for no-op edit:
            for (int i = 0; i < 2; i++)
            {
                // Get rid of popups:
                moveAndDismissPopupsAtPos(point(".ok-button"));
                clickOn(MouseButton.PRIMARY);
                // Now close dialog, and check for equality;
                View view = correctTargetWindow().lookup(".view").query();
                if (view == null)
                {
                    assertNotNull(view);
                    return;
                }
                TestUtil.sleep(500);
                assertNull(lookup(".ok-button").tryQuery().orElse(null));
                Calculate calculate = (Calculate) view.getManager().getAllTables().stream().filter(t -> t instanceof Transformation).findFirst().orElseThrow(() -> new RuntimeException("No transformation found"));
    
                // Check expressions match:
                Expression expression = calculate.getCalculatedColumns().values().iterator().next();
                assertEquals("Loop " + i, expressionValue.expression, expression);
                // Just in case equals is wrong, check String comparison:
                assertEquals("Loop " + i, expressionValue.expression.toString(), expression.toString());
    
                // Check that a no-op edit gives same expression:
                if (i == 0)
                {
                    @SuppressWarnings("units") // Declaration just to allow suppression
                    CellPosition _pos = keyboardMoveTo(view.getGrid(), view.getManager(), calculate.getId(), 0, expressionValue.recordSet.getColumns().size());
                    clickOn("DestCol");
                }
            }

            // Now check values match:
            showContextMenu(".table-display-table-title.transformation-table-title")
                .clickOn(".id-tableDisplay-menu-copyValues");
            TestUtil.sleep(1000);
            Optional<ImmutableList<LoadedColumnInfo>> clip = TestUtil.<Optional<ImmutableList<LoadedColumnInfo>>>fx(() -> ClipboardUtils.loadValuesFromClipboard(expressionValue.typeManager));
            assertTrue(clip.isPresent());
            // Need to fish out first column from clip, then compare item:
            //TestUtil.checkType(expressionValue.type, clip.get().get(0));
            List<Either<String, @Value Object>> actual = clip.get().stream().filter((LoadedColumnInfo p) -> Objects.equals(p.columnName, new ColumnId("DestCol"))).findFirst().orElseThrow(RuntimeException::new).dataValues;
            TestUtil.assertValueListEitherEqual("Transformed", Utility.<@Value Object, Either<String, @Value Object>>mapList(expressionValue.value, x -> Either.<String, @Value Object>right(x)), actual);
            
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

    public @Nullable Expression plainEntry(String expressionSrc, TypeManager typeManager) throws Exception
    {
        TestUtil.fx_(() -> {windowToUse = new Stage();});
        MainWindowActions mainWindowActions = TestUtil.openDataAsTable(windowToUse, typeManager, new KnownLengthRecordSet(ImmutableList.of(), 0));
        try
        {
            Region gridNode = TestUtil.fx(() -> mainWindowActions._test_getVirtualGrid().getNode());
            CellPosition targetPos = new CellPosition(CellPosition.row(3), CellPosition.col(5));
            keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), targetPos);
            // Only need to click once as already selected by keyboard:
            for (int i = 0; i < 1; i++)
                clickOnItemInBounds(from(gridNode), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
            // Not sure why this doesn't work:
            //clickOnItemInBounds(lookup(".create-table-grid-button"), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
            correctTargetWindow().clickOn(".id-new-transform");
            correctTargetWindow().clickOn(".id-transform-calculate");
            correctTargetWindow().write("Table1");
            push(KeyCode.ENTER);
            TestUtil.sleep(200);
            write("DestCol");
            // Focus expression editor:
            push(KeyCode.TAB);
            write(expressionSrc, 1);

            // Get rid of popups:
            moveAndDismissPopupsAtPos(point(".ok-button"));
            clickOn(MouseButton.PRIMARY);
            // Now close dialog, and check for equality;
            View view = correctTargetWindow().lookup(".view").query();
            if (view == null)
            {
                assertNotNull(view);
                return null;
            }
            TestUtil.sleep(500);
            assertNull(lookup(".ok-button").tryQuery().orElse(null));
            Calculate calculate = (Calculate) view.getManager().getAllTables().stream().filter(t -> t instanceof Transformation).findFirst().orElseThrow(() -> new RuntimeException("No transformation found"));

            // Check expressions match:
            Expression expression = calculate.getCalculatedColumns().values().iterator().next();
            
            // If test is success, ignore exceptions (which seem to occur due to hiding error display popup):
            // Shouldn't really need this code but test is flaky without it due to some JavaFX animation-related exceptions:
            TestUtil.sleep(2000);
            WaitForAsyncUtils.clearExceptions();
            return expression;
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
        testSimple(expressionSrc, expressionSrc.replaceAll("@(call|function|apply|tag)", ""));
    }
    
    private void testSimple(String expressionSrc, String plainEntry) throws Exception
    {
        DummyManager dummyManager = TestUtil.managerWithTestTypes().getFirst();
        Expression expression = Expression.parse(null, expressionSrc, dummyManager.getTypeManager(), FunctionList.getFunctionLookup(dummyManager.getUnitManager()));
        
        // Check once using structured entry:
        testEntry(new ExpressionValue(
            DataType.BOOLEAN, // Type is unused here
            ImmutableList.of(),
            dummyManager.getTypeManager(),
            new KnownLengthRecordSet(ImmutableList.of(), 0),
            expression,
            null
        ), new Random(0));
        
        // And once using plain text entry:
        // Plain entry turned off because function calls inside brackets
        // don't work; bracket auto-entered, but not overtyped because
        // they are mismatched.
        //assertEquals(expression, plainEntry(plainEntry, dummyManager.getTypeManager()));
    }

    @Test
    public void testGreaterMinus() throws Exception
    {
        testSimple("2>-1");
    }
    
    @Test
    public void testTuple() throws Exception
    {
        testSimple("(1, 2)");
    }

    @Test
    public void testCall() throws Exception
    {
        testSimple("@call @function first((1, 2))");
    }

    @Test
    public void testCall2() throws Exception
    {
        testSimple("@call @function first((1,(2+3)))");
    }

    @Test
    public void testCall3() throws Exception
    {
        testSimple("@call @function first((1,(2/3)))");
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
    public void testInvalidMinus() throws Exception
    {
        testSimple("@invalidops(-1, @unfinished \"+\", -2, @unfinished \"*\", -3, @unfinished \"-\", -4)", "-1 + -2 * -3 - -4");
    }

    @Test
    public void testInvalidPlus() throws Exception
    {
        testSimple("@invalidops(2, @unfinished \"+\")", "2+");
    }

    @Test
    public void testPlus() throws Exception
    {
        testSimple("+2");
    }

    @Test
    public void testPlus2() throws Exception
    {
        testSimple("1+2");
    }

    @Test
    public void testPlus2b() throws Exception
    {
        testSimple("1 + 2");
    }

    @Test
    public void testPlus2c() throws Exception
    {
        testSimple("\"a\"+2");
    }

    @Test
    public void testPlus3() throws Exception
    {
        testSimple("1*+2");
    }
    @Test
    public void testPlus4() throws Exception
    {
        testSimple("1++2");
    }

    @Test
    public void testPlus5() throws Exception
    {
        testSimple("1*(+2/3)");
    }

    @Test
    public void testBracket() throws Exception
    {
        testSimple("1+(2/(3*4))+5");
    }

    @Test
    public void testBracket2() throws Exception
    {
        testSimple("1+(2/@call @function abs(3*4))+5");
    }
    
    @Test
    public void testBracket3() throws Exception
    {
        testSimple("1+(1.5+@call @function abs(2+(3*4)+6))-7");
    }
    
    @Test
    public void testDateLiteral() throws Exception
    {
        testSimple("date{2001-04-05}");
    }

    @Test
    public void testDateYMLiteral() throws Exception
    {
        testSimple("dateym{2001-04}");
    }

    @Test
    public void testTimeLiteral() throws Exception
    {
        testSimple("time{12:00}");
    }

    @Test
    public void testTimeLiteral2() throws Exception
    {
        testSimple("time{12:00:01.325252666}");
    }

    @Test
    public void testDateTimeLiteral() throws Exception
    {
        testSimple("datetime{2001-04-06 22:44:45}");
    }

    @Test
    public void testDateTimeZonedLiteral() throws Exception
    {
        testSimple("datetimezoned{2001-04-06 22:44:45.667 Europe/London}");
    }

    @Test
    public void testEmpties() throws Exception
    {
        testSimple("(\"\"=\"\") & ([] <> [])");
    }

    @Test
    public void testTagNames() throws Exception
    {
        testSimple("@tag A:Single = @tag A:Single");
    }
    
    @Test
    public void testOverlappingTagNames() throws Exception
    {
        testSimple("@call @function first(@match (@if (@tag A:Single = @tag A:Single) @then @call @function from text to(type{B}, \"Single\") @else @tag B:Single @endif) @case @tag B:Single @given @call @function from text to(type{Boolean}, \"true\") @then @call @function from text to(type{(Nested, Number {cm})}, \"(A(Single),-2147483648)\") @endmatch)");
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
    public void testMaybeType() throws Exception
    {
        testSimple("@call @function asType(type{[((Text, @apply Maybe(Boolean)), @apply Maybe(@apply Maybe(Date)))]}, @call @function from text(\"[]\"))");
    }
    
    @Test
    public void testTupleAndListType() throws Exception
    {
        testSimple("@call @function asType(type{[[(Number, [Boolean])]]}, @call @function from text(\"[]\"))");
    }
    
    @Test
    public void testNumberType() throws Exception
    {
        testSimple("@call @function asType(type{Number{1}}, @call @function from text(\"3\"))");
    }

    @Test
    public void testNumberType2() throws Exception
    {
        testSimple("@call @function asType(type{Number{m/s}}, @call @function from text(\"3\"))");
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
        testSimple("1 + 2 + [@invalidops(3, @unfinished \"*\", 4, @unfinished \"/\", 5)] + 6", 
    "1 + 2 + [3 * 4 / 5] + 6");
    }

    @Test
    public void testList4() throws Exception
    {
        testSimple("[] ; [[[]]] ; [[], [[]], [[4],[],[1,2,3]]]");
    }

    @Test
    public void testSingletonList() throws Exception
    {
        testSimple("[1]");
    }

    @Test
    public void testSingletonList2() throws Exception
    {
        testSimple("[1 / 2]");
    }

    @Test
    public void testSingletonList2b() throws Exception
    {
        testSimple("[1 * 2]");
    }

    @Test
    public void testSingletonList3() throws Exception
    {
        testSimple("[(1, 2)]");
    }
    
    @Test
    public void testMatch() throws Exception
    {
        testSimple("@match @call @function from text to(type{DateTime}, \"2047-12-23 10:50:09.094335028\") @case @call @function from text to(type{DateTime}, \"2024-06-09 13:26:01.165156525\") @given true @orcase @call @function datetime(date{8848-10-02}, time{14:57:00}) @given (true = @call @function from text to(type{Boolean}, \"true\") = true = @call @function from text to(type{Boolean}, \"true\")) @then @call @function from text to(type{(Number, Number)}, \"(7,242784)\") @case _ @given true @orcase @call @function from text to(type{DateTime}, @call @function from text to(type{Text}, \"^q2914-03-04 09:00:00.753695607^q\")) @orcase @call @function from text to(type{DateTime}, \"\") @given true @orcase _var11 @given (var11 = @call @function second(@call @function second(@call @function from text to(type{(Number {(USD*m)/s^2}, (DateTime, DateTime), [Text], Number)}, \"(-2147483649,(2047-09-04 22:11:00,2047-12-23 10:50:09.094335028),[^qUNITS^q,^qknr90rr9rra^q,^qX^q],1609257947333)\")))) @then (3, 4) @endmatch");
    }

    @Test
    public void testMatchAny() throws Exception
    {
        testSimple("@match \"\" @case _prefix ; \"mid\" ; _ @then \"Y\" ; prefix @case _ @then \"\" @endmatch");
    }

    @Test
    public void testConcat() throws Exception
    {
        testSimple("\"\";(\"A\";\"B\")");
    }
}
