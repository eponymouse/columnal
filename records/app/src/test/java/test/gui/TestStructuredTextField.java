package test.gui;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.fxmisc.richtext.model.NavigationActions.SelectionPolicy;
import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;
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
            scene.setRoot(new VBox(field, dummy));
            field.requestFocus();
        });
    }

    @Test
    public void testYMD() throws InternalException
    {
        f.set(TableDisplayUtility.makeField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1)));
        f.get().selectAll();
        type("178", "17$/ / ");
        type("8", "17$/ / "); // Ignored
        type("/3", "17/3$/ ");
        type(":1973", "17/3/1973$");

        f.set(TableDisplayUtility.makeField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1)));
        f.get().start(SelectionPolicy.CLEAR);
        type("", "$1/4/1900");
        type("2", "2$1/4/1900");

        f.set(TableDisplayUtility.makeField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1)));
        f.get().selectAll();
        type("31 11 86","31/11/1986$", true);

        f.set(TableDisplayUtility.makeField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1)));
        f.get().selectAll();
        type("5 6 27","5/6/2027$", true);

        f.set(TableDisplayUtility.makeField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1)));
        f.get().selectAll();
        type("", "$/ / ");
        type("6", "6$/ / ");
        type("-", "6/$/ ");
        type("7", "6/7$/ ");
        type("-", "6/7/$");
        type("3", "6/7/2003$", true);
    }

    private void type(String entry, String expected)
    {
        type(entry, expected, false);
    }


    private void type(String entry, String expected, boolean endEdit)
    {
        write(entry);
        if (endEdit)
        {
            dummy.requestFocus();
        }
        String actual = f.get().getText();
        // Add curly brackets to indicate selection:
        actual = actual.substring(0, f.get().getAnchor()) + "^" + actual.substring(f.get().getAnchor());
        boolean anchorBeforeCaret = f.get().getAnchor() <= f.get().getCaretPosition();
        actual = actual.substring(0, f.get().getCaretPosition() + (anchorBeforeCaret ? 1 : 0)) + "$" + actual.substring(f.get().getCaretPosition() + (anchorBeforeCaret ? 1 : 0));

        if (!expected.contains("^"))
            expected = expected.replace("$", "^$");


        assertEquals(expected, actual);
    }
}
