package test.gui;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.generator.java.lang.StringGenerator;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyCombination.Modifier;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.SystemUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;
import records.data.ColumnId;
import records.data.EditableRecordSet;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.data.datatype.NumberInfo;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.gui.TableDisplayUtility.DisplayCacheSTF;
import records.gui.stable.StableView;
import records.gui.stf.STFAutoCompleteCell;
import records.gui.stf.StructuredTextField;
import records.gui.TableDisplayUtility;
import test.gen.GenDate;
import test.gen.GenDateTime;
import test.gen.GenDateTimeZoned;
import test.gen.GenNumber;
import test.gen.GenNumberAsString;
import test.gen.GenOffsetTime;
import test.gen.GenRandom;
import test.gen.GenTaggedTypeAndValueGen;
import test.gen.GenTime;
import test.gen.GenTypeAndValueGen;
import test.gen.GenYearMonth;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
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
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeThat;
import static test.TestUtil.fx;
import static test.TestUtil.fx_;
import static test.TestUtil.sim;

/**
 * Created by neil on 19/06/2017.
 */
@SuppressWarnings("initialization")
@RunWith(JUnitQuickcheck.class)
public class TestStructuredTextField extends ApplicationTest
{
    private StableView stableView;
    private final ObjectProperty<StructuredTextField<?>> f = new SimpleObjectProperty<>();
    private TextField dummy;

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void start(Stage stage) throws Exception
    {
        stableView = new StableView();
        dummy = new TextField();
        Scene scene = new Scene(new VBox(dummy, stableView.getNode()));
        stage.setScene(scene);
        stage.setMinWidth(800);
        stage.setMinHeight(300);
        stage.show();
        FXUtility.addChangeListenerPlatformNN(f, field -> {
            FXUtility.runAfter(() ->
            {
                scene.getRoot().layout();
                stage.sizeToScene();
                scene.getRoot().layout();
                field.requestFocus();
            });
            WaitForAsyncUtils.waitForFxEvents();
        });
    }

    // TODO add tests for cut, copy, paste, select-then-type

    @Test
    public void testPrompt() throws InternalException
    {
        f.set(dateField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(2034, 10, 29)));
        assertEquals("29/10/2034", fx(() -> f.get().getText()));
        testPositions(new Random(0),
                new int[] {0, 1, 2},
                null,
                new int[] {3, 4, 5},
                null,
                new int[] {6, 7, 8, 9, 10}
        );

        f.set(dateField(new DateTimeInfo(DateTimeType.DATETIME), LocalDateTime.of(2034, 10, 29, 13, 56, 22)));
        assertEquals("29/10/2034 13:56:22", fx(() -> f.get().getText()));
        testPositions(new Random(0),
            new int[] {0, 1, 2},
            null,
            new int[] {3, 4, 5},
            null,
            new int[] {6, 7, 8, 9, 10},
            null,
            new int[] {11, 12, 13},
            null,
            new int[] {14, 15, 16},
            null,
            new int[] {17, 18, 19}
        );

