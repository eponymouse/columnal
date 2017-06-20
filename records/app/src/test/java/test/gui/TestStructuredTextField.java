package test.gui;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.units.qual.A;
import org.fxmisc.richtext.model.NavigationActions.SelectionPolicy;
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

import static org.junit.Assert.assertEquals;

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
                field.requestFocus();
            });
            WaitForAsyncUtils.waitForFxEvents();
        });
    }

    @Test
    public void testYMD() throws InternalException
    {
        f.set(TableDisplayUtility.makeField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1)));
        f.get().selectAll();
        type("178", "17$//");
        type("8", "17$//"); // Ignored
        type("/3", "17/3$/");
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
        type("6", "6$//");
        type("-", "6/$/");
        type("7", "6/7$/");
        type("-", "6/7/$");
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
