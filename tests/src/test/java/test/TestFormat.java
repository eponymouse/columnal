/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package test;

import annotation.identifier.qual.ExpressionIdentifier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.embed.swing.JFXPanel;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.BeforeClass;
import org.junit.Test;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.columntype.BoolColumnType;
import xyz.columnal.data.columntype.CleanDateColumnType;
import xyz.columnal.data.columntype.ColumnType;
import xyz.columnal.data.columntype.NumericColumnType;
import xyz.columnal.data.columntype.TextColumnType;
import xyz.columnal.data.datatype.DataType.DateTimeInfo.DateTimeType;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.importers.GuessFormat;
import xyz.columnal.importers.ColumnInfo;
import xyz.columnal.importers.GuessFormat.FinalTextFormat;
import test.gen.GenFormat;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Utility;

import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static xyz.columnal.data.datatype.DataType.DateTimeInfo.F.*;
import static xyz.columnal.data.datatype.DataType.DateTimeInfo.m;

/**
 * Created by neil on 28/10/2016.
 */
public class TestFormat
{
    private static final ColumnType NUM = new NumericColumnType(Unit.SCALAR, 0, null, null);
    private static final ColumnType TEXT = new TextColumnType();
    private static final ColumnType BOOL = new BoolColumnType("true", "false");
    private static final ColumnType DATE = new CleanDateColumnType(DateTimeType.YEARMONTHDAY, true, m(" ", DAY, MONTH_NUM, YEAR2), LocalDate::from);
    private static final ColumnType TIME = new CleanDateColumnType(DateTimeType.TIMEOFDAY, false, m(":", HOUR, MIN, SEC_OPT_FRAC_OPT), LocalTime::from);
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private static ColumnInfo col(ColumnType type, @ExpressionIdentifier String name)
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
        assertFormatCR(GenFormat.f(0, c(col(NUM, "C 1"), col(NUM, "C 2")), ",", "", UTF8),
            "0,0", "1,1", "2,2");

        assertFormatCR(GenFormat.f(0, c(col(TEXT, "C 1"), col(TEXT, "C 2")), ",", "", UTF8),
            "A,B", "0,0", "3,5", "1,D", "C,2", "2,2", "E,F", "G,H", "I,J", "2,2", "3,3", "4,4", "5,5", "6,6", "7,7");
        assertFormatCR(GenFormat.f(1, c(col(NUM, "A"), col(TEXT, "B")), ",", "", UTF8),
            "A,B", "0,0", "1,1", "1.5,D", "2,2", "3,E", "4,5");

        assertFormat(GenFormat.f(1, c(col(DATE, "Date"), col(TIME, "Time"), col(BOOL, "Bool"), col(TEXT, "Text")), ",", "", UTF8),
            "Date, Time, Bool, Text", "3/5/18, 16:45, TRUE, Whatever", "21/6/17, 00:01, FALSE, Something");
    }

    @Test
    @OnThread(Tag.Simulation)
    public void testCurrency() throws InternalException, UserException, InterruptedException, ExecutionException, TimeoutException
    {
        //assertFormat(GenFormat.f(0, c(col(NUM("USD", "$"), "C1"), col(TEXT, "C2")), ",", "", UTF8),
            //"$0, A", "$1, Whatever", "$2, C");
        assertFormat(GenFormat.f(0, c(col(NUM("EUR", "€"), "C 1"), col(TEXT, "C 2")), ",", "", UTF8),
            "€ 0, A", "€ 1, Whatever", "€ 2, C");
        assertFormat(GenFormat.f(0, c(col(TEXT, "C 1"), col(TEXT, "C 2")), ",", "", UTF8),
            "A0, A", "A1, Whatever", "A2, C");
    }

    @OnThread(Tag.Simulation)
    private static void assertFormatCR(FinalTextFormat fmt, String... lines) throws InternalException, UserException, InterruptedException, ExecutionException, TimeoutException
    {
        assertFormat(fmt, lines);
        for (char sep : ";\t |".toCharArray())
        {
            fmt = GenFormat.f(fmt.trimChoice.trimFromTop, fmt.columnTypes, "" + sep, fmt.initialTextFormat.quote, fmt.initialTextFormat.charset);
            assertFormat(fmt, Utility.mapArray(String.class, lines, l -> l.replace(',', sep)));
        }
    }

    @SuppressWarnings("unchecked")
    @OnThread(Tag.Simulation)
    private static void assertFormat(FinalTextFormat fmt, String... lines) throws UserException, InternalException, InterruptedException, ExecutionException, TimeoutException
    {
        FinalTextFormat actual = GuessFormat.guessTextFormat(DummyManager.make().getTypeManager(), DummyManager.make().getUnitManager(), ImmutableMap.of(Charset.forName("UTF-8"), Arrays.asList(lines)), null, null)._test_getResultNoGUI();
        assertEquals(fmt.initialTextFormat, actual.initialTextFormat);
        // We only care about top.  If they have to trim more from sides due to header, that is fine:
        assertEquals(fmt.toString(), fmt.trimChoice.trimFromTop, actual.trimChoice.trimFromTop);
        assertEquals(fmt.columnTypes, actual.columnTypes);
    }

    private static ImmutableList<ColumnInfo> c(ColumnInfo... ts)
    {
        return ImmutableList.copyOf(ts);
    }

    private static NumericColumnType NUM(String unit, @Nullable String commonPrefix)
    {
        try
        {
            return new NumericColumnType(DummyManager.make().getUnitManager().loadUse(unit), 0, commonPrefix, null);
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }
}
