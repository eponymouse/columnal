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

import com.google.common.primitives.Ints;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import test.functions.TFunctionUtil;
import test.gui.TAppUtil;
import test.gui.TFXUtil;
import xyz.columnal.log.Log;
import org.apache.commons.lang3.SystemUtils;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import xyz.columnal.data.CellPosition;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.EditableColumn;
import xyz.columnal.data.EditableRecordSet;
import xyz.columnal.data.MemoryNumericColumn;
import xyz.columnal.data.MemoryStringColumn;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.gui.MainWindow.MainWindowActions;
import xyz.columnal.gui.grid.RectangleBounds;
import xyz.columnal.gui.lexeditor.EditorDisplay;
import test.gen.GenRandom;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.EnterTypeTrait;
import test.gui.trait.FocusOwnerTrait;
import test.gui.trait.ListUtilTrait;
import test.gui.trait.PopupTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.function.simulation.SimulationFunction;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

@RunWith(JUnitQuickcheck.class)
@OnThread(Tag.Simulation)
public class TestExpressionEditorPosition extends FXApplicationTest implements ScrollToTrait, ListUtilTrait, ClickTableLocationTrait, FocusOwnerTrait, PopupTrait, EnterTypeTrait
{    
    @Test
    public void testPosition1()
    {
        testCaretPositions("1");
    }

    @Test
    public void testPosition2()
    {
        testCaretPositions("1+2");
    }

    @Test
    public void testPosition3()
    {
        testCaretPositions("\"ab\";\"yz\"");
    }
    
    @Test
    public void testPosition4()
    {
        testCaretPositions("@iftrue@thensum(3+\"az\")@elsefalse@endif");
    }

    @Property(trials=1)
    public void testPos1(@From(GenRandom.class) Random r)
    {
        testCaretPositionsAndDisplay(r, "1", "1", p(0, 1));
    }

    @Property(trials=1)
    public void testPos2(@From(GenRandom.class) Random r)
    {
        testCaretPositionsAndDisplay(r, "1+2", "1 + 2", p(0, 1, 2, 3));
    }

    @Property(trials=1)
    public void testPos3(@From(GenRandom.class) Random r)
    {
        testCaretPositionsAndDisplay(r, "@iftrue@thensum(3+\"az\")@elsefalse@endif", "@if true\n  @then sum(3 + \"az\")\n  @else false\n@endif ", p(0, 3,4,5,6,7, 12,13,14,15,16,17,18,19,20,21,22,23,  28,29,30,31,32,33, 39),
        p(0, 3, 7, 12, 15,16,17,18,19,21,22,23,28,33,39)
        );
    }

    @Property(trials=1)
    public void testPos4(@From(GenRandom.class) Random r)
    {
        testCaretPositionsAndDisplay(r, "ACC1>ACC2", "ACC1 > ACC2", p(0,1,2,3,4, 5,6,7,8,9), p(0, 4, 5, 9));
    }

    @Property(trials=1)
    public void testPos5(@From(GenRandom.class) Random r)
    {
        testCaretPositionsAndDisplay(r, "Str>Str", "Str > Str", p(0,1,2,3, 4,5,6,7), p(0, 3, 4, 7));
    }

    @Property(trials=1)
    public void testPos6(@From(GenRandom.class) Random r)
    {
        testCaretPositionsAndDisplay(r, "ACC1<>3{(m/s)/s}", "ACC1 <> 3{(m/s)/s}", p(0,1,2,3,4, 6,7,8,9,10,11,12,13,14,15,16),
            p(0,4,6,7,8,9,10,11,12,13,14,15,16));
    }

    @Property(trials=1)
    public void testPos7(@From(GenRandom.class) Random r)
    {
        testCaretPositionsAndDisplay(r, "abs(1+2)=sum([3/4])", "abs(1 + 2) = sum([3 / 4])", p(0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19),
            p(0,3,4,5,6,7,8,9,12,13,14,15,16,17,18,19)
                );
    }

