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

package test.gui;

import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.input.KeyCode;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import xyz.columnal.data.CellPosition;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.KnownLengthRecordSet;
import xyz.columnal.data.MemoryNumericColumn;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.gui.table.DataCellSupplier.VersionedSTF;
import xyz.columnal.gui.MainWindow.MainWindowActions;
import test.gen.GenRandom;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Utility;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static com.google.common.collect.ImmutableList.of;
import static org.junit.Assert.*;

@OnThread(Tag.Simulation)
@RunWith(JUnitQuickcheck.class)
public class TestNumberColumnDisplay extends FXApplicationTest
{
    // The inside items are the centre-side of the ellipsis
    @OnThread(Tag.Any)
    private static enum Target { FAR_LEFT, FAR_LEFT_AFTER_MINUS, INSIDE_LEFT, MIDDLE, INSIDE_RIGHT, FAR_RIGHT }
    
    /**
     * Given a list of numbers (as strings), displays them in GUI and checks that:
     * (a) they all match the given truncated list when unfocused
     * (b) they switch to the full number when focused
     */
    private void testNumbers(ImmutableList<String> actualValues, ImmutableList<String> expectedGUI) throws Exception
    {
        MainWindowActions mwa = TAppUtil.openDataAsTable(windowToUse, null, new KnownLengthRecordSet(ImmutableList.of((RecordSet rs) -> 
            new MemoryNumericColumn(rs, new ColumnId("C"), new NumberInfo(Unit.SCALAR), actualValues.stream())
        ), actualValues.size()));
        
        CellPosition tablePos = mwa._test_getTableManager().getAllTables().get(0)._test_getPrevPosition();
        
        TFXUtil.sleep(2000);

        ArrayList<@Nullable VersionedSTF> cells = new ArrayList<>(expectedGUI.size());

        for (int i = 0; i < expectedGUI.size(); i++)
        {
            final int iFinal = i;
            @Nullable String cellText = TFXUtil.<@Nullable String>fx(() -> {
                @Nullable VersionedSTF cell = mwa._test_getDataCell(tablePos.offsetByRowCols(3 + iFinal, 0));
                synchronized (this)
                {
                    cells.add(cell);
                }
                if (cell != null)
                    return getText(cell);
                else
                    return null;
            });
            assertEquals("Row " + i, expectedGUI.get(i), cellText);
        }
        
        ArrayList<Double> rightOfLowestIntDigit = new ArrayList<>();
        for (@Nullable VersionedSTF cell : cells)
        {
            if (cell != null)
            {
                String cellText = TFXUtil.fx(() -> cell._test_getGraphicalText());
                int lastIntDigit = cellText.indexOf('.') - 1;
                if (lastIntDigit < 0)
                    lastIntDigit = cellText.length() - 1;
                int lastIntDigitFinal = lastIntDigit;
                rightOfLowestIntDigit.add(TFXUtil.fx(() -> cell._test_getCharacterBoundsOnScreen(lastIntDigitFinal).getMaxX()));
            }
        }
        double leftmostX = rightOfLowestIntDigit.stream().mapToDouble(d -> d).min().orElse(0);
        double rightmostX = rightOfLowestIntDigit.stream().mapToDouble(d -> d).max().orElse(Double.MAX_VALUE);
        MatcherAssert.assertThat(rightmostX, Matchers.closeTo(leftmostX, 1));
        
        // Check what happens when you click into number to edit:

        for (int i = 0; i < expectedGUI.size(); i++)
        {
            @Nullable VersionedSTF cell = cells.get(i);            
            final @Nullable String cellText;
            if (cell != null)
            {
                // Click twice to edit:
                clickOn(cell);
                sleep(200);
                push(KeyCode.ENTER);
                sleep(200);
                VersionedSTF cellFinal = cell;
                cellText = TFXUtil.fx(() -> getText(cellFinal));
            }
            else
                cellText = null;
            assertEquals("Row " + i, actualValues.get(i), cellText);
            push(KeyCode.ESCAPE);
            sleep(2000);
        }
        
        // Clicking away from the edges is obvious.  For far sides of ellipsis, we do basic thing: click lands
        // on the far side of the digit that the ellipsis directly replaced.

        for (Target target : Target.values())
        {
            for (int i = 0; i < expectedGUI.size(); i++)
            {
                @Nullable VersionedSTF cell = cells.get(i);
                final @Nullable String cellText;
                String actual = actualValues.get(i);
                if (cell != null)
                {
                    @NonNull VersionedSTF cellFinal = cell;
                    // Click twice to edit, so click once now:
                    clickOn(cell);
                    TFXUtil.sleep(400);
                    String gui = expectedGUI.get(i);
                    String guiMinusEllipsis = gui.replaceAll("\u2026", "").replaceAll("-", "").replaceAll("\\. ", "").trim();
                    
                    Function<Integer, Point2D> posOfCaret = n -> {
                        Bounds b;
                        String curText = TFXUtil.fx(() -> getText(cellFinal));
                        if (n < curText.length())
                        {
                            b = TFXUtil.fx(() -> cellFinal._test_getCharacterBoundsOnScreen(n));
                            return new Point2D(b.getMinX(), b.getMinY() + b.getHeight() * 0.5);
                        }
                        else if (n == curText.length())
                        {
                            b = TFXUtil.fx(() -> cellFinal._test_getCharacterBoundsOnScreen(n - 1));
                            return new Point2D(b.getMaxX(), b.getMinY() + b.getHeight() * 0.5);
                        }
                        
                        fail("Character not on screen: " + n);
                        // Not needed:
                        return new Point2D(0, 0);
                    };
                    
                    // Second click needs to be at target position:
                    final Point2D clickOnScreenPos;
                    final int afterIndex;
                    int nonEllipsisPos = actual.indexOf(guiMinusEllipsis);
                    assertNotEquals("Should be able to find minus ellipsis string: \"" + guiMinusEllipsis + "\" in \"" + actual + "\"", -1, nonEllipsisPos);
                    boolean startsWithMinus = actual.startsWith("-");
                    boolean ellipsisAtStart = gui.startsWith("\u2026") || gui.startsWith("-\u2026");
                    switch (target)
                    {
                        case FAR_LEFT:
                            clickOnScreenPos = posOfCaret.apply(0);
                            afterIndex = 0; //nonEllipsisPos + (gui.startsWith("\u2026") ? -1 : 0);
                            break;
                        case FAR_LEFT_AFTER_MINUS:
                            clickOnScreenPos = posOfCaret.apply(startsWithMinus ? 1 : 0);
                            afterIndex = startsWithMinus ? 1 : 0; //nonEllipsisPos + (gui.startsWith("\u2026") ? -1 : 0);
                            break;
                        case INSIDE_LEFT:
                            clickOnScreenPos = posOfCaret.apply(startsWithMinus ? 2 : 1);
                            afterIndex = nonEllipsisPos + (ellipsisAtStart ? 0 : 1);
                            break;
                        case INSIDE_RIGHT:
                            clickOnScreenPos = posOfCaret.apply(gui.trim().length() - (gui.trim().endsWith(".") ? 2 : 1));
                            afterIndex = nonEllipsisPos + guiMinusEllipsis.length() + (gui.endsWith("\u2026") ? 0 : -1); 
                            break;
                        case FAR_RIGHT:
                            clickOnScreenPos = posOfCaret.apply(gui.trim().length());
                            if (!gui.endsWith(" "))
                            {
                                Bounds cellScreenBounds = TFXUtil.fx(() -> cell.localToScreen(cell.getBoundsInLocal()));
                                MatcherAssert.assertThat(clickOnScreenPos.getX(), Matchers.closeTo(cellScreenBounds.getMaxX(), 5.0));
                            }
                            afterIndex = nonEllipsisPos + guiMinusEllipsis.length() + (gui.endsWith("\u2026") ? 1 : 0);
                            break;
                        default: // MIDDLE
                            int targetPos = gui.trim().length() / 2;
                            clickOnScreenPos = posOfCaret.apply(targetPos);
                            afterIndex = targetPos + nonEllipsisPos + (ellipsisAtStart ? -1 : 0) + (startsWithMinus ? -1 : 0);
                            break;
                    }
                    clickOn(clickOnScreenPos);
                    
                    assertTrue("Clicked on: " + clickOnScreenPos + " focus owner: " + getFocusOwner(), TFXUtil.fx(() -> cellFinal.isFocused()));
                    assertEquals("Clicking " + target + " before: \"" + gui + "\" after: \"" + actual + "\" pos: " + clickOnScreenPos, afterIndex, 
                        (int) TFXUtil.<Integer>fx(() -> cellFinal.getCaretPosition())
                    );
                    // Double-check cellText while we're here:
                    
                    cellText = TFXUtil.fx(() -> getText(cellFinal));
                } else
                    cellText = null;
                assertEquals("Row " + i, actual, cellText);
                
                // Now exit to make sure text goes back:
                push(KeyCode.ESCAPE);
                // Wait for batched re-layout:
                TFXUtil.sleep(2000);
                assertEquals("Row " + i, expectedGUI.get(i), TFXUtil.<@Nullable String>fx(() -> cell == null ? null : getText(cell)));
            }
        }

    }

