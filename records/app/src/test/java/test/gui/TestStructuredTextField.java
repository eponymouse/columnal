package test.gui;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
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
import org.apache.commons.lang3.SystemUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.fxmisc.richtext.model.NavigationActions.SelectionPolicy;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.error.InternalException;
import records.gui.StructuredTextField;
import records.gui.TableDisplayUtility;
import test.gen.GenDate;
import test.gen.GenDateTime;
import test.gen.GenOffsetTime;
import test.gen.GenRandom;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;
import utility.gui.FXUtility;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static test.TestUtil.fx;
import static test.TestUtil.fx_;

/**
 * Created by neil on 19/06/2017.
 */
@SuppressWarnings("initialization")
@RunWith(JUnitQuickcheck.class)
public class TestStructuredTextField extends ApplicationTest
{
    private final ObjectProperty<StructuredTextField<?>> f = new SimpleObjectProperty<>();
    private TextField dummy;

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void start(Stage stage) throws Exception
    {
        Scene scene = new Scene(new Label());
        stage.setScene(scene);
        stage.setMinWidth(800);
        stage.setMinHeight(300);
        stage.show();
        dummy = new TextField();
        FXUtility.addChangeListenerPlatformNN(f, field -> {
            FXUtility.runAfter(() ->
            {
                field.setMinWidth(600);
                scene.setRoot(new VBox(field, dummy));
                scene.getRoot().layout();
                stage.sizeToScene();
                scene.getRoot().layout();
                field.requestFocus();
            });
            WaitForAsyncUtils.waitForFxEvents();
        });
    }

    @Test
    public void testPrompt() throws InternalException
    {
        f.set(TableDisplayUtility.makeField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(2034, 10, 29)));
        assertEquals("29/10/2034", fx(() -> f.get().getText()));
        testPositions(new Random(0),
                new int[] {0, 1, 2},
                null,
                new int[] {3, 4, 5},
                null,
                new int[] {6, 7, 8, 9, 10}
        );

        f.set(TableDisplayUtility.makeField(new DateTimeInfo(DateTimeType.DATETIME), LocalDateTime.of(2034, 10, 29, 13, 56, 22)));
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