        f.set(dateField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1)));
        // Delete the month:
        push(KeyCode.HOME);
        push(KeyCode.RIGHT);
        push(KeyCode.RIGHT);
        push(KeyCode.DELETE);
        assertEquals("1/Month/1900", fx(() -> f.get().getText()));
        testPositions(new Random(0),
            new int[] {0, 1},
            null,
            new int[] {2},
            null,
            new int[] {8, 9, 10, 11, 12}
        );

        f.set(dateField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1)));
        // Delete all:
        pushSelectAll();
        push(KeyCode.DELETE);
        assertEquals("Day/Month/Year", fx(() -> f.get().getText()));
        testPositions(new Random(0),
            new int[] {0},
            null,
            new int[] {4},
            null,
            new int[] {10}
        );

        f.set(dateField(new DateTimeInfo(DateTimeType.DATETIME), LocalDateTime.of(1900, 1, 1, 1, 1, 1)));
        // Delete all:
        pushSelectAll();
        push(KeyCode.DELETE);
        assertEquals("Day/Month/Year Hour:Minute:Second", fx(() -> f.get().getText()));
        testPositions(new Random(0),
            new int[] {0},
            null,
            new int[] {4},
            null,
            new int[] {10},
            null,
            new int[] {15},
            null,
            new int[] {20},
            null,
            new int[] {27}
        );
    }

    private StructuredTextField<?> dateField(DateTimeInfo dateTimeInfo, TemporalAccessor t) throws InternalException
    {
        return field(DataType.date(dateTimeInfo), t);
    }

    private StructuredTextField<?> field(DataType dataType, Object value) throws InternalException
    {
        CompletableFuture<DataTypeValue> fut = new CompletableFuture<>();
        Workers.onWorkerThread("", Priority.SAVE_ENTRY, () ->
        {
            try
            {
                EditableRecordSet rs = new EditableRecordSet(Collections.singletonList(dataType.makeImmediateColumn(new ColumnId("C"), Collections.singletonList(value))), () -> 1);
                fut.complete(rs.getColumns().get(0).getType());
            }
            catch (InternalException | UserException e)
            {
                Utility.log(e);
            }
        });

        fx_(() ->
        {
            try
            {
                DisplayCacheSTF<?> cacheSTF = TableDisplayUtility.makeField(fut.get(2000, TimeUnit.MILLISECONDS));
                stableView.setColumns(Collections.singletonList(new Pair<>("C", cacheSTF)), null);
                stableView.setRows(i -> i == 0);
                stableView.resizeColumn(0, 600);
            }
            catch (InterruptedException | ExecutionException | TimeoutException | InternalException e)
            {
                throw new RuntimeException(e);
            }
        });

        for (int i = 0; i < 5; i++)
        {
            @Nullable StructuredTextField<?> stf = fx(() -> {
                stableView.getNode().applyCss();
                return (@Nullable StructuredTextField<?>) stableView.getNode().lookup(".structured-text-field");
            });
            if (stf != null)
                return stf;
            delay(200);
        }
        throw new RuntimeException("Couldn't find STF");
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
        assertEquals(positions[0][0], f.get().getCaretPosition());
        push(KeyCode.END);
        assertEquals(positions[positions.length - 1][positions[positions.length - 1].length - 1], f.get().getCaretPosition());
        push(KeyCode.HOME);
        assertEquals(positions[0][0], f.get().getCaretPosition());

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
                    collapsedX.add(fx(() -> newPos == 0 ?
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
            assertEquals(collapsed.get(i), fx(() -> f.get().getCaretPosition()));
            // Forward then back:
            if (i + 1 < collapsed.size())
            {
                push(KeyCode.RIGHT);
                push(KeyCode.LEFT);
            }
            assertEquals(collapsed.get(i), fx(() -> f.get().getCaretPosition()));
            // Back then forward:
            if (i > 0)
            {
                push(KeyCode.LEFT);
                push(KeyCode.RIGHT);
            }
            assertEquals(collapsed.get(i), fx(() -> f.get().getCaretPosition()));
            push(KeyCode.RIGHT);
        }
        assertEquals(collapsed.get(collapsed.size() - 1), fx(() -> f.get().getCaretPosition()));

        // Basic click tests:
        Bounds screenBounds = fx(() -> f.get().localToScreen(f.get().getBoundsInLocal()));
        Map<Double, Integer> xToPos = new HashMap<>();
        for (int i = 0; i < collapsed.size(); i++)
        {
            // Clicking on the exact divide should end up at the right character position:
            clickOn(collapsedX.get(i), screenBounds.getMinY() + 4.0);
            // Move so we don't treat as double click:
            moveBy(10, 0);
            assertEquals("Clicked: " + collapsedX.get(i) + ", " + (screenBounds.getMinY() + 4.0), collapsed.get(i), fx(() -> f.get().getCaretPosition()));
            if (i + 1 < collapsed.size())
            {
                // Clicking progressively between the two positions should end up in one, or the other:
                // (In particular, clicking on a prompt should not end up in the prompt, it should glue to one side or other)
                for (double x = collapsedX.get(i); x <= collapsedX.get(i + 1); x += 2.0)
                {
                    clickOn(x, screenBounds.getMinY() + 4.0);
                    // Move so we don't treat as double click:
                    moveBy(10, 0);
                    int outcome = fx(() -> f.get().getCaretPosition());
                    assertThat("Aiming for " + i + " by clicking at offset " + (x - screenBounds.getMinX()), outcome, Matchers.isIn(Arrays.asList(collapsed.get(i), collapsed.get(i + 1))));
                    xToPos.put(x, outcome);
                }
            }
        }

        // Try shift-click to select some randomly selected pairs of positions:
        List<Entry<Double, Integer>> allPos = new ArrayList<>(xToPos.entrySet());
        for (int i = 0; i < 40; i++)
        {
            Entry<Double, Integer> a = allPos.get(r.nextInt(allPos.size()));
            Entry<Double, Integer> b = allPos.get(r.nextInt(allPos.size()));

            clickOn(a.getKey(), screenBounds.getMinY() + 4.0);
            press(KeyCode.SHIFT);
            clickOn(b.getKey(), screenBounds.getMinY() + 4.0);
            release(KeyCode.SHIFT);
            String label = "#" + i + " from " + a.getKey() + "->" + a.getValue() + " to " + b.getKey() + "->" + b.getValue();
            assertEquals(label, a.getValue(), fx(() -> f.get().getAnchor()));
            assertEquals(label, b.getValue(), fx(() -> f.get().getCaretPosition()));
            // Move so we don't treat as double click:
            moveBy(10, 0);
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
            // Use click to initially position:
            clickOn(start.getKey(), screenBounds.getMinY() + 4.0);
            assertEquals(start.getValue(), fx(() -> f.get().getCaretPosition()));
            assertEquals(start.getValue(), fx(() -> f.get().getAnchor()));
            // Then press ctrl-left or ctrl-right some number of times:

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

            String label = "From " + start.getValue() + " sel:" + useSelection + " right:" + right + " x " + number + " content " + f.get().getText();
            // Work out where we expect it to end up:
            assertEquals(label, useSelection ? (int)start.getValue() : pos, (int)fx(() -> f.get().getAnchor()));
            assertEquals(label, pos, (int)fx(() -> f.get().getCaretPosition()));
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

    // Wait.  Useful to stop multiple consecutive clicks turning into double clicks
    private static void delay(int millis)
    {
        try
        {
            Thread.sleep(millis);
        }
        catch (InterruptedException e)
        {

        }
    }

    @Test
    public void testYMD() throws InternalException
    {
        f.set(dateField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1)));
        f.get().selectAll();
        type("17", "17$/Month/Year");
        type("/3/", "17/3/$Year");
        type("1973", "17/3/1973", LocalDate.of(1973, 3, 17));

        f.set(dateField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1)));
        push(KeyCode.HOME);
        type("", "$1/4/1900");
        type("2", "21/4/1900", LocalDate.of(1900, 4, 21));

        f.set(dateField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1)));
        f.get().selectAll();
        type("31 10 86","31/10/1986", LocalDate.of(1986, 10, 31));

        f.set(dateField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1)));
        f.get().selectAll();
        type("5 6 27","5/6/2027", LocalDate.of(2027, 6, 5));

        f.set(dateField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1)));
        f.get().selectAll();
        type("6", "6$/Month/Year");
        type("-", "6/$Month/Year");
        type("7", "6/7$/Year");
        type("-", "6/7/$Year");
        type("3", "6/7/2003", LocalDate.of(2003, 7, 6));

        // Check prompts for invalid dates:
        f.set(dateField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1)));
        pushSelectAll();
        type("", "^1/4/1900$");
        push(KeyCode.DELETE);
        type("", "$Day/Month/Year");
        fx_(()-> dummy.requestFocus());
        type("", "Day/Month/$Year");
        assertNotNull(lookup(".invalid-data-input-popup").query());
        assertNotNull(lookup(".invalid-data-input-popup .invalid-data-revert").query());
        // Click on the revert fix:
        clickOn(".invalid-data-input-popup .invalid-data-revert");
        WaitForAsyncUtils.waitForFxEvents();
        assertNull(lookup(".invalid-data-input-popup").query());
        type("", "1/4/1900$");

        f.set(dateField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1)));
        pushSelectAll();
        type("", "^1/4/1900$");
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
        assertThat(lookup(".invalid-data-input-popup .invalid-data-revert").<Label>query().getText(), Matchers.containsString("1/4/1900"));
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
        type("", "^8/7/2169$");
        push(KeyCode.DELETE);
        clickOn(dummy);
        // New popup should show most recent value:
        assertNotNull(lookup(".invalid-data-input-popup").query());
        assertThat(lookup(".invalid-data-input-popup .invalid-data-revert").<Label>query().getText(), Matchers.containsString("8/7/2169"));

        // Check swapped or invalid dates have the right suggestions:
        checkFix("0/3/2000", "1/3/2000");
        checkFix("8/0/2000", "8/1/2000");
        checkFix("4/13/2000", "13/4/2000");
        checkFix("13/13/2000", "13/12/2000");
        checkFix("31/9/2000", "30/9/2000");
        checkFix("31/9/2000", "1/10/2000");
        checkFix("32/9/2000", "30/9/2000");
        checkFix("1968/3/4", "4/3/1968");
        checkFix("32/9/1", "1/9/2032");
        checkFix("32/9/1", "30/9/2001");
        checkFix("68/3/4", "4/3/1968");

        targetF();
        pushSelectAll();
        type("10/12/0378", "10/12/0378", LocalDate.of(378, 12, 10));
        targetF();
        pushSelectAll();
        type("01/02/3", "1/2/2003", LocalDate.of(2003, 2, 1));
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
            fx_(() -> f.get().selectAll());
        else
            push(ctrlCmd(), KeyCode.A);
    }

    public void targetF()
    {
        doubleClickOn(f.get());
    }

    @Property(trials = 15)
    public void propYMD(@From(GenDate.class) LocalDate localDate, @From(GenRandom.class) Random r) throws InternalException
    {
        f.set(dateField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1)));
        enterDate(localDate, r, "");
    }

    @Property(trials = 20)
    public void propString(@From(StringGenerator.class) String s) throws InternalException
    {
        int[] cs = s.codePoints().filter(c -> c <= 0x7dff && !Character.isISOControl(c) && c != '\"').toArray();
        s = new String(cs, 0, cs.length);

        f.set(field(DataType.TEXT, "initial value"));
        targetF();
        pushSelectAll();
        if (s.isEmpty())
            push(KeyCode.DELETE);
        type(s, "\"" + s + "\"", s);
    }

    @Property(trials = 20)
    public void propNumberPair(@From(GenNumberAsString.class) String numAsStringA, @From(GenNumberAsString.class) String numAsStringB, @From(GenNumber.class) Number initial, boolean space) throws InternalException
    {
        BigDecimal numA = new BigDecimal(numAsStringA, MathContext.DECIMAL128);
        BigDecimal numB = new BigDecimal(numAsStringB, MathContext.DECIMAL128);
        DataType numType = DataType.number(new NumberInfo(Unit.SCALAR, null));
        f.set(field(DataType.tuple(numType, numType), new Object[] {initial, initial}));
        targetF();
        pushSelectAll();
        type("", "(^" + initial.toString() + "," + initial.toString() + "$)");
        type(numAsStringA + "," + (space ? " " : "") + numAsStringB, "(" + numAsStringA + "," + numAsStringB + ")", new Object[]{numA, numB});
        targetF();
        push(KeyCode.END);
        type("", "(" + numAsStringA + "," + numAsStringB + "^$)");
        push(KeyCode.RIGHT);
        type("", "(" + numAsStringA + "," + numAsStringB + "^$)");
        push(KeyCode.HOME);
        type("", "(^$" + numAsStringA + "," + numAsStringB + ")");
        push(KeyCode.LEFT);
        type("", "(^$" + numAsStringA + "," + numAsStringB + ")");
    }

    @Property(trials = 20)
    public void propNumberBoolPair(@From(GenNumberAsString.class) String numAsString, boolean boolValue, @From(GenRandom.class) Random r) throws InternalException
    {
        BigDecimal num = new BigDecimal(numAsString, MathContext.DECIMAL128);
        DataType numType = DataType.number(new NumberInfo(Unit.SCALAR, null));
        boolean numFirst = r.nextBoolean();
        DataType tupleType = numFirst ? DataType.tuple(numType, DataType.BOOLEAN) : DataType.tuple(DataType.BOOLEAN, numType);
        f.set(field(tupleType, numFirst ? new Object[] {0, !boolValue} : new Object[] {!boolValue, 0}));
        targetF();
        pushSelectAll();
        if (numFirst)
        {
            type("", "(^0," + !boolValue + "$)");
            type(numAsString, "(" + numAsString + "$,)");
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
            type("", "(^" + !boolValue + ",0$)");
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

    @Property(trials = 20)
    public void propNumber(@From(GenNumberAsString.class) String numAsString, @From(GenNumber.class) Number initial, char c) throws InternalException
    {
        // c should be a non-numeric char:
        assumeThat(c, Matchers.<Character>not(Matchers.isIn(ArrayUtils.toObject("-.0123456789".toCharArray()))));
        assumeThat(c, Matchers.<Character>greaterThan(' '));

        BigDecimal num = new BigDecimal(numAsString, MathContext.DECIMAL128);
        f.set(field(DataType.number(new NumberInfo(Unit.SCALAR, null)), initial));
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
    }

    @Property(trials = 15)
    public void propDateTime(@From(GenDateTime.class) LocalDateTime localDateTime, @From(GenRandom.class) Random r) throws InternalException
    {
        f.set(dateField(new DateTimeInfo(DateTimeType.DATETIME), LocalDateTime.of(1900, 4, 1, 1, 1, 1)));
        String timeVal = timeString(localDateTime);
        enterDate(localDateTime, r, " " + timeVal);
    }

    private static String timeString(TemporalAccessor t)
    {
        String timeVal = t.get(ChronoField.HOUR_OF_DAY) + ":" + t.get(ChronoField.MINUTE_OF_HOUR) + ":" + t.get(ChronoField.SECOND_OF_MINUTE);
        if (t.get(ChronoField.NANO_OF_SECOND) != 0)
        {
            timeVal += new BigDecimal("0." + String.format("%09d", t.get(ChronoField.NANO_OF_SECOND))).stripTrailingZeros().toPlainString().substring(1);
        }
        return timeVal;
    }

    @Property(trials = 15)
    public void propYM(@From(GenYearMonth.class) YearMonth yearMonth, @From(GenRandom.class) Random r) throws InternalException
    {
        f.set(dateField(new DateTimeInfo(DateTimeType.YEARMONTH), YearMonth.of(1900, 1)));
        String timeVal = yearMonth.getMonthValue() + "/" + yearMonth.getYear();
        targetF();
        pushSelectAll();
        type(timeVal, timeVal, yearMonth);
        // TODO also test errors, and other variants (e.g. text months)
    }

    @Property(trials = 15)
    public void propDateTimeZoned(@From(GenDateTimeZoned.class) ZonedDateTime zonedDateTime) throws InternalException, UserException
    {
        f.set(dateField(new DateTimeInfo(DateTimeType.DATETIMEZONED), ZonedDateTime.from(DateTimeInfo.DEFAULT_VALUE)));
        String timeVal = sim(new SimulationSupplier<String>()
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
        f.set(dateField(new DateTimeInfo(DateTimeType.TIMEOFDAY), LocalTime.of(1, 1, 1, 1)));
        String timeVal = timeString(localTime);
        targetF();
        pushSelectAll();
        type(timeVal, timeVal, localTime);
        // TODO also test errors
    }

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

    // There's only two possible values, so no need to make it a property!
    @Test
    public void testBool() throws InternalException
    {
        f.set(field(DataType.BOOLEAN, false));
        targetF();
        push(KeyCode.HOME);
        // Should immediately show the popup:
        assertAutoCompleteVisible(0, null);
        // Tried using the :filled pseudo-class here but that didn't seem to work:
        Set<STFAutoCompleteCell> items = lookup(".stf-autocomplete .stf-autocomplete-item").lookup((STFAutoCompleteCell c) -> !c.isEmpty()).queryAll();
        assertEquals(2, items.size());
        assertEquals(Arrays.asList("false", "true"), items.stream().map(c -> c.getItem().suggestion).sorted().collect(Collectors.toList()));
        assertEquals(Arrays.asList("false", "true"), items.stream().map(c -> c.getText()).sorted().collect(Collectors.toList()));

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
        STFAutoCompleteCell autoSuggestTrue = lookup(".stf-autocomplete .stf-autocomplete-item").<STFAutoCompleteCell>lookup((Predicate<STFAutoCompleteCell>) ((STFAutoCompleteCell c) -> !c.isEmpty() && "true".equals(c.getItem().suggestion))).<STFAutoCompleteCell>query();
        assertNotNull(autoSuggestTrue);
        clickOn(autoSuggestTrue);
        type("", "true", true);
        assertNull(lookup(".stf-autocomplete").query());

        // Tab should auto-complete plausible option:
        targetF();
        pushSelectAll();
        type("F", "F$");
        push(KeyCode.TAB);
        type("", "false", false);
    }

    @Property(trials=20)
    public void propTagged(@When(seed=-1306588459139486136L) @From(GenTaggedTypeAndValueGen.class) GenTypeAndValueGen.TypeAndValueGen taggedTypeAndValueGen) throws UserException, InternalException
    {
        f.set(field(taggedTypeAndValueGen.getType(), taggedTypeAndValueGen.makeValue()));
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
            String inner = sim(new SimulationSupplier<String>()
            {
                @Override
                @OnThread(Tag.Simulation)
                public String get() throws InternalException, UserException
                {
                    return DataTypeUtility.valueToString(tag.getInner(), value.getInner(), taggedTypeAndValueGen.getType());
                }
            });
            type(tagName.substring(tagName.length() - 1) + "(" + inner + ")", tagName + "(" + inner + ")", value);
        }
    }

    @Test
    public void testList() throws InternalException
    {
        DataType dataType = DataType.array(DataType.NUMBER);
        f.set(field(dataType, new ListExList(Collections.emptyList())));
        push(KeyCode.LEFT);
        push(KeyCode.LEFT);
        type("", "$[]");
        push(KeyCode.RIGHT);
        type("", "[$]");
        type("0.1", "[0.1$]");
        type(",", "[0.1,$Number]");
        push(KeyCode.BACK_SPACE);
        type("", "[0.1$]");
        push(KeyCode.BACK_SPACE);
        push(KeyCode.BACK_SPACE);
        type("", "[0$]");
        push(KeyCode.BACK_SPACE);
        type("", "[$Number]");
        push(KeyCode.LEFT);
        type("", "$[]");

        // TODO try with more structured slots, and with nested lists
        // Test that deleting comma deletes the adjacent empty slot
    }

    public Node assertAutoCompleteVisible(int atChar, @Nullable Node sameAs)
    {
        Node autoComplete = lookup(".stf-autocomplete").query();
        assertNotNull(autoComplete);
        if (sameAs != null)
            assertSame(sameAs, autoComplete);
        // In the right place:
        Bounds fScreen = f.get().localToScreen(f.get().getBoundsInLocal());
        Bounds charBounds = f.get().getCharacterBoundsOnScreen(atChar, atChar + 1).get();
        assertThat(autoComplete.localToScreen(autoComplete.getBoundsInLocal()).getMinX(), Matchers.<Double>is(Matchers.<Double>both(Matchers.<Double>greaterThan(charBounds.getMinX() - 8)).<Double>and(Matchers.<Double>lessThan(charBounds.getMaxX() + 2))));
        assertThat(autoComplete.localToScreen(autoComplete.getBoundsInLocal()).getMinY(), Matchers.<Double>is(Matchers.<Double>both(Matchers.<Double>greaterThan(charBounds.getMaxY() - 2)).<Double>and(Matchers.<Double>lessThan(charBounds.getMaxY() + 5))));
        return autoComplete;
    }

    private void enterDate(TemporalAccessor localDate, Random r, String extra) throws InternalException
    {
        // Try it in valid form with four digit year:
        targetF();
        pushSelectAll();
        String value = localDate.get(ChronoField.DAY_OF_MONTH) + "/" + localDate.get(ChronoField.MONTH_OF_YEAR) + "/" + String.format("%04d", localDate.get(ChronoField.YEAR));
        type(value + extra, value + extra, localDate);
        if (localDate.get(ChronoField.YEAR) > Year.now().getValue() - 80 && localDate.get(ChronoField.YEAR) < Year.now().getValue() + 20)
        {
            // Do two digits:
            targetF();
            pushSelectAll();
            String twoDig = localDate.get(ChronoField.DAY_OF_MONTH) + "/" + localDate.get(ChronoField.MONTH_OF_YEAR) + "/" + Integer.toString(localDate.get(ChronoField.YEAR)).substring(2);
            type(twoDig + extra, value + extra, localDate);
        }
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
            fx_(() -> f.get().requestFocus());
            pushSelectAll();
            type(variant + extra, value + extra, localDate);

            // Also try Month name, day, year:
            List<String> monthPoss = monthNames.get(vals[1] - 1);
            String variantMD = monthPoss.get(r.nextInt(monthPoss.size())) + "/" + vals[0] + "/" + vals[2];
            fx_(() -> f.get().requestFocus());
            pushSelectAll();
            type(variantMD + extra, value + extra, localDate);
        }
        // Try errors and fixes; transposition, etc:
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
    }

    private void checkFix(String input, String suggestedFix)
    {
        targetF();
        pushSelectAll();
        type(input, input + "$");
        clickOn(dummy);
        assertNotNull(lookup(".invalid-data-input-popup").query());
        Node fix = lookup(".invalid-data-input-popup .invalid-data-fix").lookup((Label l) -> l.getText().contains(suggestedFix)).query();
        assertNotNull(lookup(".invalid-data-input-popup .invalid-data-fix").<Label>queryAll().stream().map(l -> l.getText()).collect(Collectors.joining(" | ")),fix);
        clickOn(fix);
        type("", suggestedFix + "$");
    }

    private KeyCode ctrlCmd()
    {
        return SystemUtils.IS_OS_MAC_OSX ? KeyCode.SHORTCUT : KeyCode.CONTROL;
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
            FXUtility.runAfter(() -> dummy.requestFocus());
            WaitForAsyncUtils.waitForFxEvents();
        }
        String actual = fx(() -> f.get().getText());
        // We only care about cursor position if we haven't finished editing:
        if (endEditAndCompareTo == null)
        {
            // Add curly brackets to indicate selection:
            int anchor = fx(() -> f.get().getAnchor());
            actual = actual.substring(0, anchor) + "^" + actual.substring(anchor);
            int caretPos = fx(() -> f.get().getCaretPosition());
            boolean anchorBeforeCaret = anchor <= caretPos;
            actual = actual.substring(0, caretPos + (anchorBeforeCaret ? 1 : 0)) + "$" + actual.substring(caretPos + (anchorBeforeCaret ? 1 : 0));

            if (!expected.contains("^"))
                expected = expected.replace("$", "^$");
        }


        assertEquals("Typed: " + entry, expected, actual);
        if (endEditAndCompareTo != null)
        {
            CompletableFuture<Either<Exception, Integer>> fut = new CompletableFuture<Either<Exception, Integer>>();
            Object value = f.get().getCompletedValue();
            Workers.onWorkerThread("", Priority.LOAD_FROM_DISK, () -> {
                try
                {
                    fut.complete(Either.right(Utility.compareValues(endEditAndCompareTo, value)));
                }
                catch (InternalException | UserException e)
                {
                    fut.complete(Either.left(e));
                }
            });
            try
            {
                fut.get().either_(e -> fail(e.getLocalizedMessage()), x -> assertEquals(f.get().getText() + " " + DataTypeUtility._test_valueToString(endEditAndCompareTo) + " vs " + DataTypeUtility._test_valueToString(value), 0, x.intValue()));
            }
            catch (InterruptedException | ExecutionException e)
            {
                fail(e.getLocalizedMessage());
            }
        }
    }
}
