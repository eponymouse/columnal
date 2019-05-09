package test.gui;

import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.apache.commons.lang3.SystemUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;
import records.data.CellPosition;
import records.data.ColumnId;
import records.data.KnownLengthRecordSet;
import records.data.MemoryNumericColumn;
import records.data.RecordSet;
import records.data.TableManager;
import records.data.datatype.NumberInfo;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.gui.DataCellSupplier;
import records.gui.DataCellSupplier.VersionedSTF;
import records.gui.MainWindow.MainWindowActions;
import test.DummyManager;
import test.TestUtil;
import test.gen.GenRandom;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.common.collect.ImmutableList.of;
import static org.junit.Assert.*;

@OnThread(Tag.Simulation)
@RunWith(JUnitQuickcheck.class)
public class TestNumberColumnDisplay extends FXApplicationTest
{
    @OnThread(Tag.Any)
    private static enum Target { FAR_LEFT, INSIDE_LEFT, MIDDLE, INSIDE_RIGHT, FAR_RIGHT }
    
    /**
     * Given a list of numbers (as strings), displays them in GUI and checks that:
     * (a) they all match the given truncated list when unfocused
     * (b) they switch to the full number when focused
     */
    private void testNumbers(ImmutableList<String> actualValues, ImmutableList<String> expectedGUI) throws Exception
    {
        MainWindowActions mwa = TestUtil.openDataAsTable(windowToUse, null, new KnownLengthRecordSet(ImmutableList.of((RecordSet rs) -> 
            new MemoryNumericColumn(rs, new ColumnId("C"), new NumberInfo(Unit.SCALAR), actualValues.stream())
        ), actualValues.size()));
        
        // Bit of a hack, but font rendering seems different by OS:
        // Not necessary any more?
        //if (SystemUtils.IS_OS_WINDOWS)
            //TestUtil.fx_(() -> mwa._test_getVirtualGrid()._test_setColumnWidth(0, 100));
        
        CellPosition tablePos = mwa._test_getTableManager().getAllTables().get(0)._test_getPrevPosition();
        
        TestUtil.sleep(2000);

        ArrayList<@Nullable VersionedSTF> cells = new ArrayList<>(expectedGUI.size());

        for (int i = 0; i < expectedGUI.size(); i++)
        {
            final int iFinal = i;
            @Nullable String cellText = TestUtil.<@Nullable String>fx(() -> {
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
                cellText = TestUtil.fx(() -> getText(cellFinal));
            }
            else
                cellText = null;
            assertEquals("Row " + i, actualValues.get(i), cellText);
            push(KeyCode.ESCAPE);
            sleep(200);
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
                    TestUtil.sleep(400);
                    String gui = expectedGUI.get(i);
                    String guiMinusEllipsis = gui.replaceAll("\u2026", "").trim();
                    
                    Function<Integer, Point2D> posOfCaret = n -> {
                        Optional<Bounds> b;
                        String curText = TestUtil.fx(() -> getText(cellFinal));
                        if (n < curText.length())
                        {
                            b = TestUtil.fx(() -> cellFinal._test_getCharacterBoundsOnScreen(n));
                            if (b.isPresent())
                                return new Point2D(b.get().getMinX(), b.get().getMinY() + b.get().getHeight() * 0.5);
                        }
                        else if (n == curText.length())
                        {
                            b = TestUtil.fx(() -> cellFinal._test_getCharacterBoundsOnScreen(n - 1));
                            if (b.isPresent())
                                return new Point2D(b.get().getMaxX(), b.get().getMinY() + b.get().getHeight() * 0.5);
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
                    switch (target)
                    {
                        case FAR_LEFT:
                            clickOnScreenPos = posOfCaret.apply(0);
                            afterIndex = 0; //nonEllipsisPos + (gui.startsWith("\u2026") ? -1 : 0);
                            break;
                        case INSIDE_LEFT:
                            clickOnScreenPos = posOfCaret.apply(1);
                            afterIndex = nonEllipsisPos + (gui.startsWith("\u2026") ? 0 : 1);
                            break;
                        case INSIDE_RIGHT:
                            clickOnScreenPos = posOfCaret.apply(gui.trim().length() - 1);
                            afterIndex = nonEllipsisPos + guiMinusEllipsis.length() + (gui.endsWith("\u2026") ? 0 : -1); 
                            break;
                        case FAR_RIGHT:
                            clickOnScreenPos = posOfCaret.apply(gui.trim().length());
                            afterIndex = nonEllipsisPos + guiMinusEllipsis.length() + (gui.endsWith("\u2026") ? 1 : 0);
                            break;
                        default: // MIDDLE
                            int targetPos = gui.trim().length() / 2;
                            clickOnScreenPos = posOfCaret.apply(targetPos);
                            afterIndex = targetPos + nonEllipsisPos + (gui.startsWith("\u2026") ? -1 : 0);
                            break;
                    }
                    clickOn(clickOnScreenPos);
                    
                    assertTrue("Clicked on: " + clickOnScreenPos, TestUtil.fx(() -> cellFinal.isFocused()));
                    assertEquals("Clicking " + target + " before: \"" + gui + "\" after: " + actual, afterIndex, 
                        (int)TestUtil.<Integer>fx(() -> cellFinal.getCaretPosition())
                    );
                    // Double-check cellText while we're here:
                    
                    cellText = TestUtil.fx(() -> getText(cellFinal));
                } else
                    cellText = null;
                assertEquals("Row " + i, actual, cellText);
                
                // Now exit to make sure text goes back:
                push(KeyCode.ESCAPE);
                // Wait for batched re-layout:
                TestUtil.sleep(1000);
                assertEquals("Row " + i, expectedGUI.get(i), TestUtil.<@Nullable String>fx(() -> cell == null ? null : getText(cell)));
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

    // Note: need to have stylesheets in place or this will fail.
    @Test
    public void testAllTruncated() throws Exception
    {
        testNumbers(of("0.112233445566778899", "1.112233445566778899", "2.112233445566778899"), of("0.11223344\u2026", "1.11223344\u2026", "2.11223344\u2026"));
    }

    @Test
    public void testSomeTruncated() throws Exception
    {
        testNumbers(of("0.112233445566778899", "1.112233445", "2.11223344", "3.11223344"), of("0.11223344\u2026", "1.112233445", "2.11223344 ", "3.11223344 "));
    }
    
    @Test
    public void testAllAbbreviated() throws Exception
    {
        testNumbers(of("1234567890", "2234567890", "3234567890"), of("\u202634567890", "\u202634567890", "\u202634567890"));
    }

    @Test
    public void testSomeAbbreviated() throws Exception
    {
        testNumbers(of("1234567890", "234567890", "34567890"), of("\u202634567890", "234567890", "34567890"));
    }

    @Test
    public void testMixedUnaltered() throws Exception
    {
        testNumbers(of("123.456", "2", "0.3456"), of("123.456 ", "2    ", "0.3456"));
    }

    @Test
    public void testBothEnds() throws Exception
    {
        testNumbers(of("1234567890.112233445566778899", "2.3", "3.45", "4.567", "1234567890"), of("\u2026567890.1\u2026", "2.3 ", "3.45", "4.5\u2026", "\u2026567890  "));
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

        MainWindowActions mwa = TestUtil.openDataAsTable(windowToUse, null, new KnownLengthRecordSet(ImmutableList.of((RecordSet rs) ->
                new MemoryNumericColumn(rs, new ColumnId("C"), new NumberInfo(Unit.SCALAR), values.stream())
        ), values.size()));
        
        Supplier<List<String>> getCurShowing = () ->
                IntStream.range(0, 1000).mapToObj(i -> Utility.streamNullable(TestUtil.fx(() -> mwa._test_getDataCell(new CellPosition(CellPosition.row(i), CellPosition.col(1)))))).flatMap(s -> s).map(s -> TestUtil.fx(() -> getText(s))).collect(ImmutableList.toImmutableList());
        
        checkNumericSorted(getCurShowing.get());

        for (int i = 0; i < 15; i++)
        {
            int iFinal = i;
            TestUtil.asyncFx_(() -> mwa._test_getVirtualGrid().getScrollGroup().requestScrollBy(0, (iFinal % 4 == 0) ? 1000 : -1000));
            TestUtil.sleep(1000);
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
