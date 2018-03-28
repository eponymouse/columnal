package test;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
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
import records.importers.GuessFormat.FinalTextFormat;
import records.importers.GuessFormat.TrimChoice;
import utility.Utility;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Created by neil on 28/10/2016.
 */
public class TestFormat
{
    private static final ColumnType NUM = new NumericColumnType(Unit.SCALAR, 0, null, null);
    private static final ColumnType TEXT = new TextColumnType();
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private static ColumnInfo col(ColumnType type, String name)
    {
        return new ColumnInfo(type, new ColumnId(name));
    }
    
    @Test
    public void testFormat() throws UserException, InternalException
    {
        assertFormatCR(new FinalTextFormat(1, c(col(NUM, "A"), col(NUM, "B")), ",", null, UTF8),
            "A,B", "0,0", "1,1", "2,2");
        assertFormatCR(new FinalTextFormat(2, c(col(NUM, "A"), col(NUM, "B")), ",", null, UTF8),
            "# Some comment", "A,B", "0,0", "1,1", "2,2");
        assertFormatCR(new FinalTextFormat(0, c(col(NUM, "C1"), col(NUM, "C2")), ",", null, UTF8),
            "0,0", "1,1", "2,2");

        assertFormatCR(new FinalTextFormat(0, c(col(TEXT, "C1"), col(TEXT, "C2")), ",", null, UTF8),
            "A,B", "0,0", "1,1", "C,D", "2,2");
        assertFormatCR(new FinalTextFormat(1, c(col(NUM, "A"), col(TEXT, "B")), ",", null, UTF8),
            "A,B", "0,0", "1,1", "1.5,D", "2,2", "3,E");

        //#error TODO add support for date columns
    }
    @Test
    public void testCurrency() throws InternalException, UserException
    {
        assertFormat(new FinalTextFormat(0, c(col(NUM("$", "$"), "C1"), col(TEXT, "C2")), ",", null, UTF8),
            "$0, A", "$1, Whatever", "$2, C");
        assertFormat(new FinalTextFormat(0, c(col(NUM("£", "£"), "C1"), col(TEXT, "C2")), ",", null, UTF8),
            "£ 0, A", "£ 1, Whatever", "£ 2, C");
        assertFormat(new FinalTextFormat(0, c(col(TEXT, "C1"), col(TEXT, "C2")), ",", null, UTF8),
            "A0, A", "A1, Whatever", "A2, C");
    }

    private static void assertFormatCR(FinalTextFormat fmt, String... lines) throws InternalException, UserException
    {
        assertFormat(fmt, lines);
        for (char sep : ";\t :".toCharArray())
        {
            fmt = fmt.withSeparator("" + sep);
            assertFormat(fmt, Utility.mapArray(String.class, lines, l -> l.replace(',', sep)));
        }
    }

    @SuppressWarnings("unchecked")
    private static void assertFormat(FinalTextFormat fmt, String... lines) throws UserException, InternalException
    {
        ChoicePick[] picks = new ChoicePick[] {
            new ChoicePick<CharsetChoice>(CharsetChoice.class, new CharsetChoice(Charset.forName("UTF-8"))),
            new ChoicePick<TrimChoice>(TrimChoice.class, fmt.trimChoice),
            new ChoicePick<SeparatorChoice>(SeparatorChoice.class, new SeparatorChoice("" + fmt.separator)),
            new ChoicePick<QuoteChoice>(QuoteChoice.class, new QuoteChoice(null)),
            new ChoicePick<ColumnCountChoice>(ColumnCountChoice.class, new ColumnCountChoice(fmt.columnTypes.size()))
        };
        assertEquals(fmt, TestUtil.pick(GuessFormat.guessFinalTextFormat(DummyManager.INSTANCE.getUnitManager(), Collections.singletonMap(Charset.forName("UTF-8"), Arrays.asList(lines))), picks));
    }

    private static ImmutableList<ColumnInfo> c(ColumnInfo... ts)
    {
        return ImmutableList.copyOf(ts);
    }

    private static NumericColumnType NUM(String unit, @Nullable String commonPrefix)
    {
        try
        {
            return new NumericColumnType(DummyManager.INSTANCE.getUnitManager().loadUse(unit), 0, commonPrefix, null);
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }
}
