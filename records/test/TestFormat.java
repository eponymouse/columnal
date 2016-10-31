import org.junit.Test;
import records.data.type.ColumnType;
import records.data.type.NumericColumnType;
import records.data.type.TextColumnType;
import records.importers.GuessFormat;
import records.importers.ColumnInfo;
import records.importers.TextFormat;
import utility.Utility;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Created by neil on 28/10/2016.
 */
public class TestFormat
{
    private static final ColumnType NUM = new NumericColumnType("");
    private static final ColumnType TEXT = new TextColumnType();
    @Test
    public void testFormat()
    {
        assertFormatCR(new TextFormat(1, c(new ColumnInfo(NUM, "A"), new ColumnInfo(NUM, "B")), ','),
            "A,B", "0,0", "1,1", "2,2");
        assertFormatCR(new TextFormat(2, c(new ColumnInfo(NUM, "A"), new ColumnInfo(NUM, "B")), ','),
            "# Some comment", "A,B", "0,0", "1,1", "2,2");
        assertFormatCR(new TextFormat(3, c(new ColumnInfo(NUM, "A"), new ColumnInfo(NUM, "B")), ','),
            "# Some comment", "A,B", "===", "0,0", "1,1", "2,2");
        assertFormatCR(new TextFormat(0, c(new ColumnInfo(NUM, ""), new ColumnInfo(NUM, "")), ','),
            "0,0", "1,1", "2,2");

        assertFormatCR(new TextFormat(0, c(new ColumnInfo(TEXT, ""), new ColumnInfo(TEXT, "")), ','),
            "A,B", "0,0", "1,1", "C,D", "2,2");
        assertFormatCR(new TextFormat(1, c(new ColumnInfo(NUM, "A"), new ColumnInfo(TEXT, "B")), ','),
            "A,B", "0,0", "1,1", "1.5,D", "2,2");

        //#error TODO add support for date columns
    }
    @Test
    public void testCurrency()
    {
        assertFormat(new TextFormat(0, c(new ColumnInfo(NUM("$"), ""), new ColumnInfo(TEXT, "")), ','),
            "$0, A", "$1, Whatever", "$2, C");
        assertFormat(new TextFormat(0, c(new ColumnInfo(NUM("£"), ""), new ColumnInfo(TEXT, "")), ','),
            "£ 0, A", "£ 1, Whatever", "£ 2, C");
        assertFormat(new TextFormat(0, c(new ColumnInfo(TEXT, ""), new ColumnInfo(TEXT, "")), ','),
            "A0, A", "A1, Whatever", "A2, C");
    }

    private static void assertFormatCR(TextFormat fmt, String... lines)
    {
        assertFormat(fmt, lines);
        for (char sep : ";\t :".toCharArray())
        {
            fmt.separator = sep;
            assertFormat(fmt, Utility.mapArray(String.class, lines, l -> l.replace(',', sep)));
        }
    }

    private static void assertFormat(TextFormat fmt, String... lines)
    {
        assertEquals(fmt, GuessFormat.guessTextFormat(java.util.Arrays.asList(lines)));
    }

    private static List<ColumnInfo> c(ColumnInfo... ts)
    {
        return Arrays.asList(ts);
    }

    private static NumericColumnType NUM(String displayPrefix)
    {
        return new NumericColumnType(displayPrefix);
    }
}