    @OnThread(Tag.FXPlatform)
    private String getText(VersionedSTF cell)
    {
        return cell._test_getGraphicalText();
    }

    @Test
    public void testUnaltered() throws Exception
    {
        testNumbers(of("0.1", "1.1", "2.1"), of("0.1", "1.1", "2.1"));
    }

    @Test
    public void testNegative() throws Exception
    {
        testNumbers(of("0.1", "-0.1", "2"), of("0.1", "-0.1", "2. "));
    }

    // Note: need to have stylesheets in place or this will fail.
    @Test
    public void testAllTruncated() throws Exception
    {
        testNumbers(of("0.112233445566778899", "1.112233445566778899", "2.112233445566778899"), of("0.11223344\u2026", "1.11223344\u2026", "2.11223344\u2026"));
    }

    @Test
    public void testAllTruncatedNeg() throws Exception
    {
        testNumbers(of("-0.112233445566778899", "1.112233445566778899", "-2.112233445566778899"), of("-0.1122334\u2026", "1.1122334\u2026", "-2.1122334\u2026"));
    }

    @Test
    public void testSomeTruncated() throws Exception
    {
        testNumbers(of("0.112233445566778899", "1.112233445", "2.11223344", "3.11223344"), of("0.11223344\u2026", "1.112233445", "2.11223344 ", "3.11223344 "));
    }

