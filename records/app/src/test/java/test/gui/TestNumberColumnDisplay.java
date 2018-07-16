package test.gui;

import com.google.common.collect.ImmutableList;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.stage.Stage;
import org.apache.commons.lang3.SystemUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;
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
import records.gui.DataCellSupplier.VersionedSTF;
import records.gui.MainWindow.MainWindowActions;
import test.DummyManager;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Function;

import static com.google.common.collect.ImmutableList.of;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@OnThread(Tag.Simulation)
public class TestNumberColumnDisplay extends ApplicationTest
{
    @OnThread(Tag.Any)
    @SuppressWarnings("nullness")
    private Stage windowToUse;

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void start(Stage stage) throws Exception
    {
        this.windowToUse = stage;
        FXUtility._test_setTestingMode();
    }

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
        
        TestUtil.sleep(2000);

        ArrayList<@Nullable VersionedSTF> cells = new ArrayList<>(expectedGUI.size());

        for (int i = 0; i < expectedGUI.size(); i++)
        {
            final int iFinal = i;
            @Nullable String cellText = TestUtil.<@Nullable String>fx(() -> {
                @Nullable VersionedSTF cell = mwa._test_getDataCell(new CellPosition(CellPosition.row(3 + iFinal), CellPosition.col(0)));
                synchronized (this)
                {
                    cells.add(cell);
                }
                if (cell != null)
                    return cell.getText();
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
                TestUtil.sleep(400);
                clickOn(cell);
                VersionedSTF cellFinal = cell;
                cellText = TestUtil.fx(() -> cellFinal.getText());
            }
            else
                cellText = null;
            assertEquals("Row " + i, actualValues.get(i), cellText);
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
                    // Click twice to edit:
                    clickOn(cell);
                    TestUtil.sleep(400);
                    String gui = expectedGUI.get(i);
                    String guiMinusEllipsis = gui.replaceAll("\u2026", "");
                    
                    Function<Integer, Point2D> posOfCaret = n -> {
                        Optional<Bounds> b = Optional.empty();
                        String curText = TestUtil.fx(() -> cellFinal.getText());
                        if (n < curText.length())
                        {
                            b = TestUtil.fx(() -> cellFinal.getCharacterBoundsOnScreen(n, n + 1));
                            if (b.isPresent())
                                return new Point2D(b.get().getMinX(), b.get().getMinY() + b.get().getHeight() * 0.5);
                        }
                        else if (n == curText.length())
                        {
                            b = TestUtil.fx(() -> cellFinal.getCharacterBoundsOnScreen(n - 1, n));
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
                    switch (target)
                    {
                        default: // MIDDLE
                            int targetPos = gui.length() / 2;
                            clickOnScreenPos = posOfCaret.apply(targetPos);
                            afterIndex = targetPos + actual.indexOf(guiMinusEllipsis) + (gui.startsWith("\u2026") ? 1 : 0);
                            break;
                    }
                    clickOn(clickOnScreenPos);
                    
                    assertEquals("Before: \"" + gui + "\" after: " + actual, afterIndex, 
                        (int)TestUtil.<Integer>fx(() -> cellFinal.getCaretPosition())
                    );
                    // Double-check cellText while we're here:
                    
                    cellText = TestUtil.fx(() -> cellFinal.getText());
                } else
                    cellText = null;
                assertEquals("Row " + i, actual, cellText);
            }
        }

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
}
