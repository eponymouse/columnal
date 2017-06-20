package test.gui;

import org.fxmisc.richtext.model.NavigationActions.SelectionPolicy;
import org.junit.Test;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.error.InternalException;
import records.gui.StructuredTextField;
import records.gui.TableDisplayUtility;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.Assert.assertEquals;

/**
 * Created by neil on 19/06/2017.
 */
@SuppressWarnings("initialization")
public class TestStructuredTextField
{
    private StructuredTextField f;

    @Test
    public void testYMD() throws InternalException
    {
        f = TableDisplayUtility.makeField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1));
        f.selectAll();
        type("", "$ / / ");
        type("178", "17$/ / ");
        type("8", "17$/ / "); // Ignored
        type("/3", "17/3$/ ");
        type(":1973", "17/3/1973$");

        f = TableDisplayUtility.makeField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1));
        f.start(SelectionPolicy.CLEAR);
        type("", "$1/4/1900");
        type("2", "2$1/4/1900");

        f = TableDisplayUtility.makeField(new DateTimeInfo(DateTimeType.YEARMONTHDAY), LocalDate.of(1900, 4, 1));
        f.selectAll();
        type("31 11 86","31/11/1986$", true);
    }

    private void type(String entry, String expected)
    {
        type(entry, expected, false);
    }


    private void type(String entry, String expected, boolean endEdit)
    {
        f.replaceSelection(entry);
        if (endEdit)
            f.endEdit();
        String actual = f.getText();
        // Add curly brackets to indicate selection:
        actual = actual.substring(0, f.getAnchor()) + "^" + actual.substring(f.getAnchor());
        boolean anchorBeforeCaret = f.getAnchor() <= f.getCaretPosition();
        actual = actual.substring(0, f.getCaretPosition() + (anchorBeforeCaret ? 1 : 0)) + "$" + actual.substring(f.getCaretPosition() + (anchorBeforeCaret ? 1 : 0));

        if (!expected.contains("^"))
            expected = expected.replace("$", "^$");


        assertEquals(expected, actual);
    }
}
