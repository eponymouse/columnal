/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package test.gui.expressionEditor;

import javafx.stage.Window;
import org.testjavafx.FxRobot;
import org.testjavafx.FxRobotInterface;
import com.google.common.collect.ImmutableList;
import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import test.functions.TFunctionUtil;
import test.gui.TAppUtil;
import test.gui.TFXUtil;
import xyz.columnal.log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testfx.util.WaitForAsyncUtils;
import xyz.columnal.data.CellPosition;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.KnownLengthRecordSet;
import xyz.columnal.data.MemoryBooleanColumn;
import xyz.columnal.id.TableId;
import xyz.columnal.data.Transformation;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.gui.MainWindow.MainWindowActions;
import xyz.columnal.gui.View;
import xyz.columnal.gui.grid.RectangleBounds;
import xyz.columnal.transformations.Calculate;
import xyz.columnal.transformations.expression.*;
import xyz.columnal.transformations.function.FunctionList;
import test.DummyManager;
import test.gen.ExpressionValue;
import test.gen.GenExpressionValueBackwards;
import test.gen.GenExpressionValueForwards;
import test.gen.GenRandom;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.EnterExpressionTrait;
import test.gui.trait.ListUtilTrait;
import test.gui.trait.PopupTrait;
import test.gui.trait.ScrollToTrait;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@OnThread(Tag.Simulation)
public class TestExpressionEditor extends BaseTestExpressionEditorEntry implements ListUtilTrait, ScrollToTrait, EnterExpressionTrait, ClickTableLocationTrait, PopupTrait
{
    @Override
    @OnThread(value = Tag.Any)
    public FxRobotInterface write(char character)
    {
        Log.normal("Pressing: {{{" + character + "}}}");
        return super.write(character);
    }

    @Override
    @OnThread(value = Tag.Any)
    public FxRobotInterface write(String text)
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