    @Property(trials=1)
    public void testPos8(@From(GenRandom.class) Random r)
    {
        int m = TypeManager.MAYBE_NAME.length();
        testCaretPositionsAndDisplay(r, "from string to(type{" + TypeManager.MAYBE_NAME + "(Number{m},Text)},\"Maybe Not\")", "from string to(type{" + TypeManager.MAYBE_NAME +  "(Number{m}, Text)}, \"Maybe Not\")", IntStream.concat(IntStream.range(0, 16), IntStream.range(20, 51 + m)).toArray(),
            p(0, 14, 15, 20, 20+m, 21+m, 27+m,28+m,29+m,30+m,31+m,35+m,36+m,37+m,38+m,39+m,48+m,49+m,50+m));
    }

    @Property(trials=1)
    public void testPos9(@From(GenRandom.class) Random r)
    {
        // Space added in unit because it's an error
        testCaretPositionsAndDisplay(r, "date{2019}<=time{20:10}<type{Number{}}",
            "date{2019} <= time{20:10} < type{Number{ }}",
            p(0, 5,6,7,8,9,10, 12, 17,18,19,20,21,22,23, 24, 29,30,31,32,33,34,35,36,37,38),
            p(0, 5, 9, 10, 12, 17, 22,23,24, 29, 35,36,37,38)
        );
    }

    @Property(trials=1)
    public void testPos10(@From(GenRandom.class) Random r)
    {
        testCaretPositionsAndDisplay(r, "@definex=3@thenx@enddefine", "@define x = 3\n  @then x\n@enddefine ", p(0, 7,8,9,10, 15,16, 26)
        );
    }

    @Property(trials=1)
    public void testPos11(@From(GenRandom.class) Random r)
    {
        testCaretPositionsAndDisplay(r, "@definex=3,abc=x@thenabc@enddefine", "@define x = 3, \n       abc = x\n  @then abc\n@enddefine ", p(0, 7,8,9,10, 11,12,13,14,15,16, 21,22,23,24, 34),
                p(0, 7,8,9,10, 11,14,15,16, 21,24, 34)
        );
    }

    @Property(trials=1)
    public void testPos12(@From(GenRandom.class) Random r)
    {
        testCaretPositionsAndDisplay(r, "@match3@case5@then7@endmatch", "@match 3\n  @case 5 @then 7\n@endmatch ",
            p(0, 6,7, 12,13, 18,19, 28)
        );
    }

    @Property(trials=1)
    public void testPos13(@From(GenRandom.class) Random r)
    {
        testCaretPositionsAndDisplay(r, "@match3@case5@then7@casexyz@thenxyz@endmatch", "@match 3\n  @case 5 @then 7\n  @case xyz @then xyz\n@endmatch ",
            p(0, 6,7, 12,13, 18,19, 24,25,26,27, 32,33,34,35, 44),
            p(0, 6,7, 12,13, 18,19, 24,27, 32,35, 44)
        );
    }

    @Property(trials=1)
    public void testPos14(@From(GenRandom.class) Random r)
    {
        testCaretPositionsAndDisplay(r, "@if1<(2*3)@then0@else1@endif", "@if 1 < (2 * 3)\n  @then 0\n  @else 1\n@endif ", 
            p(0, 3,4,5,6,7,8,9,10,  15,16, 21,22, 28)
        );
    }

    @Property(trials=1)
    public void testPos15(@From(GenRandom.class) Random r)
    {
        testCaretPositionsAndDisplay(r, "@match[3]@case[]@then7@case[1+(2*3)]@then99@case[1]@giventrue&(true|false)@then8@endmatch", 
            "@match [3]\n  @case [] @then 7\n  @case [1 + (2 * 3)] @then 99\n  @case [1] @given true & (true | false) @then 8\n@endmatch ",
            p(0, 6,7,8,9,  14,15,16, 21,22, 27,28,29,30,31,32,33,34,35,36, 41,42,43, 48,49,50,51, 57,58,59,60,61,62,63,64,65,66,67,68,69,70,71,72,73,74, 79,80,89),
            p(0, 6,7,8,9,  14,15,16, 21,22, 27,28,29,30,31,32,33,34,35,36, 41,43, 48,49,50,51, 57,61,62,63,67,68,73,74, 79,80,89));
    }

