package test.gui;

import annotation.qual.Value;
import annotation.units.TableDataColIndex;
import annotation.units.TableDataRowIndex;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Chars;
import com.google.common.primitives.Ints;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyCombination.Modifier;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import log.Log;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.SystemUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.fxmisc.richtext.model.NavigationActions.SelectionPolicy;
import org.hamcrest.Matchers;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testfx.util.WaitForAsyncUtils;
import records.data.CellPosition;
import records.data.ColumnId;
import records.data.EditableRecordSet;
import records.data.ImmediateDataSource;
import records.data.Table.InitialLoadDetails;
import records.data.TableId;
import records.data.TableManager;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.data.datatype.DataTypeValue.DataTypeVisitorGetEx;
import records.data.datatype.DataTypeValue.GetValue;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeId;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.gui.MainWindow.MainWindowActions;
import records.gui.stf.STFAutoCompleteCell;
import records.gui.stf.StructuredTextField;
import records.gui.stf.StructuredTextField.EditorKit;
import records.gui.stf.TableDisplayUtility.GetDataPosition;
import test.DummyManager;
import test.TestUtil;
import test.gen.*;
import test.gen.GenTypeAndValueGen.TypeAndValueGen;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformSupplier;
import utility.Pair;
import utility.SimulationSupplier;
import utility.TaggedValue;
import utility.Utility;
import utility.Utility.ListExList;
import utility.Workers;
import utility.Workers.Priority;
import utility.gui.FXUtility;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeThat;

/**
 * Created by neil on 19/06/2017.
 */