    public @Nullable Expression plainEntry(String expressionSrc, TypeManager typeManager) throws Exception
    {
        TFXUtil.fx_(() -> {windowToUse = new Stage();});
        MainWindowActions mainWindowActions = TAppUtil.openDataAsTable(windowToUse, typeManager, new KnownLengthRecordSet(ImmutableList.of(), 0));
        try
        {
            Region gridNode = TFXUtil.fx(() -> mainWindowActions._test_getVirtualGrid().getNode());
            CellPosition targetPos = new CellPosition(CellPosition.row(3), CellPosition.col(5));
            keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), targetPos);
            // Only need to click once as already selected by keyboard:
            for (int i = 0; i < 1; i++)
                clickOnItemInBounds(fromNode(gridNode), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
            // Not sure why this doesn't work:
            //clickOnItemInBounds(lookup(".create-table-grid-button"), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
            correctTargetWindow().clickOn(".id-new-transform");
            correctTargetWindow().clickOn(".id-transform-calculate");
            correctTargetWindow().write("Table1");
            push(KeyCode.ENTER);
            TFXUtil.sleep(200);
            write("DestCol");
            // Focus expression editor:
            push(KeyCode.TAB);
            enterAndDeleteSmartBrackets(expressionSrc);

            // Close dialog, ignoring errors:
            TFXUtil.doubleOk(this);
            // Now close dialog, and check for equality;
            correctTargetWindow();
            View view = waitForOne(".view");
            assertNotShowing("OK button", ".ok-button");
            Calculate calculate = (Calculate) view.getManager().getAllTables().stream().filter(t -> t instanceof Transformation).findFirst().orElseThrow(() -> new RuntimeException("No transformation found"));

            // Check expressions match:
            Expression expression = calculate.getCalculatedColumns().values().iterator().next();
            
            // If test is success, ignore exceptions (which seem to occur due to hiding error display popup):
            // Shouldn't really need this code but test is flaky without it due to some JavaFX animation-related exceptions:
            TFXUtil.sleep(2000);
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

    private void testSimpleQ(String expressionSrc, String... qualifiedIdentsToEnterInFull) throws Exception
    {
        DummyManager dummyManager = TFunctionUtil.managerWithTestTypes().getFirst();
        Expression expression = TFunctionUtil.parseExpression(expressionSrc, dummyManager.getTypeManager(), FunctionList.getFunctionLookup(dummyManager.getUnitManager()));
        
        // Check once using structured entry:
        testEntry_Impl(new ExpressionValue(
            DataType.BOOLEAN, // Type is unused here
            ImmutableList.of(),
            dummyManager.getTypeManager(),
            new TableId("TEE"),
            new KnownLengthRecordSet(ImmutableList.of(rs -> new MemoryBooleanColumn(rs, new ColumnId("Col1"), ImmutableList.of(), true)), 0),
            expression,
            null
        ), new Random(0), qualifiedIdentsToEnterInFull);
    }

    private void testSimple(String expressionSrc) throws Exception
    {
        testSimpleQ(expressionSrc);
    }

    private void testSimple(String expressionSrc, String plainEntry) throws Exception
    {
        DummyManager dummyManager = TFunctionUtil.managerWithTestTypes().getFirst();
        Expression expression = TFunctionUtil.parseExpression(expressionSrc, dummyManager.getTypeManager(), FunctionList.getFunctionLookup(dummyManager.getUnitManager()));

        assertEquals(expression, plainEntry(plainEntry, dummyManager.getTypeManager()));
    }

    @Test
    public void testGreaterMinus() throws Exception
    {
        testSimple("2>-1");
    }
    
    @Test
    public void testTuple() throws Exception
    {
        testSimple("(k:1, v:2)");
    }

    @Test
    public void testCall() throws Exception
    {
        testSimple("@call function\\\\list\\get single([1])");
    }

    @Test
    public void testCall2() throws Exception
    {
        testSimple("@call function\\\\number\\abs(3{m})");
    }

    @Test
    public void testCall3() throws Exception
    {
        testSimple("@call function\\\\list\\element([1, 2], 1)");
    }

    @Test
    public void testCall4() throws Exception
    {
        testSimple("@call function\\\\number\\abs(2/3)");
    }

    @Test
    public void testCall5() throws Exception
    {
        testSimple("@call tag\\\\Optional\\Is(1)");
    }

    @Test
    public void testCall6() throws Exception
    {
        testSimple("@call tag\\\\A\\Single(1)");
    }

    @Test
    public void testCall7() throws Exception
    {
        testSimple("1 + @call tag\\\\Optional\\Is(@call tag\\\\A\\Single(1))");
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
        testSimple("1+(2/@call function\\\\number\\abs(3*4))+5");
    }
    
    @Test
    public void testBracket3() throws Exception
    {
        testSimple("1+(1.5+@call function\\\\number\\abs(2+(3*4)+6))-7");
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
        testSimple("tag\\\\A\\Single = tag\\\\A\\Single");
    }
    
    @Test
    public void testOverlappingTagNames() throws Exception
    {
        testSimple("@match (@if (tag\\\\A\\Single = tag\\\\A\\Single) @then @call function\\\\conversion\\from text to(type{B}, \"Single\") @else tag\\\\B\\Single @endif) @case tag\\\\B\\Single @given @call function\\\\conversion\\from text to(type{Boolean}, \"true\") @then @call function\\\\conversion\\from text to(type{(a: Nested, b: Number {cm})}, \"(a: A(Single), b:-2147483648)\") @endmatch");
    }
    
    @Test
    public void testTupleType() throws Exception
    {
        testSimple("@call function\\\\core\\as type(type{(t 0:Number, t 1:Boolean)}, @call function\\\\conversion\\from text(\"(1, true)\"))");
    }

    @Test
    public void testNestedTupleType() throws Exception
    {
        testSimple("@call function\\\\core\\as type(type{(a 0: (a 0: Number, b 1: Boolean), b 1: Text)}, @call function\\\\conversion\\from text(\"((1, true), ^qhi^q)\"))");
    }

    @Test
    public void testMaybeType() throws Exception
    {
        testSimple("@call function\\\\core\\as type(type{[(k:(k:Text, v:@apply " + TypeManager.MAYBE_NAME + "(Boolean)), v:@apply "+ TypeManager.MAYBE_NAME + "(@apply "+ TypeManager.MAYBE_NAME +  "(Date)))]}, @call function\\\\conversion\\from text(\"[]\"))");
    }
    
    @Test
    public void testTupleAndListType() throws Exception
    {
        testSimple("@call function\\\\core\\as type(type{[[(k k:Number, v v:[Boolean])]]}, @call function\\\\conversion\\from text(\"[]\"))");
    }
    
    @Test
    public void testNumberType() throws Exception
    {
        testSimple("@call function\\\\core\\as type(type{Number{1}}, @call function\\\\conversion\\from text(\"3\"))");
    }

    @Test
    public void testNumberType2() throws Exception
    {
        testSimple("@call function\\\\core\\as type(type{Number{m/s}}, @call function\\\\conversion\\from text(\"3\"))");
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
        testSimple("[(k:1, v:2)]");
    }
    
    @Test
    public void testMatch() throws Exception
    {
        testSimple("@match @call function\\\\conversion\\from text to(type{DateTime}, \"2047-12-23 10:50:09.094335028\") @case @call function\\\\conversion\\from text to(type{DateTime}, \"2024-06-09 13:26:01.165156525\") @given true @orcase @call function\\\\datetime\\datetime from dt(date{8848-10-02}, time{14:57:00}) @given (true = @call function\\\\conversion\\from text to(type{Boolean}, \"true\") = true = @call function\\\\conversion\\from text to(type{Boolean}, \"true\")) @then @call function\\\\conversion\\from text to(type{(a: Number, b: Number)}, \"(a: 7, b: 242784)\") @case _ @given true @orcase @call function\\\\conversion\\from text to(type{DateTime}, @call function\\\\conversion\\from text to(type{Text}, \"^q2914-03-04 09:00:00.753695607^q\")) @orcase @call function\\\\conversion\\from text to(type{DateTime}, \"\") @given true @orcase var\\\\var11 @given var\\\\var11 = ((@call function\\\\conversion\\from text to(type{(a: Number {(USD*m)/s^2}, b:(c: DateTime, d: DateTime), e:[Text], f:Number)}, \"(-2147483649,(2047-09-04 22:11:00,2047-12-23 10:50:09.094335028),[^qUNITS^q,^qknr90rr9rra^q,^qX^q],1609257947333)\")#b)#d) @then (a:3, b:4) @endmatch");
    }

    @Test
    public void testMatchAny() throws Exception
    {
        testSimple("@match \"\" @case var\\\\prefix ; \"mid\" ; _ @then \"Y\" ; var\\\\prefix @case _ @then \"\" @endmatch");
    }

    @Test
    public void testConcat() throws Exception
    {
        testSimple("\"\";(\"A\";\"B\")");
    }
    
    @Test
    public void testTableReference() throws Exception
    {
        testSimple("table\\\\TEE");
    }

    @Test
    public void testDefine() throws Exception
    {
        testSimple("@define var\\\\x = 3, var\\\\y = var\\\\x @then var\\\\x / var\\\\y @enddefine");
    }

    @Test
    public void testDefine2() throws Exception
    {
        testSimple("@define var\\\\x :: type{Number}, var\\\\x = 3, var\\\\y = var\\\\x @then var\\\\x / var\\\\y @enddefine");
    }

    @Test
    public void testFieldAccess() throws Exception
    {
        testSimple("@define var\\\\x 1 = (a 0: 5, b 3: 6) @then (var\\\\x 1#a 0) + ( var\\\\x 1 # b 3 ) @enddefine");
    }
    
    @Test
    public void testRecordList() throws Exception
    {
        testSimpleQ("@call function\\\\conversion\\from text(@call function\\\\text\\replace many([(find:\"nw\",replace:\"0\"),(find:\"~\",replace:\"-\")],column\\\\Wind m s))", "column\\\\Wind m s");
    }

    @Test
    public void testLambda() throws Exception
    {
        // Can't have function return type because it can't be stored in a column:
        testSimple("@define var\\\\f = @function(var\\\\x) @then var\\\\x + 3 @endfunction @then @call var\\\\f(1) @enddefine");
    }

    @Test
    public void testLambda2() throws Exception
    {
        // Can't have function return type because it can't be stored in a column:
        testSimple("@define var\\\\f = @function(var\\\\x, _, 3 \u00B1 4) @then var\\\\x + 3 @endfunction @then true @enddefine");
    }

    @Test
    public void testLambda3() throws Exception
    {
        // Can't have function return type because it can't be stored in a column:
        testSimple("@define var\\\\f = @function(var\\\\x, _, 3 \u00B1 4) @then var\\\\x + 3 @endfunction @then @call var\\\\f(2, [3], 5) @enddefine");
    }
    
    // Check that if an internal is unterminated, the outer still counts as terminated:
    @Test
    public void testUnclosedInternal1() throws Exception
    {
        testSimple("[@invalidops(@unfinished \"(\", (1+2))]", "[(1+2]");
    }

    @Test
    public void testUnclosedInternal2() throws Exception
    {
        testSimple("@invalidops(@unfinished \"(\", (0 + (1+2)))", "(0+(1+2)");
    }

    @Test
    public void testUnclosedInternal2b() throws Exception
    {
        testSimple("0 + @invalidops(@unfinished \"[\", (1+2))", "(0+[1+2)");
    }

    @Test
    public void testUnclosedInternal3() throws Exception
    {
        testSimple("@if @invalidops(function\\\\number\\abs, @unfinished \"(\", 5) @then @invalidops(@unfinished \"[\", 0) @else 1 @endif", "@if abs(5 @then [0 @else 1 @endif");
    }

    @Ignore // TODO
    @Test
    public void testUnclosedInternal3b() throws Exception
    {
        testSimple("@if @invalidops(function\\\\number\\abs, @unfinished \"(\", 5) @then @invalidops(@unfinished \"[\", 0) @else @invalidops(1, @unfinished \"]\") @endif", "@if abs(5 @then [0 @else 1] @endif");
    }
}
