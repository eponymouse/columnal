package test;

import org.junit.Test;
import records.data.ColumnId;
import records.data.columntype.ColumnType;
import records.data.columntype.NumericColumnType;
import records.data.columntype.TextColumnType;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.importers.GuessFormat;
import records.importers.ColumnInfo;
import records.importers.GuessFormat.ColumnCountChoice;
import records.importers.GuessFormat.HeaderRowChoice;
import records.importers.GuessFormat.SeparatorChoice;
import records.importers.TextFormat;
import test.TestUtil.ChoicePick;
import utility.Utility;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Created by neil on 28/10/2016.
 */
public class TestFormat
{
    private static final ColumnType NUM = new NumericColumnType(Unit.SCALAR, 0, null);
    private static final ColumnType TEXT = new TextColumnType();
    
    private static ColumnInfo col(ColumnType type, String name)
    {
        return new ColumnInfo(type, new ColumnId(name));
    }
    
    @Test
    public void testFormat() throws UserException, InternalException
    {
        assertFormatCR(new TextFormat(1, c(col(NUM, "A"), col(NUM, "B")), ","),
            "A,B", "0,0", "1,1", "2,2");
        assertFormatCR(new TextFormat(2, c(col(NUM, "A"), col(NUM, "B")), ","),
            "# Some comment", "A,B", "0,0", "1,1", "2,2");
        assertFormatCR(new TextFormat(3, c(col(NUM, "A"), col(NUM, "B")), ","),
            "# Some comment", "A,B", "===", "0,0", "1,1", "2,2");
        assertFormatCR(new TextFormat(0, c(col(NUM, "C1"), col(NUM, "C2")), ","),
            "0,0", "1,1", "2,2");

        assertFormatCR(new TextFormat(0, c(col(TEXT, "C1"), col(TEXT, "C2")), ","),
            "A,B", "0,0", "1,1", "C,D", "2,2");
        assertFormatCR(new TextFormat(1, c(col(NUM, "A"), col(TEXT, "B")), ","),
            "A,B", "0,0", "1,1", "1.5,D", "2,2");

        //#error TODO add support for date columns
    }
    @Test
    public void testCurrency() throws InternalException, UserException
    {
        assertFormat(new TextFormat(0, c(col(NUM("$"), "C1"), col(TEXT, "C2")), ","),
            "$0, A", "$1, Whatever", "$2, C");
        assertFormat(new TextFormat(0, c(col(NUM("£"), "C1"), col(TEXT, "C2")), ","),
            "£ 0, A", "£ 1, Whatever", "£ 2, C");
        assertFormat(new TextFormat(0, c(col(TEXT, "C1"), col(TEXT, "C2")), ","),
            "A0, A", "A1, Whatever", "A2, C");
    }

    private static void assertFormatCR(TextFormat fmt, String... lines) throws InternalException, UserException
    {
        assertFormat(fmt, lines);
        for (char sep : ";\t :".toCharArray())
        {
            fmt.separator = "" + sep;
            assertFormat(fmt, Utility.mapArray(String.class, lines, l -> l.replace(',', sep)));
        }
    }

    private static void assertFormat(TextFormat fmt, String... lines) throws UserException, InternalException
    {
        ChoicePick[] picks = new ChoicePick[] {
            new ChoicePick<HeaderRowChoice>(HeaderRowChoice.class, new HeaderRowChoice(fmt.headerRows)),
            new ChoicePick<SeparatorChoice>(SeparatorChoice.class, new SeparatorChoice("" + fmt.separator)),
            new ChoicePick<ColumnCountChoice>(ColumnCountChoice.class, new ColumnCountChoice(fmt.columnTypes.size()))
        };
        assertEquals(fmt, TestUtil.pick(GuessFormat.guessTextFormat(DummyManager.INSTANCE.getUnitManager(), java.util.Arrays.asList(lines)), picks));
    }

    private static List<ColumnInfo> c(ColumnInfo... ts)
    {
        return Arrays.asList(ts);
    }

    private static NumericColumnType NUM(String unit)
    {
        try
        {
            return new NumericColumnType(DummyManager.INSTANCE.getUnitManager().loadUse(unit), 0, null);
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }
}
