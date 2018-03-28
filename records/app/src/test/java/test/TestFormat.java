package test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.embed.swing.JFXPanel;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.BeforeClass;
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
import test.gen.GenFormat;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

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
    
    @BeforeClass
    @OnThread(Tag.Swing)
    public static void _initFX()
    {
        new JFXPanel();
    }
    
    @Test
    @OnThread(Tag.Simulation)
    public void testFormat() throws UserException, InternalException, InterruptedException, ExecutionException, TimeoutException
    {
        assertFormatCR(GenFormat.f(1, c(col(NUM, "A"), col(NUM, "B")), ",", "", UTF8),
            "A,B", "0,0", "1,1", "2,2");
        assertFormatCR(GenFormat.f(2, c(col(NUM, "A"), col(NUM, "B")), ",", "", UTF8),
            "# Some comment", "A,B", "0,0", "1,1", "2,2");
        assertFormatCR(GenFormat.f(0, c(col(NUM, "C1"), col(NUM, "C2")), ",", "", UTF8),
            "0,0", "1,1", "2,2");

        assertFormatCR(GenFormat.f(0, c(col(TEXT, "C1"), col(TEXT, "C2")), ",", "", UTF8),
            "A,B", "0,0", "1,1", "C,D", "2,2");
        assertFormatCR(GenFormat.f(1, c(col(NUM, "A"), col(TEXT, "B")), ",", "", UTF8),
            "A,B", "0,0", "1,1", "1.5,D", "2,2", "3,E");

        //#error TODO add support for date columns
    }

    @Test
    @OnThread(Tag.Simulation)
    public void testCurrency() throws InternalException, UserException, InterruptedException, ExecutionException, TimeoutException
    {
        assertFormat(GenFormat.f(0, c(col(NUM("$", "$"), "C1"), col(TEXT, "C2")), ",", "", UTF8),
            "$0, A", "$1, Whatever", "$2, C");
        assertFormat(GenFormat.f(0, c(col(NUM("£", "£"), "C1"), col(TEXT, "C2")), ",", "", UTF8),
            "£ 0, A", "£ 1, Whatever", "£ 2, C");
        assertFormat(GenFormat.f(0, c(col(TEXT, "C1"), col(TEXT, "C2")), ",", "", UTF8),
            "A0, A", "A1, Whatever", "A2, C");
    }

    @OnThread(Tag.Simulation)
    private static void assertFormatCR(FinalTextFormat fmt, String... lines) throws InternalException, UserException, InterruptedException, ExecutionException, TimeoutException
    {
        assertFormat(fmt, lines);
        for (char sep : ";\t :".toCharArray())
        {
            fmt = GenFormat.f(fmt.trimChoice.trimFromTop, fmt.columnTypes, "" + sep, fmt.initialTextFormat.quote, fmt.initialTextFormat.charset);
            assertFormat(fmt, Utility.mapArray(String.class, lines, l -> l.replace(',', sep)));
        }
    }

    @SuppressWarnings("unchecked")
    @OnThread(Tag.Simulation)
    private static void assertFormat(FinalTextFormat fmt, String... lines) throws UserException, InternalException, InterruptedException, ExecutionException, TimeoutException
    {
        assertEquals(fmt, GuessFormat.guessTextFormat(DummyManager.INSTANCE.getTypeManager(), DummyManager.INSTANCE.getUnitManager(), ImmutableMap.of(Charset.forName("UTF-8"), Arrays.asList(lines)))._test_getResultNoGUI());
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