    @Ignore // TODO restore
    @Property(trials=1)
    public void testPos16(@From(GenRandom.class) Random r)
    {
        testCaretPositionsAndDisplay(r, "@definex::[Text],x=[\"a\";\"b\"]@thenx;(x;x)@enddefine",
            "@define x :: [Text], \n       x = [\"a\" ; \"b\"]\n  @then x ; (x ; x)\n@enddefine ",
            p(0,7,8,10,11,12,13,14,15,16, 17,18,19,20,21,22,23,24,25,26,27,28,  33,34,35,36,37,38,39,40,50),
            p(0,7,8,10,11,15,16, 17,18,19,20,21,22,23,24,25,26,27,28,  33,34,35,36,37,38,39,40,50)
        );
    }

    // This (slightly indirectly) tests loading of scoped identifiers:
    @Property(trials=1)
    public void testPosConstructors(@From(GenRandom.class) Random r)
    {
        testCaretPositionsAndDisplay(r, "1+Is(2)", "1 + Is(2)",
            p(0,1,2,3,4,5,6,7),
            p(0,1,2, 4,5,6,7));
    }

    @Property(trials=1)
    public void testPosConstructors2(@From(GenRandom.class) Random r)
    {
        testCaretPositionsAndDisplay(r, "1+Nested(2)", "1 + Nested(2)",
            p(0,1,2,3,4,5,6,7,8,9,10,11),
            p(0,1,2, 8,9,10,11));
    }

    @Property(trials=1)
    public void testPosConstructors3(@From(GenRandom.class) Random r)
    {
        testCaretPositionsAndDisplay(r, "1+B\\Single(2)", "1 + B\\Single(2)",
            p(0,1,2,3,4,5,6,7,8,9,10,11,12,13),
            p(0,1,2, 10,11,12,13));
    }

    @Property(trials=1)
    public void testPosConstructors4(@From(GenRandom.class) Random r)
    {
        testCaretPositionsAndDisplay(r, "1+Nested(A\\Single(2))", "1 + Nested(A\\Single(2))",
            p(0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21),
            p(0,1,2,8,9,17,18,19,20,21));
    }

    @Property(trials=1)
    public void testPosDoubleSpace(@From(GenRandom.class) Random r)
    {
        testCaretPositionsAndDisplay(r, "The quick  brown fox>Str", "The quick brown fox > Str", p(0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19, 20,21,22,23), p(0,19, 20,23));
    }

    @Property(trials=1)
    public void testPosDoubleSpace2(@From(GenRandom.class) Random r)
    {
        testCaretPositionsAndDisplay(r, "The quick  brown  fox>The quick brown fox", "The quick brown fox > The quick brown fox", p(0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19, 20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39), p(0,19, 20, 39));
    }

    @Property(trials=1)
    public void testPosIncomplete1(@From(GenRandom.class) Random r)
    {
        testCaretPositionsAndDisplay(r, "1+", "1 +  ", p(0, 1, 2));
    }
    
    @Property(trials=1)
    public void testPosIncomplete2(@From(GenRandom.class) Random r)
    {
        testCaretPositionsAndDisplay(r, "1<", "1 <  ", p(0, 1, 2));
    }
    
    @Property(trials=1)
    public void testPosIncomplete2b(@From(GenRandom.class) Random r)
    {
        testCaretPositionsAndDisplay(r, "1<=", "1 <=  ", p(0, 1, 3));
    }
    
    @Property(trials=1)
    public void testPosIncomplete3(@From(GenRandom.class) Random r)
    {
        testCaretPositionsAndDisplay(r, "@i", "@i", p(0, 1, 2), p(0,2));
    }

    @Property(trials=1)
    public void testPosIncomplete4(@From(GenRandom.class) Random r)
    {
        testCaretPositionsAndDisplay(r, "@if", "@if  ", p(0, 3));
    }