        f.set(TableDisplayUtility.makeField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1)));
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

        f.set(TableDisplayUtility.makeField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1)));
        // Delete all:
        push(KeyCode.SHORTCUT, KeyCode.A);
        push(KeyCode.DELETE);
        assertEquals("Day/Month/Year", fx(() -> f.get().getText()));
        testPositions(new Random(0),
            new int[] {0},
            null,
            new int[] {4},
            null,
            new int[] {10}
        );

        f.set(TableDisplayUtility.makeField(new DateTimeInfo(DateTimeType.DATETIME), LocalDateTime.of(1900, 1, 1, 1, 1, 1)));
        // Delete all:
        push(KeyCode.SHORTCUT, KeyCode.A);
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
            assertEquals(collapsed.get(i), fx(() -> f.get().getCaretPosition()));
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
    private static void delay()
    {
        try
        {
            Thread.sleep(500);
        }
        catch (InterruptedException e)
        {

        }
    }

    @Test
    public void testYMD() throws InternalException
    {
        f.set(TableDisplayUtility.makeField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1)));
        f.get().selectAll();
        type("17", "17$/Month/Year");
        type("/3", "17/3$/Year");
        type(":1973", "17/3/1973$", LocalDate.of(1973, 3, 17));

        f.set(TableDisplayUtility.makeField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1)));
        push(KeyCode.HOME);
        type("", "$1/4/1900");
        type("2", "21/4/1900$", LocalDate.of(1900, 4, 21));

        f.set(TableDisplayUtility.makeField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1)));
        f.get().selectAll();
        type("31 10 86","31/10/1986$", LocalDate.of(1986, 10, 31));

        f.set(TableDisplayUtility.makeField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1)));
        f.get().selectAll();
        type("5 6 27","5/6/2027$", LocalDate.of(2027, 6, 5));

        f.set(TableDisplayUtility.makeField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1)));
        f.get().selectAll();
        type("6", "6$/Month/Year");
        type("-", "6/$Month/Year");
        type("7", "6/7$/Year");
        type("-", "6/7/$Year");
        type("3", "6/7/2003$", LocalDate.of(2003, 7, 6));

        // Check prompts for invalid dates:
        f.set(TableDisplayUtility.makeField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1)));
        push(ctrlCmd(), KeyCode.A);
        type("", "^1/4/1900$");
        push(KeyCode.DELETE);
        type("", "$Day/Month/Year");
        fx_(()-> dummy.requestFocus());
        type("", "$Day/Month/Year");
        assertNotNull(lookup(".invalid-data-input-popup").query());
        assertNotNull(lookup(".invalid-data-input-popup .invalid-data-revert").query());
        // Click on the revert fix:
        clickOn(".invalid-data-input-popup .invalid-data-revert");
        WaitForAsyncUtils.waitForFxEvents();
        assertNull(lookup(".invalid-data-input-popup").query());
        type("", "1/4/1900$");

        f.set(TableDisplayUtility.makeField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1)));
        push(ctrlCmd(), KeyCode.A);
        type("", "^1/4/1900$");
        push(KeyCode.DELETE);
        type("", "$Day/Month/Year");
        clickOn(dummy);
        type("", "$Day/Month/Year");
        assertNotNull(lookup(".invalid-data-input-popup").query());
        // Now edit again:
        clickOn(f.get());
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
        clickOn(f.get());
        push(KeyCode.HOME);
        push(KeyCode.RIGHT);
        type("/7/2169", "8/7/2169$");
        clickOn(dummy);
        // Should be no popup:
        assertNull(lookup(".invalid-data-input-popup").query());
        // Now delete again:
        clickOn(f.get());
        push(ctrlCmd(), KeyCode.A);
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
    }

    @Property(trials = 15)
    public void propYMD(@From(GenDate.class) LocalDate localDate, @From(GenRandom.class) Random r) throws InternalException
    {
        f.set(TableDisplayUtility.makeField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1)));
        enterDate(localDate, r, "");
    }

    @Property(trials = 15)
    public void propDateTime(@From(GenDateTime.class) LocalDateTime localDateTime, @From(GenRandom.class) Random r) throws InternalException
    {
        f.set(TableDisplayUtility.makeField(new DateTimeInfo(DateTimeType.DATETIME), LocalDateTime.of(1900, 4, 1, 1, 1, 1)));
        String timeVal = localDateTime.get(ChronoField.HOUR_OF_DAY) + ":" + localDateTime.get(ChronoField.MINUTE_OF_HOUR) + ":" + localDateTime.get(ChronoField.SECOND_OF_MINUTE);
        if (localDateTime.getNano() != 0)
        {
            timeVal += new BigDecimal("0." + String.format("%09d", localDateTime.getNano())).stripTrailingZeros().toPlainString().substring(1);
        }
        enterDate(localDateTime, r, " " + timeVal);
    }

    @Property(trials = 15)
    public void propTimeZoned(@From(GenOffsetTime.class) OffsetTime timeZoned, @From(GenRandom.class) Random r) throws InternalException
    {
        f.set(TableDisplayUtility.makeField(new DateTimeInfo(DateTimeType.TIMEOFDAYZONED), OffsetTime.of(1, 1, 1, 1, ZoneOffset.ofHours(3))));
        String timeVal = timeZoned.get(ChronoField.HOUR_OF_DAY) + ":" + timeZoned.get(ChronoField.MINUTE_OF_HOUR) + ":" + timeZoned.get(ChronoField.SECOND_OF_MINUTE);
        if (timeZoned.getNano() != 0)
        {
            timeVal += new BigDecimal("0." + String.format("%09d", timeZoned.getNano())).stripTrailingZeros().toPlainString().substring(1);
        }
        int offsetHour = timeZoned.getOffset().getTotalSeconds() / 3600;
        int offsetMinute = Math.abs(timeZoned.getOffset().getTotalSeconds() / 60 - offsetHour * 60);
        timeVal += (timeZoned.getOffset().getTotalSeconds() < 0 ? "-" : "+") + Math.abs(offsetHour) + ":" + offsetMinute;
        clickOn(f.get());
        push(KeyCode.CONTROL, KeyCode.A);
        type(timeVal, timeVal + "$", timeZoned);
        // TODO also test errors
    }

    private void enterDate(TemporalAccessor localDate, Random r, String extra) throws InternalException
    {
        // Try it in valid form with four digit year:
        clickOn(f.get());
        push(KeyCode.CONTROL, KeyCode.A);
        String value = localDate.get(ChronoField.DAY_OF_MONTH) + "/" + localDate.get(ChronoField.MONTH_OF_YEAR) + "/" + String.format("%04d", localDate.get(ChronoField.YEAR));
        type(value + extra, value + extra + "$", localDate);
        if (localDate.get(ChronoField.YEAR) > Year.now().getValue() - 80 && localDate.get(ChronoField.YEAR) < Year.now().getValue() + 20)
        {
            // Do two digits:
            clickOn(f.get());
            push(KeyCode.CONTROL, KeyCode.A);
            String twoDig = localDate.get(ChronoField.DAY_OF_MONTH) + "/" + localDate.get(ChronoField.MONTH_OF_YEAR) + "/" + Integer.toString(localDate.get(ChronoField.YEAR)).substring(2);
            type(twoDig + extra, value + extra + "$", localDate);
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
        List<String> divs = Arrays.asList("/","-"," ",":", ".");
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
            push(KeyCode.CONTROL, KeyCode.A);
            type(variant + extra, value + extra + "$", localDate);

            // Also try Month name, day, year:
            List<String> monthPoss = monthNames.get(vals[1] - 1);
            String variantMD = monthPoss.get(r.nextInt(monthPoss.size())) + "/" + vals[0] + "/" + vals[2];
            fx_(() -> f.get().requestFocus());
            push(KeyCode.CONTROL, KeyCode.A);
            type(variantMD + extra, value + extra + "$", localDate);
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
        clickOn(f.get());
        push(ctrlCmd(), KeyCode.A);
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
        return SystemUtils.IS_OS_MAC_OSX ? KeyCode.COMMAND : KeyCode.CONTROL;
    }

    private void type(String entry, String expected)
    {
        type(entry, expected, null);
    }


    private void type(String entry, String expected, @Nullable TemporalAccessor endEditAndCompareTo)
    {
        write(entry);
        if (endEditAndCompareTo != null)
        {
            FXUtility.runAfter(() -> dummy.requestFocus());
            WaitForAsyncUtils.waitForFxEvents();
        }
        String actual = f.get().getText();
        // Add curly brackets to indicate selection:
        actual = actual.substring(0, f.get().getAnchor()) + "^" + actual.substring(f.get().getAnchor());
        boolean anchorBeforeCaret = f.get().getAnchor() <= f.get().getCaretPosition();
        actual = actual.substring(0, f.get().getCaretPosition() + (anchorBeforeCaret ? 1 : 0)) + "$" + actual.substring(f.get().getCaretPosition() + (anchorBeforeCaret ? 1 : 0));

        if (!expected.contains("^"))
            expected = expected.replace("$", "^$");


        assertEquals(expected, actual);
        if (endEditAndCompareTo != null)
        {
            assertEquals(f.get().getText(), endEditAndCompareTo, f.get().getCompletedValue());
        }
    }
}