    @Test
    public void testSomeTruncatedNeg() throws Exception
    {
        testNumbers(of("-0.112233445566778899", "1.112233445", "-2.11223344", "3.112233"), of("-0.1122334\u2026", "1.1122334\u2026", "-2.11223344", "3.112233  "));
    }
    
    @Test
    public void testAllAbbreviated() throws Exception
    {
        testNumbers(of("1234567890", "2234567890", "3234567890"), of("\u202634567890", "\u202634567890", "\u202634567890"));
    }

    @Test
    public void testAllAbbreviatedNeg() throws Exception
    {
        testNumbers(of("1234567890", "-2234567890", "3234567890"), of("\u202634567890", "-\u20264567890", "\u202634567890"));
    }

    @Test
    public void testSomeAbbreviated() throws Exception
    {
        testNumbers(of("1234567890", "234567890", "34567890"), of("\u202634567890", "234567890", "34567890"));
    }

    @Test
    public void testSomeAbbreviatedNeg() throws Exception
    {
        testNumbers(of("-1234567890", "234567890", "34567890"), of("-\u20264567890", "234567890", "34567890"));
    }

    @Test
    public void testMixedUnaltered() throws Exception
    {
        testNumbers(of("123.456", "2", "0.3456"), of("123.456 ", "2.    ", "0.3456"));
    }

    @Test
    public void testBothEnds() throws Exception
    {
        testNumbers(of("1234567890.112233445566778899", "2.3", "3.45", "4.567", "1234567890"), of("\u2026567890.1\u2026", "2.3 ", "3.45", "4.5\u2026", "\u2026567890.  "));
    }

    @Test
    public void testBothEndsNeg() throws Exception
    {
        testNumbers(of("-1234567890.112233445566778899", "2.3", "3.45", "-4.567", "1234567890"), of("-\u202667890.1\u2026", "2.3 ", "3.45", "-4.5\u2026", "\u2026567890.  "));
    }
    
    @Property(trials = 1)
    public void testScrollUpDown(@From(GenRandom.class) Random r) throws Exception
    {
        // Make some columns of ascending numbers and text,
        // then scroll up and down and check relation.
        
        List<String> values = new AbstractList<String>() {

            @Override
            public int size()
            {
                return 1000;
            }

            @Override
            public String get(int index)
            {
                return Integer.toString(index);
            }
        };

        MainWindowActions mwa = TAppUtil.openDataAsTable(windowToUse, null, new KnownLengthRecordSet(ImmutableList.of((RecordSet rs) ->
                new MemoryNumericColumn(rs, new ColumnId("C"), new NumberInfo(Unit.SCALAR), values.stream())
        ), values.size()));
        
        Supplier<List<String>> getCurShowing = () ->
                IntStream.range(0, 1000).mapToObj(i -> Utility.streamNullable(TFXUtil.fx(() -> mwa._test_getDataCell(new CellPosition(CellPosition.row(i), CellPosition.col(1)))))).flatMap(s -> s).map(s -> TFXUtil.fx(() -> getText(s))).collect(ImmutableList.toImmutableList());
        
        checkNumericSorted(getCurShowing.get());

        for (int i = 0; i < 15; i++)
        {
            int iFinal = i;
            TFXUtil.asyncFx_(() -> mwa._test_getVirtualGrid().getScrollGroup().requestScrollBy(0, (iFinal % 4 == 0) ? 1000 : -1000));
            TFXUtil.sleep(1000);
            checkNumericSorted(getCurShowing.get());
        }
        
    }

    private void checkNumericSorted(List<String> numbers)
    {
        for (int i = 1; i < numbers.size(); i++)
        {
            int prev = Integer.valueOf(numbers.get(i - 1));
            int cur = Integer.valueOf(numbers.get(i));
            MatcherAssert.assertThat(prev, Matchers.lessThan(cur));
        }
    }
}