@RunWith(JUnitQuickcheck.class)
@OnThread(Tag.Simulation)
@Ignore
public class TestStructuredTextField extends FXApplicationTest
{
    @OnThread(Tag.FXPlatform)
    private final ObjectProperty<StructuredTextField> f = new SimpleObjectProperty<>();
    @OnThread(Tag.Any)
    @SuppressWarnings("nullness")
    private TextField dummy;
    @SuppressWarnings("nullness")
    @OnThread(Tag.Simulation)
    private TableManager tableManager;

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void start(Stage stage) throws Exception
    {
        super.start(stage);
        AtomicBoolean simDone = new AtomicBoolean(false);
        Workers.onWorkerThread("Init", Priority.SAVE, () -> {
            try
            {
                MainWindowActions mainWindowActions = TestUtil.openDataAsTable(windowToUse, new DummyManager()).get();
                Platform.runLater(() -> mainWindowActions._test_getVirtualGrid()._test_setColumnWidth(0, 400.0));
                tableManager = mainWindowActions._test_getTableManager();
                simDone.set(true);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        });
        TestUtil.fxYieldUntil(() -> simDone.get());
        dummy = new TextField();
        windowToUse.getScene().setRoot(new VBox(dummy, windowToUse.getScene().getRoot()));
        stage.setMinWidth(800);
        stage.setMinHeight(500);
        stage.show();
        FXUtility.addChangeListenerPlatformNN(f, field -> {
            FXUtility.runAfter(() ->
            {
                stage.getScene().getRoot().layout();
                stage.sizeToScene();
                stage.getScene().getRoot().layout();
                field.requestFocus();
            });
            WaitForAsyncUtils.waitForFxEvents();
        });
    }

    // TODO add tests for cut, copy, paste, select-then-type

    @Test
    public void testPrompt() throws InternalException
    {
        TestUtil.fx_(() -> f.set(dateField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(2034, 10, 29))));
        assertEquals("2034-10-29", TestUtil.fx(() -> f.get().getText()));
        testPositions(new Random(0),
                new int[] {0, 1, 2},
                nul(),
                new int[] {3, 4, 5},
                nul(),
                new int[] {6, 7, 8, 9, 10}
        );

        /*
        TestUtil.fx_(() -> f.set(dateField(new DateTimeInfo(DateTimeType.DATETIME), LocalDateTime.of(2034, 10, 29, 13, 56, 22))));
        assertEquals("2034-10-29 13:56:22", TestUtil.fx(() -> f.get().getText()));
        testPositions(new Random(0),
            new int[] {0, 1, 2},
            nul(),
            new int[] {3, 4, 5},
            nul(),
            new int[] {6, 7, 8, 9, 10},
            nul(),
            new int[] {11, 12, 13},
            nul(),
            new int[] {14, 15, 16},
            nul(),
            new int[] {17, 18, 19}
        );
        */

        TestUtil.fx_(() -> f.set(dateField(new DateTimeInfo(DateTimeType.TIMEOFDAY), LocalTime.of( 13, 56, 22))));
        assertEquals("13:56:22", TestUtil.fx(() -> f.get().getText()));
        testPositions(new Random(0),
                new int[] {0, 1, 2},
                nul(),
                new int[] {3, 4, 5},
                nul(),
                new int[] {6, 7, 8}
        );
        
        TestUtil.fx_(() -> f.set(dateField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1))));
        // Delete the month:
        push(KeyCode.HOME);
        push(KeyCode.RIGHT);
        push(KeyCode.RIGHT);
        push(KeyCode.RIGHT);
        push(KeyCode.RIGHT);
        push(KeyCode.RIGHT);
        push(KeyCode.DELETE);
        push(KeyCode.DELETE);
        assertEquals("1900-Month-01", TestUtil.fx(() -> f.get().getText()));
        testPositions(new Random(0),
            new int[] {0, 1, 2, 3, 4},
            nul(),
            new int[] {5},
            nul(),
            new int[] {11, 12, 13}
        );

        TestUtil.fx_(() -> f.set(dateField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1))));
        // Delete all:
        pushSelectAll();
        push(KeyCode.DELETE);
        assertEquals("Year-Month-Day", TestUtil.fx(() -> f.get().getText()));
        testPositions(new Random(0),
            new int[] {0},
            nul(),
            new int[] {5},
            nul(),
            new int[] {11}
        );

        
        TestUtil.fx_(() -> f.set(dateField(new DateTimeInfo(DateTimeType.DATETIME), LocalDateTime.of(1900, 1, 1, 1, 1, 1))));
        // Delete all:
        pushSelectAll();
        push(KeyCode.DELETE);
        assertEquals("Year-Month-Day Hour:Minute:Second", TestUtil.fx(() -> f.get().getText()));
        testPositions(new Random(0),
            new int[] {0},
            nul(),
            new int[] {5},
            nul(),
            new int[] {11},
            nul(),
            new int[] {15},
            nul(),
            new int[] {20},
            nul(),
            new int[] {27}
        );
    }

    // Shorthand for null that is ignored by the checker:
    @SuppressWarnings("nullness")
    private int[] nul()
    {
        return null;
    }

    @OnThread(Tag.FXPlatform)
    private StructuredTextField dateField(DateTimeInfo dateTimeInfo, TemporalAccessor t)
    {
        return field(DataType.date(dateTimeInfo), t);
    }

    @OnThread(Tag.FXPlatform)
    private StructuredTextField field(DataType dataType, Object value)
    {
        Workers.onWorkerThread("", Priority.SAVE, () ->
        {
            try
            {
                List<TableId> ids = Utility.mapList(tableManager.getAllTables(), t -> t.getId());
                for (TableId id : ids)
                {
                    tableManager.remove(id);
                }
                @SuppressWarnings({"keyfor", "value", "units"})
                EditableRecordSet rs = new EditableRecordSet(Collections.singletonList(dataType.makeImmediateColumn(new ColumnId("C"), Collections.<Either<String, @Value Object>>singletonList(Either.right(value)), DataTypeUtility.makeDefaultValue(dataType))), () -> 1);
                ImmediateDataSource dataSource = new ImmediateDataSource(tableManager, new InitialLoadDetails(null, CellPosition.ORIGIN, null), rs);
                tableManager.record(dataSource);
            }
            catch (InternalException | UserException e)
            {
                Log.log(e);
            }
        });

        
        FXPlatformSupplier<@Nullable StructuredTextField> findSTF = () -> {
            windowToUse.getScene().getRoot().applyCss();
            @Nullable StructuredTextField structuredTextField = (@Nullable StructuredTextField) windowToUse.getScene().getRoot().lookup(".structured-text-field");
            if (structuredTextField != null)
            {
                structuredTextField.requestFocus();
                return structuredTextField;
            }
            return null;
        };
        // Need a moment for old field to get cleared:
        long now = System.currentTimeMillis();
        TestUtil.fxYieldUntil(() -> System.currentTimeMillis() > now + 800 && findSTF.get() != null);
        StructuredTextField stf = findSTF.get();
        if (stf != null)
            return stf;
        
        dumpScreenshot(windowToUse);
        throw new RuntimeException("Couldn't find STF, windows: " + getWindowList() + "\n");
    }

    @OnThread(Tag.Any)
    private GetDataPosition makeGetDataPosition()
    {
        return new GetDataPosition()
        {
            @Override
            public @OnThread(Tag.FXPlatform) CellPosition getDataPosition(@TableDataRowIndex int rowIndex, @TableDataColIndex int columnIndex)
            {
                return CellPosition.ORIGIN;
            }

            @SuppressWarnings("units")
            @Override
            public @OnThread(Tag.FXPlatform) @TableDataRowIndex int getFirstVisibleRowIncl()
            {
                return -1;
            }

            @SuppressWarnings("units")
            @Override
            public @OnThread(Tag.FXPlatform) @TableDataRowIndex int getLastVisibleRowIncl()
            {
                return -1;
            }
        };
    }

    /**
     * Keyboard shortcuts for STF:
     * - Left/Right move one character.  If you are next to a divider, skip to the other side.
     * - Ctrl/Alt-Left/Right moves one word.  If you're in a field with spaces, act like
     *   in a normal text control.  If you're in the first/last word of a slot, go to edge of slot.
     *   If at edge of slot, act like left/right and skip divider.
     * - Enter finishes editing
     * - Escape finishes editing (and pops up undo reminder?)
     * - Tab selects from an auto-complete list
     */
    private void testPositions(Random r, int[]... positions)
    {
        // Check home and end:
        push(KeyCode.HOME);
        assertEquals(positions[0][0], (int)TestUtil.fx(() -> f.get().getCaretPosition()));
        push(KeyCode.END);
        assertEquals(positions[positions.length - 1][positions[positions.length - 1].length - 1], (int)TestUtil.fx(() -> f.get().getCaretPosition()));
        push(KeyCode.HOME);
        assertEquals(positions[0][0], (int)TestUtil.fx(() -> f.get().getCaretPosition()));

        ArrayList<Integer> collapsed = new ArrayList<>();
        ArrayList<Double> collapsedX = new ArrayList<>();
        int major = 0;
        int minor = 0;
        while (major < positions.length)
        {
            if (positions[major] == null)
            {
                major += 1;
                minor = 0;
            }
            else
            {
                while (minor < positions[major].length)
                {
                    int newPos = positions[major][minor];
                    collapsed.add(newPos);
                    collapsedX.add(TestUtil.fx(() -> newPos == 0 ?
                        // No idea why I need these casts here:
                        ((Optional<Bounds>)f.get().getCharacterBoundsOnScreen(newPos, newPos + 1)).get().getMinX() :
                        ((Optional<Bounds>)f.get().getCharacterBoundsOnScreen(newPos - 1, newPos)).get().getMaxX()));

                    minor += 1;
                }
                major += 1;
                minor = 0;
            }
        }

        // Now go through one at a time using LEFT and RIGHT, backing up and advancing each stage:
        for (int i = 0; i < collapsed.size(); i++)
        {
            assertEquals(collapsed.get(i), TestUtil.fx(() -> f.get().getCaretPosition()));
            // Forward then back:
            if (i + 1 < collapsed.size())
            {
                push(KeyCode.RIGHT);
                push(KeyCode.LEFT);
            }
            assertEquals(collapsed.get(i), TestUtil.fx(() -> f.get().getCaretPosition()));
            // Back then forward:
            if (i > 0)
            {
                push(KeyCode.LEFT);
                push(KeyCode.RIGHT);
            }
            assertEquals(collapsed.get(i), TestUtil.fx(() -> f.get().getCaretPosition()));
            push(KeyCode.RIGHT);
        }
        assertEquals(collapsed.get(collapsed.size() - 1), TestUtil.fx(() -> f.get().getCaretPosition()));

        // Basic click tests:
        Bounds screenBounds = TestUtil.fx(() -> f.get().localToScreen(f.get().getBoundsInLocal()));
        Map<Double, Integer> xToPos = new HashMap<>();
        for (int i = 0; i < collapsed.size(); i++)
        {
            // Clicking on the exact divide should end up at the right character position:
            Point2D p = new Point2D(collapsedX.get(i), screenBounds.getMinY() + 4.0);
            if (!FXUtility.boundsToRect(screenBounds).contains(p))
                continue;
            clickOn(p);
            // Move so we don't treat as double click:
            TestUtil.sleep(300);
            moveBy(10, 0);
            assertEquals("Clicked: " + collapsedX.get(i) + ", " + (screenBounds.getMinY() + 4.0), collapsed.get(i), TestUtil.fx(() -> f.get().getCaretPosition()));
            if (i + 1 < collapsed.size())
            {
                // Clicking progressively between the two positions should end up in one, or the other:
                // (In particular, clicking on a prompt should not end up in the prompt, it should glue to one side or other)
                for (double x = collapsedX.get(i); x <= collapsedX.get(i + 1); x += 2.0)
                {
                    clickOn(x, screenBounds.getMinY() + 4.0);
                    // Move so we don't treat as double click:
                    moveBy(10, 0);
                    TestUtil.sleep(300);
                    int outcome = TestUtil.fx(() -> f.get().getCaretPosition());
                    assertThat("Aiming for " + i + " by clicking at offset " + (x - screenBounds.getMinX()), outcome, Matchers.isIn(Arrays.asList(collapsed.get(i), collapsed.get(i + 1))));
                    xToPos.put(x, outcome);
                }
            }
        }

        // Try drag to select some randomly selected pairs of positions:
        List<Entry<Double, Integer>> allPos = new ArrayList<>(xToPos.entrySet());
        for (int i = 0; i < 40; i++)
        {
            Entry<Double, Integer> a = allPos.get(r.nextInt(allPos.size()));
            Entry<Double, Integer> b = allPos.get(r.nextInt(allPos.size()));

            drag(a.getKey(), screenBounds.getMinY() + 4.0, MouseButton.PRIMARY);
            dropTo(b.getKey(), screenBounds.getMinY() + 4.0);
            String label = "#" + i + " from " + a.getKey() + "->" + a.getValue() + " to " + b.getKey() + "->" + b.getValue();
            assertEquals(label, a.getValue(), TestUtil.fx(() -> f.get().getAnchor()));
            assertEquals(label, b.getValue(), TestUtil.fx(() -> f.get().getCaretPosition()));
            // Move so we don't treat as drag move or double click:
            push(KeyCode.RIGHT);
            TestUtil.sleep(500);
        }

        // TODO test with shift-left/right
        // TODO test double and triple clicking
        for (int i = 0; i < 40; i++)
        {
            Entry<Double, Integer> start = allPos.get(r.nextInt(allPos.size()));
            // Home will deselect:
            push(KeyCode.HOME);
            // Avoid a double click:
            moveTo(start.getKey() + 10, screenBounds.getMinY() + 4.0);
            TestUtil.sleep(300);
            // Use click to initially position:
            clickOn(start.getKey(), screenBounds.getMinY() + 4.0);
            assertEquals(start.getValue(), TestUtil.fx(() -> f.get().getCaretPosition()));
            assertEquals(start.getValue(), TestUtil.fx(() -> f.get().getAnchor()));
            // Then press ctrl-left or ctrl-right some number of times:
            // For now, skip this, as it seems to trip TestFX bugs:
            if (true)
                continue;

            // For reasons I don't understand (TestFX/Robot bug?), SHIFT+CTRL registers as CTRL on Windows,
            // so don't do selection test on Windows:
            boolean useSelection = SystemUtils.IS_OS_WINDOWS ? false : r.nextBoolean();

            int number = r.nextInt(positions.length);
            boolean right = r.nextBoolean();

            int pos = start.getValue();
            for (int repetition = 0; repetition < number; repetition++)
            {
                List<Modifier> mods = new ArrayList<>();
                // TODO should be Alt on Mac, Control on Windows:
                // (need to fix RichTextFX):
                mods.add(SystemUtils.IS_OS_MAC_OSX ? KeyCombination.META_DOWN : KeyCombination.CONTROL_DOWN);
                if (useSelection)
                    mods.add(KeyCombination.SHIFT_DOWN);
                push(new KeyCodeCombination(right ? KeyCode.RIGHT : KeyCode.LEFT, mods.toArray(new KeyCombination.Modifier[0])));
                pos = calculateExpectedWordMove(positions, pos, right);
            }

            String label = "From " + start.getValue() + " sel:" + useSelection + " right:" + right + " x " + number + " content " + TestUtil.fx(() -> f.get().getText());
            // Work out where we expect it to end up:
            assertEquals(label, useSelection ? (int)start.getValue() : pos, (int)TestUtil.fx(() -> f.get().getAnchor()));
            assertEquals(label, pos, (int)TestUtil.fx(() -> f.get().getCaretPosition()));
        }
    }

    private int calculateExpectedWordMove(int[][] positions, int startCaretPos, boolean right)
    {
        int major;
        int minor = 0;
        boolean found = false;
        outer: for (major = 0; major < positions.length; major++)
        {
            if (positions[major] == null)
                continue outer;

            for (minor = 0; minor < positions[major].length; minor++)
            {
                if (positions[major][minor] == startCaretPos)
                {
                    found = true;
                    break outer;
                }
            }
        }
        assertTrue(found);
        if (right)
        {
            if (minor == positions[major].length - 1)
            {
                int fallback = positions[major][minor];
                major += 1;
                // Need to find next valid:
                while (major < positions.length)
                {
                    if (positions[major] != null)
                        return positions[major][0];
                    major += 1;
                }
                return fallback;
            }
            else
            {
                return positions[major][positions[major].length - 1];
            }
        }
        else
        {
            if (minor == 0)
            {
                int fallback = positions[major][minor];
                major -= 1;
                // Need to find earlier valid:
                while (major >= 0)
                {
                    if (positions[major] != null)
                        return positions[major][positions[major].length - 1];
                    major -= 1;
                }
                return fallback;
            }
            else
            {
                return positions[major][0];
            }
        }
    }

    @Test
    public void testYMD() throws InternalException
    {
        TestUtil.fx_(() -> f.set(dateField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1))));
        TestUtil.fx_(() -> f.get().selectAll());
        type("1973", "1973$-Month-Day");
        type("-3-", "1973-3-$Day");
        type("17", "1973-03-17", LocalDate.of(1973, 3, 17));

        TestUtil.fx_(() -> f.set(dateField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1))));
        push(KeyCode.HOME);
        push(KeyCode.DELETE);
        type("", "$1/04/1900");
        type("2", "21/04/1900", LocalDate.of(1900, 4, 21));

        TestUtil.fx_(() -> f.set(dateField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1))));
        TestUtil.fx_(() -> f.get().selectAll());
        type("31 10 86","31/10/1986", LocalDate.of(1986, 10, 31));

        TestUtil.fx_(() -> f.set(dateField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1))));
        TestUtil.fx_(() -> f.get().selectAll());
        type("5 6 27","05/06/2027", LocalDate.of(2027, 6, 5));

        TestUtil.fx_(() -> f.set(dateField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1))));
        TestUtil.fx_(() -> f.get().selectAll());
        type("6", "6$/Month/Year");
        type("-", "6/$Month/Year");
        type("7", "6/7$/Year");
        type("-", "6/7/$Year");
        type("3", "06/07/2003", LocalDate.of(2003, 7, 6));

        // Check prompts for invalid dates:
        TestUtil.fx_(() -> f.set(dateField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1))));
        pushSelectAll();
        type("", "^01/04/1900$");
        push(KeyCode.DELETE);
        type("", "$Day/Month/Year");
        TestUtil.fx_(()-> dummy.requestFocus());
        type("", "Day/Month/$Year");
        assertNotNull(lookup(".invalid-data-input-popup").query());
        assertNotNull(lookup(".invalid-data-input-popup .invalid-data-revert").query());
        // Click on the revert fix:
        clickOn(".invalid-data-input-popup .invalid-data-revert");
        WaitForAsyncUtils.waitForFxEvents();
        assertNull(lookup(".invalid-data-input-popup").query());
        type("", "01/04/1900$");

        TestUtil.fx_(() -> f.set(dateField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1))));
        pushSelectAll();
        type("", "^01/04/1900$");
        push(KeyCode.DELETE);
        type("", "$Day/Month/Year");
        clickOn(dummy);
        type("", "Day/Month/$Year");
        assertNotNull(lookup(".invalid-data-input-popup").query());
        // Now edit again:
        targetF();
        // Popup should still show for now:
        assertNotNull(lookup(".invalid-data-input-popup").query());
        // Now we type in something:
        push(KeyCode.HOME);
        type("8", "8$/Month/Year");
        // Popup should disappear:
        assertNull(lookup(".invalid-data-input-popup").query());
        // Click off:
        clickOn(dummy);
        // Popup should be back:
        assertNotNull(lookup(".invalid-data-input-popup").query());
        // Should show the first value as a fix:
        Label label = lookup(".invalid-data-input-popup .invalid-data-revert").<Label>query();
        assertThat(TestUtil.fx(() -> label.getText()), Matchers.containsString("01/04/1900"));
        // Click in again:
        targetF();
        push(KeyCode.HOME);
        push(KeyCode.RIGHT);
        type("/7/2169", "8/7/2169$");
        clickOn(dummy);
        // Should be no popup:
        assertNull(lookup(".invalid-data-input-popup").query());
        // Now delete again:
        targetF();
        pushSelectAll();
        // Will have gained zeroes because it lost focus:
        type("", "^08/07/2169$");
        push(KeyCode.DELETE);
        clickOn(dummy);
        // New popup should show most recent value:
        assertNotNull(lookup(".invalid-data-input-popup").query());
        Label label2 = lookup(".invalid-data-input-popup .invalid-data-revert").<Label>query();
        assertThat(TestUtil.fx(() -> label2.getText()), Matchers.containsString("08/07/2169"));

        // Check swapped or invalid dates have the right suggestions:
        checkFix("0/3/2000", "01/03/2000");
        checkFix("8/0/2000", "08/01/2000");
        checkFix("4/13/2000", "13/04/2000");
        checkFix("13/13/2000", "13/12/2000");
        checkFix("31/9/2000", "30/09/2000");
        checkFix("31/9/2000", "01/10/2000");
        checkFix("32/9/2000", "30/09/2000");
        checkFix("1968/3/4", "04/03/1968");
        checkFix("32/9/1", "01/09/2032");
        checkFix("32/9/1", "30/09/2001");
        checkFix("68/3/4", "04/03/1968");

        targetF();
        pushSelectAll();
        type("10/12/0378", "10/12/0378", LocalDate.of(378, 12, 10));
        targetF();
        pushSelectAll();
        type("01/02/3", "01/02/2003", LocalDate.of(2003, 2, 1));
        targetF();
        pushSelectAll();
        type("10/12/03", "10/12/2003", LocalDate.of(2003, 12, 10));
        targetF();
        pushSelectAll();
        type("10/12/0003", "10/12/0003", LocalDate.of(3, 12, 10));
    }

    private void pushSelectAll()
    {
        // Some sort of bug on OS X prevents Cmd-A working in TestFX:
        if (SystemUtils.IS_OS_MAC_OSX)
            TestUtil.fx_(() -> f.get().selectAll());
        else
            push(TestUtil.ctrlCmd(), KeyCode.A);
    }

    public void targetF()
    {
        doubleClickOn(TestUtil.fx(() -> f.get()));
    }

    @Property(trials = 15)
    public void propYMD(@From(GenDate.class) LocalDate localDate, @From(GenRandom.class) Random r) throws InternalException
    {
        TestUtil.fx_(() -> f.set(dateField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1))));
        enterDate(localDate, r, "");
    }

    @Property(trials = 20)
    public void propString(@From(UnicodeStringGenerator.class) String s) throws InternalException
    {
        int[] cs = s.codePoints().filter(c -> c <= 0x7dff && !Character.isISOControl(c) && Character.isDefined(c) && c != '\"').toArray();
        s = new String(cs, 0, cs.length);

        TestUtil.fx_(() -> f.set(field(DataType.TEXT, "initial value")));
        targetF();
        pushSelectAll();
        push(KeyCode.DELETE);
        // Don't depend on HOME behaviour as it's currently undecided:
        TestUtil.fx_(() -> f.get().moveTo(1));
        type(s, "\"" + s + "\"", s);
    }

    @Property(trials = 20)
    public void propNumberPair(@From(GenNumberAsString.class) String numAsStringA, @From(GenNumberAsString.class) String numAsStringB, @From(GenNumber.class) Number initial, boolean space) throws InternalException
    {
        BigDecimal numA = new BigDecimal(numAsStringA, MathContext.DECIMAL128);
        BigDecimal numB = new BigDecimal(numAsStringB, MathContext.DECIMAL128);
        DataType numType = DataType.number(new NumberInfo(Unit.SCALAR));
        TestUtil.fx_(() -> f.set(field(DataType.tuple(numType, numType), new Object[] {initial, initial})));
        targetF();
        pushSelectAll();
        type("", "^(" + initial.toString() + "," + initial.toString() + ")$");
        type("(" + numAsStringA + "," + (space ? " " : "") + numAsStringB, "(" + numAsStringA + "," + numAsStringB + ")", new Object[]{numA, numB});
        targetF();
        push(KeyCode.END);
        type("", "(" + numAsStringA + "," + numAsStringB + ")^$");
        push(KeyCode.LEFT);
        type("", "(" + numAsStringA + "," + numAsStringB + "^$)");
        push(KeyCode.HOME);
        type("", "^$(" + numAsStringA + "," + numAsStringB + ")");
        push(KeyCode.RIGHT);
        type("", "(^$" + numAsStringA + "," + numAsStringB + ")");
    }

    @Property(trials = 20)
    public void propNumberBoolPair(@From(GenNumberAsString.class) String numAsString, boolean boolValue, @From(GenRandom.class) Random r) throws InternalException
    {
        BigDecimal num = new BigDecimal(numAsString, MathContext.DECIMAL128);
        DataType numType = DataType.number(new NumberInfo(Unit.SCALAR));
        boolean numFirst = r.nextBoolean();
        DataType tupleType = numFirst ? DataType.tuple(numType, DataType.BOOLEAN) : DataType.tuple(DataType.BOOLEAN, numType);
        TestUtil.fx_(() -> f.set(field(tupleType, numFirst ? new Object[] {0, !boolValue} : new Object[] {!boolValue, 0})));
        targetF();
        pushSelectAll();
        if (numFirst)
        {
            type("", "^(0," + !boolValue + ")$");
            type("(" + numAsString, "(" + numAsString + "$,)");
            type(",", "(" + numAsString + ",$)");
            if (r.nextBoolean())
            {
                // Type full:
                type(Boolean.toString(boolValue), "(" + numAsString + "," + boolValue + ")", new Object[]{num, boolValue});
            }
            else
            {
                // Use completion:
                type(Boolean.toString(boolValue).substring(0, 1), "(" + numAsString + "," + Boolean.toString(boolValue).substring(0, 1) + "$)");
                Node n = assertAutoCompleteVisible(1 + numAsString.length() + 1, null);
                type(Boolean.toString(boolValue).substring(1, 2), "(" + numAsString + "," + Boolean.toString(boolValue).substring(0, 2) + "$)");
                assertAutoCompleteVisible(1 + numAsString.length() + 2, n);
                push(KeyCode.TAB);
                type("", "(" + numAsString + "," + boolValue + ")", new Object[]{num, boolValue});
            }
        }
        else
        {
            type("", "^(" + !boolValue + ",0)$");
            if (r.nextBoolean())
            {
                // Type full:
                type(Boolean.toString(boolValue), "(" + boolValue + "$,)");
                if (r.nextBoolean())
                    push(KeyCode.RIGHT);
                else
                    write(",");
            }
            else
            {
                // Use completion:
                type(Boolean.toString(boolValue).substring(0 ,1), "(" + Boolean.toString(boolValue).substring(0, 1) + "$,)");
                assertAutoCompleteVisible(1, null);
                push(KeyCode.TAB);
                type("", "(" + boolValue + ",$)");
            }
            type("", "(" + boolValue + ",$)");
            type(numAsString, "(" + boolValue + "," + numAsString + ")", new Object[]{boolValue, num});
        }
    }

    @SuppressWarnings("deprecation") // Because of assumeThat
    @Property(trials = 20)
    public void propNumber(@From(GenNumberAsString.class) String numAsString, @From(GenNumber.class) Number initial, char c) throws InternalException
    {
        // c should be a non-numeric char:
        assumeThat(c, Matchers.<Character>not(Matchers.isIn(ArrayUtils.toObject("-.0123456789".toCharArray()))));
        assumeThat(c, Matchers.<Character>greaterThan(' '));

        BigDecimal num = new BigDecimal(numAsString, MathContext.DECIMAL128);
        TestUtil.fx_(() -> f.set(field(DataType.number(new NumberInfo(Unit.SCALAR)), initial)));
        targetF();
        pushSelectAll();
        type(numAsString, numAsString, num);

        // Random character in middle should be ignored:
        moveBy(10, 0);
        targetF();
        push(KeyCode.HOME);
        type("", "$" + numAsString);
        int mid = numAsString.length() / 2;
        for (int i = 0; i < mid; i++)
            push(KeyCode.RIGHT);
        write(c);
        push(KeyCode.HOME);
        type("", "$" + numAsString);
        type("", numAsString, num);

        fail("TODO Test number entry more extensively, especially entering and deleting the dot");
    }

    @Property(trials = 15)
    public void propDateTime(@From(GenDateTime.class) LocalDateTime localDateTime, @From(GenRandom.class) Random r) throws InternalException
    {
        TestUtil.fx_(() -> f.set(dateField(new DateTimeInfo(DateTimeType.DATETIME), LocalDateTime.of(1900, 4, 1, 1, 1, 1))));
        String timeVal = timeString(localDateTime, true);
        enterDate(localDateTime, r, " " + timeVal);
    }

    private static String timeString(TemporalAccessor t, boolean padded)
    {
        int hour = t.get(ChronoField.HOUR_OF_DAY);
        int minute = t.get(ChronoField.MINUTE_OF_HOUR);
        int second = t.get(ChronoField.SECOND_OF_MINUTE);
        String timeVal;
        if (padded)
            timeVal = String.format("%02d:%02d:%02d", hour, minute, second);
        else
            timeVal = hour + ":" + minute + ":" + second;
        if (t.get(ChronoField.NANO_OF_SECOND) != 0)
        {
            timeVal += new BigDecimal("0." + String.format("%09d", t.get(ChronoField.NANO_OF_SECOND))).stripTrailingZeros().toPlainString().substring(1);
        }
        return timeVal;
    }

    @Property(trials = 15)
    public void propYM(@From(GenYearMonth.class) YearMonth yearMonth, @From(GenRandom.class) Random r) throws InternalException
    {
        TestUtil.fx_(() -> f.set(dateField(new DateTimeInfo(DateTimeType.YEARMONTH), YearMonth.of(1900, 1))));
        String timeVal = yearMonth.getYear() + "-" + yearMonth.getMonthValue();
        String timeVal42 = String.format("%04d", yearMonth.getYear()) + "-" + String.format("%02d", yearMonth.getMonthValue());
        targetF();
        pushSelectAll();
        type(timeVal, timeVal42, yearMonth);
        // TODO also test errors, and other variants (e.g. text months)
    }

    @Property(trials = 15)
    public void propDateTimeZoned(@From(GenDateTimeZoned.class) @Value ZonedDateTime zonedDateTime) throws InternalException, UserException
    {
        TestUtil.fx_(() -> f.set(dateField(new DateTimeInfo(DateTimeType.DATETIMEZONED), ZonedDateTime.from(DateTimeInfo.DEFAULT_VALUE))));
        String timeVal = TestUtil.sim(new SimulationSupplier<String>()
        {
            @Override
            @OnThread(Tag.Simulation)
            public String get() throws InternalException, UserException
            {
                return DataTypeUtility.valueToString(DataType.date(new DateTimeInfo(DateTimeType.DATETIMEZONED)), zonedDateTime, null);
            }
        });
        targetF();
        pushSelectAll();
        /*
        StringBuilder expected = new StringBuilder();
        expected.append(zonedDateTime.getDayOfMonth()).append("/").append(zonedDateTime.getMonthValue()).append("/").append(zonedDateTime.getYear());
        expected.append(" ").append(zonedDateTime.getHour()).append(":").append(zonedDateTime.getMinute()).append(":").append(zonedDateTime.getSecond());
        if (zonedDateTime.getNano() != 0)
            expected.append(".").append(zonedDateTime.getNano());
        expected.append(" ").append(zonedDateTime.getZone().getId());
        */
        type(timeVal, timeVal, zonedDateTime);
        // TODO also test errors, and other variants (e.g. text months)
    }

    @Property(trials = 15)
    public void propTime(@From(GenTime.class) LocalTime localTime, @From(GenRandom.class) Random r) throws InternalException
    {
        TestUtil.fx_(() -> f.set(dateField(new DateTimeInfo(DateTimeType.TIMEOFDAY), LocalTime.of(1, 1, 1, 1))));
        targetF();
        pushSelectAll();
        type(timeString(localTime, false), timeString(localTime, true), localTime);
        // TODO also test errors
    }

    /*
    @Property(trials = 15)
    public void propTimeZoned(@From(GenOffsetTime.class) OffsetTime timeZoned, @From(GenRandom.class) Random r) throws InternalException
    {
        f.set(dateField(new DateTimeInfo(DateTimeType.TIMEOFDAYZONED), OffsetTime.of(1, 1, 1, 1, ZoneOffset.ofHours(3))));
        String timeVal = timeString(timeZoned);
        int offsetHour = timeZoned.getOffset().getTotalSeconds() / 3600;
        int offsetMinute = Math.abs(timeZoned.getOffset().getTotalSeconds() / 60 - offsetHour * 60);
        timeVal += (timeZoned.getOffset().getTotalSeconds() < 0 ? "-" : "+") + Math.abs(offsetHour) + ":" + offsetMinute;
        targetF();
        pushSelectAll();
        type(timeVal, timeVal, timeZoned);
        // TODO also test errors
    }
    */

    // There's only two possible values, so no need to make it a property!
    @Test
    public void testBool() throws InternalException
    {
        TestUtil.fx_(() -> f.set(field(DataType.BOOLEAN, false)));
        targetF();
        push(KeyCode.HOME);
        // Should immediately show the popup:
        assertAutoCompleteVisible(0, null);
        // Tried using the :filled pseudo-class here but that didn't seem to work:
        Set<STFAutoCompleteCell> items = lookup(".stf-autocomplete .stf-autocomplete-item").lookup((STFAutoCompleteCell c) -> TestUtil.fx(() -> !c.isEmpty())).queryAll();
        assertEquals(2, items.size());
        @SuppressWarnings("nullness")
        List<String> completions = items.stream().map(c -> TestUtil.fx(() -> c.getItem()).suggestion).sorted().collect(Collectors.toList());
        assertEquals(Arrays.asList("false", "true"), completions);
        assertEquals(Arrays.asList("false", "true"), items.stream().map(c -> TestUtil.fx(() -> c.getText())).sorted().collect(Collectors.toList()));

        pushSelectAll();
        // Deliberate capital A, should still work:
        type("fAlse", "false", false);
        targetF();
        pushSelectAll();
        type("True", "true", true);
        targetF();
        pushSelectAll();
        push(KeyCode.DELETE);
        type("", "$");
        @SuppressWarnings("nullness")
        STFAutoCompleteCell autoSuggestTrue = lookup(".stf-autocomplete .stf-autocomplete-item").<STFAutoCompleteCell>lookup((Predicate<STFAutoCompleteCell>) ((STFAutoCompleteCell c) -> TestUtil.fx(() -> !c.isEmpty() && "true".equals(c.getItem().suggestion)))).<STFAutoCompleteCell>query();
        assertNotNull(autoSuggestTrue);
        clickOn(autoSuggestTrue);
        type("", "true", true);
        assertNull(lookup(".stf-autocomplete").tryQuery().orElse(null));

        // Tab should auto-complete plausible option:
        targetF();
        pushSelectAll();
        type("F", "F$");
        push(KeyCode.TAB);
        type("", "false", false);
    }

    @SuppressWarnings("deprecation") // Because of assumeThat
    @Property(trials=20)
    public void propTagged(@From(GenTaggedTypeAndValueGen.class) GenTypeAndValueGen.TypeAndValueGen taggedTypeAndValueGen) throws UserException, InternalException
    {
        assumeThat(taggedTypeAndValueGen.getType().getTagTypes().size(), Matchers.greaterThan(0));
        @Value Object initialVal = taggedTypeAndValueGen.makeValue();
        TestUtil.fx_(() -> f.set(field(taggedTypeAndValueGen.getType(), initialVal)));
        type("", TestUtil.sim(new SimulationSupplier<String>()
        {
            @Override
            @OnThread(Tag.Simulation)
            public String get() throws InternalException, UserException
            {
                return DataTypeUtility.valueToString(taggedTypeAndValueGen.getType(), initialVal, null);
            }
        }), initialVal);
        targetF();
        pushSelectAll();
        TaggedValue value = (TaggedValue)taggedTypeAndValueGen.makeValue();
        TagType<DataType> tag = taggedTypeAndValueGen.getType().getTagTypes().get(value.getTagIndex());
        String tagName = tag.getName();
        type(tagName.substring(0, tagName.length() - 1), tagName.substring(0, tagName.length() - 1) + "$");
        if (value.getInner() == null)
        {
            type(tagName.substring(tagName.length() - 1), tagName, value);
        }
        else
        {
            // Get text for inner item, enter that, check value.
            String inner = TestUtil.sim(new SimulationSupplier<String>()
            {
                @Override
                @OnThread(Tag.Simulation)
                @SuppressWarnings("nullness")
                public String get() throws InternalException, UserException
                {
                    return DataTypeUtility.valueToString(tag.getInner(), value.getInner(), taggedTypeAndValueGen.getType());
                }
            });
            type(tagName.substring(tagName.length() - 1) + "(" + inner + ")", tagName + "(" + inner + ")", value);
        }
    }

    // This doesn't check that all the positions make sense, it just checks some basic properties:
    // HOME should go to zero
    // LEFT and RIGHT should be reversible.
    @Property(trials = 15)
    public void propPositions(@When(seed=-8325242676710787694L) @From(GenTypeAndValueGen.class) TypeAndValueGen typeAndValueGen) throws UserException, InternalException
    {
        @Value Object origValue = typeAndValueGen.makeValue();
        TestUtil.fx_(() -> f.set(field(typeAndValueGen.getType(), origValue)));
        checkPositions(typeAndValueGen.getType() + ":" + TestUtil.fx(() -> f.get().getText()));
        targetF();
        pushSelectAll();
        push(KeyCode.DELETE);
        checkPositions(typeAndValueGen.getType() + ":" + TestUtil.fx(() -> f.get().getText()));
        @Value Object value = typeAndValueGen.makeValue();
        String str = TestUtil.sim(new SimulationSupplier<String>()
        {
            @Override
            @OnThread(Tag.Simulation)
            public String get() throws InternalException, UserException
            {
                return DataTypeUtility.valueToString(typeAndValueGen.getType(), value, null);
            }
        });
        type(str, str, value);
        targetF();
        checkPositions(typeAndValueGen.getType() + ":" + str);
    }

    private void checkPositions(String msg)
    {
        push(KeyCode.HOME);
        assertEquals(msg, 0, getCaretPosition());
        push(KeyCode.END);
        // May not be actual end of the slot if last item is empty and showing a prompt:
        int length = getCaretPosition();
        for (boolean byWord : new boolean[] {false, true})
        {
            push(KeyCode.HOME);
            int pos = 0;
            ArrayList<Integer> positions = new ArrayList<>();
            int attempts = 0; // counter just to prevent test running forever if it fails.  Max pushes needed should be length of field
            while (pos < length && attempts++ <= length)
            {
                positions.add(pos);
                if (byWord) push(SystemUtils.IS_OS_MAC_OSX ? KeyCode.META : KeyCode.CONTROL, KeyCode.RIGHT); else push(KeyCode.RIGHT);
                pos = getCaretPosition();
            }
            // Check we did actually reach the end, not just time-out:
            assertThat(msg, attempts, Matchers.<Integer>lessThanOrEqualTo(length));
            // Now check that it reverses.  Note that array is deliberately missing the last position:
            int index = positions.size() - 1;
            int prevPosition = getCaretPosition();
            while (index >= 0)
            {
                if (byWord) push(SystemUtils.IS_OS_MAC_OSX ? KeyCode.META : KeyCode.CONTROL, KeyCode.LEFT); else push(KeyCode.LEFT);
                assertEquals(msg + (byWord ? "by word " : "") + " left from " + prevPosition, (int) positions.get(index), getCaretPosition());
                prevPosition = getCaretPosition();
                index -= 1;
            }
        }
    }

    private int getCaretPosition()
    {
        return TestUtil.fx(() -> f.get().getCaretPosition());
    }

    @Test
    public void testList() throws InternalException
    {
        DataType dataType = DataType.array(DataType.NUMBER);
        TestUtil.fx_(() -> f.set(field(dataType, new ListExList(Collections.emptyList()))));
        push(KeyCode.RIGHT);
        push(KeyCode.RIGHT);
        type("", "[]$");
        push(KeyCode.LEFT);
        // No item until we start typing:
        type("", "[$]");
        type("0.1", "[0.1$]");
        type(",", "[0.1,$]");
        push(KeyCode.BACK_SPACE);
        type("", "[0.1$]");
        push(KeyCode.BACK_SPACE);
        type("", "[0.$]");
        push(KeyCode.BACK_SPACE);
        type("", "[0$]");
        push(KeyCode.BACK_SPACE);
        type("", "[$]");
        // Blank item should be removed once cursor leaves it:
        push(KeyCode.LEFT);
        type("", "$[]");

        TestUtil.fx_(() -> f.set(field(DataType.array(DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAY))), new ListExList(Collections.emptyList()))));
        push(KeyCode.LEFT);
        push(KeyCode.LEFT);
        type("", "$[]");
        push (KeyCode.RIGHT);
        type("", "[$]");
        type("5", "[5$:Minute:Second]");
        push(KeyCode.LEFT);
        push(KeyCode.LEFT);
        // Shouldn't remove the item as it has data:
        type("", "$[5:Minute:Second]");
        pushShiftRight();
        pushShiftRight();
        type("", "^[5$:Minute:Second]");
        push(KeyCode.DELETE);
        // If the content is gone, the item should go too:
        type("", "$[]");
        push(KeyCode.RIGHT);
        type("6", "[6$:Minute:Second]");
        push(KeyCode.BACK_SPACE);
        type("", "[$Hour:Minute:Second]");
        push(KeyCode.RIGHT);
        type("", "[Hour:$Minute:Second]");
        push(KeyCode.RIGHT);
        type("", "[Hour:Minute:$Second]");
        push(KeyCode.RIGHT);
        type("", "[]$");
        push(KeyCode.LEFT);
        type("12:34:56", "[12:34:56$]");
        for (int count = 0; count < 8; count++)
            pushShiftLeft();
        type("", "[$12:34:56^]");
        push(KeyCode.DELETE);
        type("", "[$Hour:Minute:Second]");

        TestUtil.fx_(() -> f.set(field(DataType.array(DataType.array(DataType.date(new DateTimeInfo(DateTimeType.TIMEOFDAY)))), new ListExList(Collections.singletonList(new ListExList(Collections.emptyList()))))));
        for (int count = 0; count < 4;count++)
            push(KeyCode.LEFT);
        type("", "$[[]]");
        push(KeyCode.DELETE);
        type("", "$[[]]");
        push(KeyCode.RIGHT);
        pushShiftRight();
        pushShiftRight();
        push(KeyCode.DELETE);
        type("", "[$]");

        // TODO try more with nested lists
        // TODO Test that deleting comma deletes the adjacent empty slot
        // TODO test that deleting whole list item actually deletes the item, without leaving the comma
    }

    private void pushShiftLeft()
    {
        // Some sort of bug on Windows doesn't let shift work:
        if (SystemUtils.IS_OS_WINDOWS)
            TestUtil.fx_(() -> f.get().previousChar(SelectionPolicy.EXTEND));
        else
            push(KeyCode.SHIFT, KeyCode.LEFT);
    }

    private void pushShiftRight()
    {
        // Some sort of bug on Windows doesn't let shift work:
        if (SystemUtils.IS_OS_WINDOWS)
            TestUtil.fx_(() -> f.get().nextChar(SelectionPolicy.EXTEND));
        else
            push(KeyCode.SHIFT, KeyCode.RIGHT);
    }

    public Node assertAutoCompleteVisible(int atChar, @Nullable Node sameAs)
    {
        Node autoComplete = lookup(".stf-autocomplete").query();
        assertNotNull(autoComplete);
        if (sameAs != null)
            assertSame(sameAs, autoComplete);
        // In the right place:
        Bounds charBounds = TestUtil.fx(() -> f.get().getCharacterBoundsOnScreen(atChar, atChar + 1).get());
        assertThat(TestUtil.fx(() -> autoComplete.localToScreen(autoComplete.getBoundsInLocal()).getMinX()), Matchers.<Double>is(Matchers.<Double>both(Matchers.<Double>greaterThan(charBounds.getMinX() - 8)).<Double>and(Matchers.<Double>lessThan(charBounds.getMaxX() + 2))));
        assertThat(TestUtil.fx(() -> autoComplete.localToScreen(autoComplete.getBoundsInLocal()).getMinY()), Matchers.<Double>is(Matchers.<Double>both(Matchers.<Double>greaterThan(charBounds.getMaxY() - 2)).<Double>and(Matchers.<Double>lessThan(charBounds.getMaxY() + 5))));
        return autoComplete;
    }

    private void enterDate(TemporalAccessor localDate, Random r, String extra) throws InternalException
    {
        // Try it in valid form with four digit year:
        targetF();
        pushSelectAll();
        String oneDigEntry = String.format("%04d", localDate.get(ChronoField.YEAR)) + "-" + localDate.get(ChronoField.MONTH_OF_YEAR) + "-" + localDate.get(ChronoField.DAY_OF_MONTH);
        String value = String.format("%04d", localDate.get(ChronoField.YEAR)) + "-" + String.format("%02d", localDate.get(ChronoField.MONTH_OF_YEAR)) + "-" + String.format("%02d", localDate.get(ChronoField.DAY_OF_MONTH));
        type(oneDigEntry + extra, value + extra, localDate);
        if (localDate.get(ChronoField.YEAR) > Year.now().getValue() - 80 && localDate.get(ChronoField.YEAR) < Year.now().getValue() + 20)
        {
            // Do two digits:
            targetF();
            pushSelectAll();
            String twoDigYear = Integer.toString(localDate.get(ChronoField.YEAR)).substring(2) + "-" + localDate.get(ChronoField.MONTH_OF_YEAR) + "-" + localDate.get(ChronoField.DAY_OF_MONTH);
            type(twoDigYear + extra, value + extra, localDate);
        }
        /*
        // Try slight variant, such as other dividers or leading zeros:
        // Also try month names:
        int[] vals = new int[] {localDate.get(ChronoField.DAY_OF_MONTH), localDate.get(ChronoField.MONTH_OF_YEAR), localDate.get(ChronoField.YEAR)};
        List<List<String>> monthNames = Arrays.asList(
            Arrays.asList("Ja", "Jan", "January"),
            Arrays.asList("F", "Feb", "February"),
            Arrays.asList("Mar", "March"),
            Arrays.asList("Ap", "Apr", "April"),
            Arrays.asList("May"),
            Arrays.asList("Jun", "June"),
            Arrays.asList("Jul", "July"),
            Arrays.asList("Au", "Aug", "August"),
            Arrays.asList("S", "Sep", "September"),
            Arrays.asList("O", "Oct", "October"),
            Arrays.asList("N", "Nov", "November"),
            Arrays.asList("D", "Dec", "December")
        );
        List<String> divs = Arrays.asList("/","-"," ",".");
        for (int attempt = 0; attempt < 3; attempt++)
        {
            String variant = "";
            for (int i = 0; i < 3; i++)
            {
                if (i == 1 && r.nextBoolean())
                {
                    List<String> monthPoss = monthNames.get(vals[i] - 1);
                    variant += monthPoss.get(r.nextInt(monthPoss.size()));
                }
                else
                {
                    variant += String.join("", Utility.replicate(r.nextInt(3), "0"));
                    variant += vals[i];
                }
                if (i < 2)
                {
                    variant += divs.get(r.nextInt(divs.size()));
                }
            }
            TestUtil.fx_(() -> f.get().requestFocus());
            pushSelectAll();
            type(variant + extra, value + extra, localDate);

            // Also try Month name, day, year:
            List<String> monthPoss = monthNames.get(vals[1] - 1);
            String variantMD = monthPoss.get(r.nextInt(monthPoss.size())) + "/" + vals[0] + "/" + vals[2];
            TestUtil.fx_(() -> f.get().requestFocus());
            pushSelectAll();
            type(variantMD + extra, value + extra, localDate);
        }
        */
        // Try errors and fixes; transposition, etc:
        /*
        if (vals[0] > 12)
        {
            // Swap day and month:
            String wrongVal = vals[1] + "/" + vals[0] + "/" + vals[2];
            checkFix(wrongVal + extra, value + extra);
        }
        if (vals[2] % 100 >= 32)
        {
            String wrongVal = vals[2] + "/" + vals[1] + "/" + vals[0];
            checkFix(wrongVal + extra, value + extra);
        }
        */
    }

    private void checkFix(String input, String suggestedFix)
    {
        targetF();
        pushSelectAll();
        type(input, input + "$");
        clickOn(dummy);
        assertNotNull(lookup(".invalid-data-input-popup").query());
        Node fix = lookup(".invalid-data-input-popup .invalid-data-fix").lookup((Label l) -> TestUtil.fx(() -> l.getText()).contains(suggestedFix)).query();
        assertNotNull(lookup(".invalid-data-input-popup .invalid-data-fix").<Label>queryAll().stream().map(l -> TestUtil.fx(() -> l.getText())).collect(Collectors.joining(" | ")),fix);
        clickOn(fix);
        type("", suggestedFix + "$");
    }

    private void type(String entry, String expected)
    {
        type(entry, expected, null);
    }


    private void type(String entry, String expected, @Nullable Object endEditAndCompareTo)
    {
        write(entry);
        if (endEditAndCompareTo != null)
        {
            Platform.runLater(() -> dummy.requestFocus());
            WaitForAsyncUtils.waitForFxEvents();
        }
        String actual = TestUtil.fx(() -> f.get().getText());
        // We only care about cursor position if we haven't finished editing:
        if (endEditAndCompareTo == null)
        {
            // Add curly brackets to indicate selection:
            int anchor = TestUtil.fx(() -> f.get().getAnchor());
            actual = actual.substring(0, anchor) + "^" + actual.substring(anchor);
            int caretPos = TestUtil.fx(() -> f.get().getCaretPosition());
            boolean anchorBeforeCaret = anchor <= caretPos;
            actual = actual.substring(0, caretPos + (anchorBeforeCaret ? 1 : 0)) + "$" + actual.substring(caretPos + (anchorBeforeCaret ? 1 : 0));

            if (!expected.contains("^"))
                expected = expected.replace("$", "^$");
        }
        
        assertEquals("Typed: " + entry + " [chars: " + Utility.listToString(Utility.mapList(Chars.asList(entry.toCharArray()), c -> Integer.toHexString(c))) + "] [codepoints: " + TestUtil.stringAsHexChars(entry) + "]", expected, actual);
        if (endEditAndCompareTo != null)
        {
            CompletableFuture<Either<Exception, Integer>> fut = new CompletableFuture<Either<Exception, Integer>>();
            @SuppressWarnings("nullness")
            EditorKit<?> ed = TestUtil.fx(() -> f.get().getEditorKit());
            @SuppressWarnings({"value", "nullness"})
            @NonNull @Value Object value = TestUtil.fx(() -> ed.getLastCompletedValue());
            @SuppressWarnings("value")
            @Value Object eeco = endEditAndCompareTo;
            Workers.onWorkerThread("", Priority.LOAD_FROM_DISK, () -> {
                try
                {
                    fut.complete(Either.right(Utility.compareValues(eeco, value)));
                }
                catch (InternalException | UserException e)
                {
                    fut.complete(Either.left(e));
                }
            });
            try
            {
                fut.get().either_(e -> fail(e.getLocalizedMessage()), x -> assertEquals(TestUtil.fx(() -> f.get().getText()) + " " + DataTypeUtility._test_valueToString(eeco) + " vs " + DataTypeUtility._test_valueToString(value), 0, x.intValue()));
            }
            catch (InterruptedException | ExecutionException e)
            {
                fail(e.getLocalizedMessage());
            }
        }
    }
    
    @OnThread(Tag.Any)
    private static class DeleteInfo
    {
        private final int deleteLeft;
        private final Either<Supplier<String>, String> contentAfter;
        private final int caretAfter;

        private DeleteInfo(int deleteLeft, String contentAfter, int caretAfter)
        {
            this.deleteLeft = deleteLeft;
            this.contentAfter = Either.right(contentAfter);
            this.caretAfter = caretAfter;
        }

        private DeleteInfo(int deleteLeft, Supplier<String> contentAfter, int caretAfter)
        {
            this.deleteLeft = deleteLeft;
            this.contentAfter = Either.left(contentAfter);
            this.caretAfter = caretAfter;
        }

        public String getContentAfter()
        {
            return contentAfter.either(Supplier::get, Functions.identity());
        }

        public DeleteInfo withPrefix(int deleteBefore, String beforeContent, String after, int posBefore)
        {
            return contentAfter.either(
                ss -> new DeleteInfo(deleteLeft + deleteBefore, () -> beforeContent + ss.get() + after, posBefore + caretAfter),
                s -> new DeleteInfo(deleteLeft + deleteBefore, beforeContent + s + after, posBefore + caretAfter)
            );
        }
    }

    @Property(trials=10)
    @OnThread(Tag.Simulation)
    public void propPartialDelete(@When(seed=5066135170253110651L) @From(GenTypeAndValueGen.class) GenTypeAndValueGen.TypeAndValueGen typeAndValueGen) throws Exception
    {
        TestUtil.printSeedOnFail(() -> {
            @Value Object value = typeAndValueGen.makeValue();
            List<DeleteInfo> possibleDeletes = calcDeletes(typeAndValueGen.getType(), value);
            for (DeleteInfo possibleDelete : possibleDeletes)
            {
                TestUtil.fx_(() -> f.set(field(typeAndValueGen.getType(), value)));
                targetF();
                TestUtil.sleep(100);
                TestUtil.fx_(() -> f.get().moveTo(0));
                String original = TestUtil.fx(() -> f.get().getText());
                press(KeyCode.SHIFT);
                for (int i = 0; i < possibleDelete.deleteLeft; i++)
                {
                    push(KeyCode.RIGHT);
                }
                release(KeyCode.SHIFT);
                // Backspace not delete, so that we delete nothing if at beginning
                // and nothing selected:
                push(KeyCode.BACK_SPACE);
                assertEquals("Deleting " + possibleDelete.deleteLeft + " from {{{" + original + "}}}", possibleDelete.getContentAfter(), TestUtil.fx(() -> f.get().getText()));
                // Not totally sure about where cursor should end up
                // when deleting selection involving undeletable part:
                //assertEquals("Deleting " + possibleDelete.deleteLeft + " from {{{" + original + "}}} to get {{{" + possibleDelete.getContentAfter() + "}}}", possibleDelete.caretAfter, (int)TestUtil.<Integer>fx(() -> f.get().getCaretPosition()));
            }
        });
    }

    // The first item in the list is always what happens if you
    // delete nothing.
    // The last item in the list is always what happens if you delete
    // the whole lot.
    @OnThread(Tag.Simulation)
    private List<DeleteInfo> calcDeletes(DataType type, @Value Object value) throws InternalException, UserException
    {
        return type.fromCollapsed((i, prog) -> value).applyGet(new DataTypeVisitorGetEx<List<DeleteInfo>, UserException>()
        {
            @Override
            @OnThread(Tag.Simulation)
            public List<DeleteInfo> number(GetValue<@Value Number> g, NumberInfo displayInfo) throws InternalException, UserException
            {
                return standard(Utility.numberToString(g.get(0)), "");
            }

            @Override
            @OnThread(Tag.Simulation)
            public List<DeleteInfo> text(GetValue<@Value String> g) throws InternalException, UserException
            {
                String s = g.get(0);
                ArrayList<DeleteInfo> deleteInfos = new ArrayList<>(s.length() + 2);
                deleteInfos.add(new DeleteInfo(0, "\"" + s + "\"", 0));
                int index;
                for (index = 0; index < s.length(); index = s.offsetByCodePoints(index, 1))
                {
                    deleteInfos.add(new DeleteInfo(1 + index, "\"" + s.substring(index) + "\"", 1));
                }
                deleteInfos.add(new DeleteInfo(2 + index, "\"\"", 2));
                return deleteInfos;
            }

            @Override
            @OnThread(Tag.Simulation)
            public List<DeleteInfo> bool(GetValue<@Value Boolean> g) throws InternalException, UserException
            {
                return standard(Boolean.toString(g.get(0)), "");
            }

            @Override
            public List<DeleteInfo> date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> g) throws InternalException
            {
                TemporalAccessor t = (TemporalAccessor)value;
                ArrayList<String> contentChunks = new ArrayList<>();
                ArrayList<String> prompts = new ArrayList<>();
                ArrayList<String> dividers = new ArrayList<>();
                if (dateTimeInfo.getType().hasYearMonth())
                {
                    contentChunks.add(String.format("%04d", t.get(ChronoField.YEAR)));
                    prompts.add("Year");
                    dividers.add("-");
                    contentChunks.add(String.format("%02d", t.get(ChronoField.MONTH_OF_YEAR)));
                    prompts.add("Month");
                    if (dateTimeInfo.getType().hasDay())
                    {
                        dividers.add("-");
                        contentChunks.add(String.format("%02d", t.get(ChronoField.DAY_OF_MONTH)));
                        prompts.add("Day");
                    }
                    if (dateTimeInfo.getType().hasTime())
                        dividers.add(" ");
                }
                if (dateTimeInfo.getType().hasTime())
                {
                    contentChunks.add(String.format("%02d", t.get(ChronoField.HOUR_OF_DAY)));
                    prompts.add("Hour");
                    dividers.add(":");
                    contentChunks.add(String.format("%02d", t.get(ChronoField.MINUTE_OF_HOUR)));
                    prompts.add("Minute");
                    dividers.add(":");
                    int nano = t.get(ChronoField.NANO_OF_SECOND);
                    String secs = String.format("%02d", t.get(ChronoField.SECOND_OF_MINUTE)) + (nano == 0 ? "" : ".") + String.format("%09d", nano).replaceAll("0*$", "");
                    contentChunks.add(secs);
                    prompts.add("Second");
                }
                if (dateTimeInfo.getType().hasZoneId())
                {
                    ZoneId zone = ((ZonedDateTime) t).getZone();
                    dividers.add(" ");
                    contentChunks.add(zone.getId());
                    prompts.add("Zone");
                }
                
                LinkedList<DeleteInfo> deleteInfos = new LinkedList<>();
                String after = "";

                for (int i = contentChunks.size() - 1; i >= 0; i--)
                {
                    int beforeCount = dividers.subList(0, i).stream().mapToInt(String::length).sum() + contentChunks.subList(0, i).stream().mapToInt(String::length).sum();
                    // If we delete everything up to here, we'll only have prompts and dividers
                    StringBuilder before = new StringBuilder();
                    for (int j = 0; j < i; j++)
                    {
                        before.append(prompts.get(j));
                        before.append(dividers.get(j));
                    }
                    
                    String chunk = contentChunks.get(i);

                    // Delete-all must be last only for last item:
                    DeleteInfo deleteAll = new DeleteInfo(beforeCount + chunk.length(), before + prompts.get(i) + after, before.length() + prompts.get(i).length());
                    if (i == contentChunks.size() - 1)
                        deleteInfos.addLast(deleteAll);
                    else
                        deleteInfos.addFirst(deleteAll);
                    
                    for (int c = 0; c < chunk.length() - 1; c++)
                    {
                        deleteInfos.addFirst(new DeleteInfo(beforeCount + c, before.toString() + chunk.substring(c) + after, before.length() + c));
                    }
                    
                    
                    after = ((i - 1 < dividers.size() && i != 0) ? dividers.get(i - 1) : "") + chunk + after;
                }
                
                // Delete nothing:
                deleteInfos.addFirst(new DeleteInfo(0, after, 0));
                
                return deleteInfos;
            }

            @Override
            @OnThread(Tag.Simulation)
            public List<DeleteInfo> tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataTypeValue>> tagTypes, GetValue<Integer> g) throws InternalException, UserException
            {
                TagType<DataTypeValue> tt = tagTypes.get(g.get(0));
                String tag = tt.getName();
                
                ArrayList<DeleteInfo> deleteInfos = new ArrayList<>();
                
                @Nullable List<DeleteInfo> content = tt.getInner() == null ? null : calcDeletes(tt.getInner(), tt.getInner().getCollapsed(0));
                
                for (int index = 0; index < tag.length(); index = tag.offsetByCodePoints(index, 1))
                {
                    deleteInfos.add(new DeleteInfo(index, tag.substring(index) + (content == null ? "" : "(" + content.get(0).getContentAfter() + ")"), 0));
                }
                
                // Deleting whole tag will be prefixing the delete-nothing
                // tag with whole delete
                
                if (content != null)
                {
                    deleteInfos.addAll(Utility.mapListEx(content, d -> d.withPrefix(tag.length() + 1, tag + "(", ")", 1)));
                }
                else
                {
                    deleteInfos.add(new DeleteInfo(tag.length(), "Tag", 0));
                }
                
                return deleteInfos;
            }

            @Override
            @OnThread(Tag.Simulation)
            public List<DeleteInfo> tuple(ImmutableList<DataTypeValue> types) throws InternalException, UserException
            {
                List<DeleteInfo> r = new ArrayList<>();
                List<List<DeleteInfo>> itemDeletes = Utility.mapListEx(types, t -> calcDeletes(t, t.getCollapsed(0)));
                
                // Delete nothing:
                r.add(new DeleteInfo(0, "(" + itemDeletes.stream().map(l -> l.get(0).getContentAfter()).collect(Collectors.joining(",")) + ")", 0));
                
                StringBuilder prevGone = new StringBuilder("(");
                int latestPos = 1;
                int deletePrev = 1;
                for (int i = 0; i < itemDeletes.size(); i++)
                {
                    if (i > 0)
                    {
                        latestPos += 1; // for the comma
                        deletePrev += 1;
                        prevGone.append(",");
                    }
                    String after = itemDeletes.stream().skip(i + 1).map(d -> "," + d.get(0).getContentAfter()).collect(Collectors.joining()) + ")";
                    for (DeleteInfo possible : itemDeletes.get(i))
                    {
                        r.add(possible.withPrefix(deletePrev, prevGone.toString(), after, latestPos));
                    }
                    DeleteInfo deleteAll = itemDeletes.get(i).get(itemDeletes.get(i).size() - 1);
                    latestPos += deleteAll.caretAfter;
                    deletePrev += deleteAll.deleteLeft;
                    prevGone.append(deleteAll.getContentAfter());
                }
                r.add(new DeleteInfo(deletePrev + 1, prevGone.toString() + ")", latestPos + 1));
                return r;
            }

            @Override
            @OnThread(Tag.Simulation)
            public List<DeleteInfo> array(@Nullable DataType inner, GetValue<Pair<Integer, DataTypeValue>> g) throws InternalException, UserException
            {
                assertNotNull(inner);
                @SuppressWarnings("nullness")
                @NonNull DataType innerNN = inner;

                Pair<Integer, DataTypeValue> array = g.get(0);

                List<DeleteInfo> r = new ArrayList<>();
                List<List<DeleteInfo>> itemDeletes =
                        new ArrayList<>();
                for (int arrayIndex = 0; arrayIndex < array.getFirst(); arrayIndex++)
                {
                    @Value Object collapsed = array.getSecond().getCollapsed(arrayIndex);
                    itemDeletes.add(calcDeletes(innerNN, collapsed));
                }

                // Delete nothing:
                r.add(new DeleteInfo(0, "[" + itemDeletes.stream().map(l -> l.get(0).getContentAfter()).collect(Collectors.joining(",")) + "]", 0));
                
                int latestPos = 1;
                int deletePrev = 1;
                for (int i = 0; i < itemDeletes.size(); i++)
                {
                    if (i > 0)
                    {
                        latestPos += 1; // for the comma
                        deletePrev += 1;
                    }
                    String after = itemDeletes.stream().skip(i + 1).map(d -> "," + d.get(0).getContentAfter()).collect(Collectors.joining()) + "]";
                    List<DeleteInfo> itemDeletePoss = itemDeletes.get(i);
                    for (int possIndex = 0; possIndex < itemDeletePoss.size(); possIndex++)
                    {
                        DeleteInfo possible = itemDeletePoss.get(possIndex);
                        // Don't test delete-all here if it is last item in the list:
                        if (possIndex < itemDeletePoss.size() - 1 || i < itemDeletes.size() - 1)
                            r.add(possible.withPrefix(deletePrev, "[", after, latestPos));
                    }
                    DeleteInfo deleteAll = itemDeletePoss.get(itemDeletePoss.size() - 1);
                    deletePrev += deleteAll.deleteLeft;
                }
                // Delete everything:
                r.add(new DeleteInfo(deletePrev + 1, "[]", 2));
                return r;
            }

            @OnThread(Tag.Simulation)
            private List<DeleteInfo> standard(String s, String prompt)
            {
                List<DeleteInfo> deleteInfos = new ArrayList<>();
                int index;
                for (index = 0; index < s.length(); index = s.offsetByCodePoints(index, 1))
                {
                    deleteInfos.add(new DeleteInfo(index, s.substring(index), 0));
                }
                // Delete everything:
                deleteInfos.add(new DeleteInfo(s.length(), prompt, 0));
                return deleteInfos;
            }
        });
    }
}