    @Property(trials=1)
    public void testPosIncomplete5(@From(GenRandom.class) Random r)
    {
        testCaretPositionsAndDisplay(r, "@if@then@else@endif", "@if  \n  @then  \n  @else  \n@endif ", p(0, 3, 8, 13, 19));
    }

    @Property(trials=1)
    @Ignore // Not clear what right answer is with bracketing in an incomplete expression
    public void testPosIncomplete6(@From(GenRandom.class) Random r)
    {
        testCaretPositionsAndDisplay(r, "@iftrue@then(1+2@else3@endif+1", "@if true\n  @then (1 + 2\n  @else 3\n@endif  + 1",
            p(0, 3,4,5,6,7, 12,13,14,15,16, 21,22, 28,29,30),
            p(0, 3,7, 12,13,14,15,16, 21,22, 28,29,30));
    }

    @Property(trials=1)
    public void testPosIncompleteCase(@From(GenRandom.class) Random r)
    {
        testCaretPositionsAndDisplay(r, "@case", "  @case ", p(0, 5));
    }

    private int[] p(int... values)
    {
        return values;
    }

    @SuppressWarnings("identifier")
    private void testCaretPositionsAndDisplay(Random r, String internalContent, String display, int[] internalCaretPos, int... wordBoundaryCaretPos)
    {
        // If last param missing, means it's same as penultimate:
        if (wordBoundaryCaretPos.length == 0)
            wordBoundaryCaretPos = internalCaretPos;
        try
        {
            UnitManager u = new UnitManager();
            TypeManager typeManager = new TypeManager(u);
            typeManager._test_copyTaggedTypesFrom(TFunctionUtil.managerWithTestTypes().getFirst().getTypeManager());
            List<SimulationFunction<RecordSet, EditableColumn>> columns = new ArrayList<>();
            for (int i = 1; i <= 3; i++)
            {
                int iFinal = i;
                columns.add(rs -> new MemoryStringColumn(rs, new ColumnId("S" + iFinal), Collections.emptyList(), ""));
                columns.add(rs -> new MemoryNumericColumn(rs, new ColumnId("ACC" + iFinal), new NumberInfo(u.loadUse("m/s^2")), Collections.emptyList(), 0));
            }
            columns.add(rs -> new MemoryStringColumn(rs, new ColumnId("Str"), Collections.emptyList(), ""));
            columns.add(rs -> new MemoryStringColumn(rs, new ColumnId("The quick brown fox"), Collections.emptyList(), ""));
            MainWindowActions mainWindowActions = TAppUtil.openDataAsTable(windowToUse, typeManager, new EditableRecordSet(columns, () -> 0));

            Region gridNode = TFXUtil.fx(() -> mainWindowActions._test_getVirtualGrid().getNode());
            CellPosition targetPos = new CellPosition(CellPosition.row(5), CellPosition.col(5));
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

            enterAndDeleteSmartBrackets(internalContent);

            /*
            TFXUtil.fx_(() -> {
                Node n = getFocusOwner();
                if (n != null && n.getScene() != null)
                {
                    FXUtility.addChangeListenerPlatform(n.getScene().focusOwnerProperty(), o -> {
                        Log.logStackTrace("Focus owner now " + o);
                    });
                }
            });
            */

            // Once for initial load, twice for opening editor again
            for (int i = 0; i < 2; i++)
            {
                @MonotonicNonNull Point2D[] caretCentres = new Point2D[internalCaretPos.length];
                assertEquals(display, getDisplayText());

                push(KeyCode.HOME);
                int curIndex = 0;
                while (curIndex < internalCaretPos.length)
                {
                    // Speed up by not assessing every position on every run:
                    if (r.nextInt(10) == 1)
                    {
                        // Make sure to wait for caret reposition on blink:
                        sleep(1300);
                        caretCentres[curIndex] = getCaretPosOnScreen();
                    }
                    assertEquals("Index " + curIndex, internalCaretPos[curIndex], getPosition().getSecond().intValue());
                    curIndex += 1;
                    push(KeyCode.RIGHT);
                }
                push(KeyCode.END);
                curIndex = internalCaretPos.length - 1;
                while (curIndex >= 0)
                {
                    assertEquals(internalCaretPos[curIndex], getPosition().getSecond().intValue());
                    curIndex -= 1;
                    push(KeyCode.LEFT);
                }

                push(KeyCode.HOME);
                curIndex = 0;
                KeyCode wordKey = SystemUtils.IS_OS_MAC_OSX ? KeyCode.ALT : KeyCode.CONTROL;
                press(wordKey);
                while (curIndex < wordBoundaryCaretPos.length)
                {
                    assertEquals("Index " + curIndex, wordBoundaryCaretPos[curIndex], getPosition().getSecond().intValue());
                    curIndex += 1;
                    push(KeyCode.RIGHT);
                }
                push(KeyCode.END);
                curIndex = wordBoundaryCaretPos.length - 1;
                while (curIndex >= 0)
                {
                    assertEquals(wordBoundaryCaretPos[curIndex], getPosition().getSecond().intValue());
                    curIndex -= 1;
                    push(KeyCode.LEFT);
                }
                release(wordKey);

                for (int clickIndex = 0; clickIndex < caretCentres.length; clickIndex++)
                {
                    Point2D caretCentre = caretCentres[clickIndex];
                    if (caretCentre == null)
                        continue;
                    System.out.println("Clicking on " + clickIndex + ": " + caretCentre);
                    //TFXUtil.fx_(() -> dumpScreenshot());
                    moveAndDismissPopupsAtPos(caretCentre);
                    sleep(400);
                    clickOn(caretCentre.add(1, 0));
                    assertEquals("Clicked: " + caretCentre.add(1, 0), internalCaretPos[clickIndex], getPosition().getSecond().intValue());
                    // Try double-click just after the position:
                    push(KeyCode.LEFT);
                    // Double click is unreliable in TestFX, so fake it:
                    Point2D doubleClick = caretCentre.add(1, 0);
                    EditorDisplay editorDisplay = getEditorDisplay();
                    TFXUtil.fx_(() -> editorDisplay._test_doubleClickOn(doubleClick));
                    int lhsSel = findPrev(wordBoundaryCaretPos, internalCaretPos[clickIndex == internalCaretPos.length - 1 ? clickIndex - 1 : clickIndex]);
                    assertEquals("Double clicked: " + caretCentre.add(1, 0), lhsSel, getAnchorPosition());
                    assertEquals("Double clicked: " + caretCentre.add(1, 0), findNext(wordBoundaryCaretPos, internalCaretPos[clickIndex]), getPosition().getSecond().intValue());
                    
                    // Cancel selection:
                    push(KeyCode.LEFT);
                    assertEquals("Left after click: " + caretCentre.add(1, 0), lhsSel, getPosition().getSecond().intValue());
                }

                // Dismiss dialog:
                TFXUtil.doubleOk(this);
                
                if (i == 0)
                {
                    // Bring editor up again:
                    keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), targetPos.offsetByRowCols(1, columns.size()));
                    push(KeyCode.ENTER);
                    clickOn("DestCol");
                    push(KeyCode.TAB);
                }
            }
            
        }
        catch (Exception e)
        {
            // Test fails regardless, so no harm turning checked exception
            // into unchecked for simpler signatures:
            throw new RuntimeException(e);
        }
    }

    private int findPrev(int[] wordBoundaryCaretPos, int caretPos)
    {
        return wordBoundaryCaretPos[Utility.findLastIndex(Ints.asList(wordBoundaryCaretPos), w -> w <= caretPos).orElse(0)];
    }

    private int findNext(int[] wordBoundaryCaretPos, int caretPos)
    {
        return wordBoundaryCaretPos[Utility.findFirstIndex(Ints.asList(wordBoundaryCaretPos), w -> w > caretPos).orElse(wordBoundaryCaretPos.length - 1)];
    }

    // Tests that you can get back to the caret positions 
    // that were seen during insertion, by using left and
    // right cursor keys
    @SuppressWarnings("identifier")
    private void testCaretPositions(String content)
    {
        try
        {
            UnitManager u = new UnitManager();
            TypeManager typeManager = new TypeManager(u);
            typeManager._test_copyTaggedTypesFrom(TFunctionUtil.managerWithTestTypes().getFirst().getTypeManager());
            List<SimulationFunction<RecordSet, EditableColumn>> columns = new ArrayList<>();
            for (int i = 1; i <= 3; i++)
            {
                int iFinal = i;
                columns.add(rs -> new MemoryStringColumn(rs, new ColumnId("S" + iFinal), Collections.emptyList(), ""));
                columns.add(rs -> new MemoryNumericColumn(rs, new ColumnId("ACC" + iFinal), new NumberInfo(u.loadUse("m/s^2")), Collections.emptyList(), 0));
            }
            MainWindowActions mainWindowActions = TAppUtil.openDataAsTable(windowToUse, typeManager, new EditableRecordSet(columns, () -> 0));

            Region gridNode = TFXUtil.fx(() -> mainWindowActions._test_getVirtualGrid().getNode());
            CellPosition targetPos = new CellPosition(CellPosition.row(5), CellPosition.col(5));
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
            
            write(content);
            
            TFXUtil.fx_(() -> {
                Node n = getFocusOwner();
                if (n != null && n.getScene() != null)
                {
                    FXUtility.addChangeListenerPlatform(n.getScene().focusOwnerProperty(), o -> {
                        Log.logStackTrace("Focus owner now " + o);
                    });
                }
            });
            
            
            // We check that if we go all-left then all-right, we reach a termination point
            // in each case (as opposed to looping forever somewhere in the middle)
            
            Pair<EditorDisplay, Integer> curPosition = getPosition();
            Pair<EditorDisplay, Integer> oldPosition = null;
            int maxRemaining = 3 * content.length() + 5;
            while (!curPosition.equals(oldPosition) && --maxRemaining > 0)
            {                
                push(KeyCode.LEFT);
                oldPosition = curPosition;
                curPosition = getPosition();
            }
            assertNotEquals(0, maxRemaining);

            curPosition = getPosition();
            oldPosition = null;
            maxRemaining = 3 * content.length() + 5;
            while (!curPosition.equals(oldPosition) && --maxRemaining > 0)
            {
                push(KeyCode.RIGHT);
                oldPosition = curPosition;
                curPosition = getPosition();
            }
            assertNotEquals(0, maxRemaining);
            
            // Dismiss dialog:
            push(KeyCode.ESCAPE);
            push(KeyCode.ESCAPE);
            TFXUtil.doubleOk(this);
        }
        catch (Exception e)
        {
            // Test fails regardless, so no harm turning checked exception
            // into unchecked for simpler signatures:
            throw new RuntimeException(e);
        }
    }

    private Pair<EditorDisplay, Integer> getPosition()
    {
        EditorDisplay textField = getEditorDisplay();
        return new Pair<>(textField, TFXUtil.fx(() -> textField.getCaretPosition()));
    }

    private EditorDisplay getEditorDisplay()
    {
        Node focusOwner = getFocusOwner();
        if (!(focusOwner instanceof EditorDisplay))
            throw new RuntimeException("Focus owner is " + (focusOwner == null ? "null" : focusOwner.getClass().toString()));
        return (EditorDisplay) focusOwner;
    }

    private int getAnchorPosition()
    {
        EditorDisplay textField = getEditorDisplay();
        return TFXUtil.fx(() -> textField.getAnchorPosition());
    }
    
    private Point2D getCaretPosOnScreen()
    {
        EditorDisplay editorDisplay = getPosition().getFirst();
        Node caret = TFXUtil.fx(() -> editorDisplay.lookup(".document-caret"));
        return TFXUtil.fx(() -> FXUtility.getCentre(caret.localToScreen(caret.getBoundsInLocal())));
    }
    
    private String getDisplayText()
    {
        Pair<EditorDisplay, Integer> pos = getPosition();
        return TFXUtil.fx(() -> Utility.filterClass(((TextFlow) pos.getFirst().lookup(".document-text-flow")).getChildren().stream(), Text.class).map(t -> t.getText()).collect(Collectors.joining()));
    }
}
