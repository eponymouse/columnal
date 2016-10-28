import org.junit.Test;
import records.data.Column;
import utility.Import;
import utility.Import.ColumnInfo;
import utility.Import.ColumnType;
import utility.Import.TextFormat;
import utility.Utility;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;

/**
 * Created by neil on 28/10/2016.
 */
public class TestFormat
{
    private static final ColumnType NUM = ColumnType.NUMERIC;
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
        assertEquals(fmt, Import.guessTextFormat(java.util.Arrays.asList(lines)));
    }

    private static List<ColumnInfo> c(ColumnInfo... ts)
    {
        return Arrays.asList(ts);
    }
}
