package test.gui;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.units.qual.A;
import org.fxmisc.richtext.model.NavigationActions.SelectionPolicy;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.error.InternalException;
import records.gui.StructuredTextField;
import records.gui.TableDisplayUtility;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.gui.FXUtility;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static test.TestUtil.fx;
import static test.TestUtil.fx_;

/**
 * Created by neil on 19/06/2017.
 */
@SuppressWarnings("initialization")
public class TestStructuredTextField extends ApplicationTest
{
    private final ObjectProperty<StructuredTextField> f = new SimpleObjectProperty<>();
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
        resetToPrompt();
        //         1/Month/1900
        testPositions(
            new int[] {0, 1},
            null,
            new int[] {2},
            null,
            new int[] {8, 9, 10, 11, 12}
        );
    }

    private void testPositions(int[]... positions)
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

        // TODO do test with shift-left/right, with ctrl-left/ctrl-right, and with shift-clicking

        // Basic click tests:
        Bounds screenBounds = fx(() -> f.get().localToScreen(f.get().getBoundsInLocal()));
        for (int i = 0; i < collapsed.size(); i++)
        {
            // Clicking on the exact divide should end up at the right character position:
            clickOn(collapsedX.get(i), screenBounds.getMinY() + 4.0);
            delay();
            assertEquals(collapsed.get(i), fx(() -> f.get().getCaretPosition()));
            if (i + 1 < collapsed.size())
            {
                // Clicking progressively between the two positions should end up in one, or the other:
                // (In particular, clicking on a prompt should not end up in the prompt, it should glue to one side or other)
                for (double x = collapsedX.get(i); x <= collapsedX.get(i + 1); x += 2.0)
                {
                    clickOn(x, screenBounds.getMinY() + 4.0);
                    delay();
                    assertThat("Aiming for " + i + " by clicking at offset " + (x - screenBounds.getMinX()), fx(() -> f.get().getCaretPosition()), Matchers.isIn(Arrays.asList(collapsed.get(i), collapsed.get(i + 1))));
                }
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

    private void resetToPrompt() throws InternalException
    {
        f.set(TableDisplayUtility.makeField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1)));
        WaitForAsyncUtils.waitForFxEvents();
        push(KeyCode.HOME);
        push(KeyCode.RIGHT);
        push(KeyCode.RIGHT);
        push(KeyCode.DELETE);
        assertEquals("1/Month/1900", fx(() -> f.get().getText()));
    }

    @Test
    public void testYMD() throws InternalException
    {
        f.set(TableDisplayUtility.makeField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1)));
        f.get().selectAll();
        type("178", "17$/Month/Year");
        type("8", "17$/Month/Year"); // Ignored
        type("/3", "17/3$/Year");
        type(":1973", "17/3/1973$", LocalDate.of(1973, 3, 17));

        f.set(TableDisplayUtility.makeField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1)));
        f.get().start(SelectionPolicy.CLEAR);
        type("", "$1/4/1900");
        type("2", "2$1/4/1900", LocalDate.of(1900, 4, 21));

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
            assertEquals(endEditAndCompareTo, f.get().getCompletedValue());
        }
    }
}
