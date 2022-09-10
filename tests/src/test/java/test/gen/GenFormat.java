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

package test.gen;

import annotation.identifier.qual.ExpressionIdentifier;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.columntype.BlankColumnType;
import xyz.columnal.data.columntype.ColumnType;
import xyz.columnal.data.columntype.CleanDateColumnType;
import xyz.columnal.data.columntype.NumericColumnType;
import xyz.columnal.data.columntype.TextColumnType;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.DateTimeInfo.DateTimeType;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.importers.ColumnInfo;
import xyz.columnal.importers.GuessFormat.FinalTextFormat;
import xyz.columnal.importers.GuessFormat.InitialTextFormat;
import xyz.columnal.importers.GuessFormat.TrimChoice;
import test.DummyManager;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.Utility;

import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Created by neil on 29/10/2016.
 */
public class GenFormat extends Generator<FinalTextFormat>
{
    public static List<Charset> CHARSETS = Utility.mapList(Arrays.asList("ISO-8859-1", "UTF-8", "UTF-16"), Charset::forName);
    public static List<Character> seps = Arrays.asList(',', ';', '\t', '|');
    public static List<Unit> currencies;
    static {
        try
        {
            // Only EUR has reliable one-to-one connection with currency symbol:
            currencies = Arrays.asList(
                DummyManager.make().getUnitManager().loadUse("EUR")
            );
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    public GenFormat()
    {
        super(FinalTextFormat.class);
    }

    public static FinalTextFormat f(int headerRows, ImmutableList<ColumnInfo> columns, @Nullable String sep, @Nullable String quote, Charset charset)
    {
        return new FinalTextFormat(new InitialTextFormat(charset, sep, quote), new TrimChoice(headerRows, 0, 0, 0), columns);
    }

    @Override
    public FinalTextFormat generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        String sep = "" + sourceOfRandomness.choose(seps);
        boolean hasTitle = true; //sourceOfRandomness.nextBoolean();
        int garbageBeforeTitle = 0; //sourceOfRandomness.nextInt(0, 10);
        int garbageAfterTitle = 0; //sourceOfRandomness.nextInt(0, 5);
        List<ColumnInfo> columns = new ArrayList<>();
        int columnCount = sourceOfRandomness.nextInt(2, 15);
        for (int i = 0; i < columnCount; i++)
        {
            List<DateTimeFormatter> dateFormats = new DateTimeInfo(DateTimeType.YEARMONTHDAY).getFlexibleFormatters().stream().flatMap(l -> l.stream()).collect(Collectors.toList());
            ColumnType type = sourceOfRandomness.choose(Arrays.asList(
                ColumnType.BLANK,
                new TextColumnType(),
                sourceOfRandomness.nextInt(0, 10) < 7 ?
                new NumericColumnType(Unit.SCALAR, sourceOfRandomness.nextInt(0, 6), null, null) :
                ((Supplier<ColumnType>)() -> {
                    Unit curr = sourceOfRandomness.choose(currencies);
                    String displayPrefix = curr.getDisplayPrefix();
                    String displaySuffix = curr.getDisplaySuffix();
                    // We use null if empty:
                    if (displayPrefix.isEmpty())
                        displayPrefix = null;
                    if (displaySuffix.isEmpty())
                        displaySuffix = null;
                    return new NumericColumnType(curr, sourceOfRandomness.nextInt(0, 6), displayPrefix, displaySuffix);}).get(),
                new CleanDateColumnType(DateTimeType.YEARMONTHDAY, true, sourceOfRandomness.choose(dateFormats), LocalDate::from)));
                //TODO tag?, boolean
            // Don't end with blank:
            if (i == columnCount - 1 && (type instanceof BlankColumnType || columns.stream().allMatch(GenFormat::canBeBlank)))
                type = new TextColumnType();
            // Don't let all be text/blank:
            if (i == columnCount - 1 && columns.stream().allMatch(c -> c.type instanceof TextColumnType || c.type instanceof BlankColumnType))
                type = new CleanDateColumnType(DateTimeType.YEARMONTHDAY, true, sourceOfRandomness.choose(dateFormats), LocalDate::from);
            @ExpressionIdentifier String title = IdentifierUtility.identNum(hasTitle ? "GenCol" : "Unspec", i);
            columns.add(new ColumnInfo(type, new ColumnId(title)));
        }
        // Don't pick a charset which can't represent the currency signs:
        List<Charset> possibleCharsets = CHARSETS.stream().filter(charset -> columns.stream().allMatch(ci -> ci.type instanceof NumericColumnType ?
            charset.newEncoder().canEncode(((NumericColumnType) ci.type).unit.getDisplayPrefix()) : true)
        ).collect(Collectors.toList());

        return f(garbageBeforeTitle + garbageAfterTitle + (hasTitle ? 1 : 0), ImmutableList.copyOf(columns), sep, ""/*TODO */, sourceOfRandomness.choose(possibleCharsets));
    }

    @Override
    public List<FinalTextFormat> doShrink(SourceOfRandomness random, FinalTextFormat larger)
    {
        ArrayList<FinalTextFormat> r = new ArrayList<>();
        for (int i = 0; i < larger.columnTypes.size(); i++)
        {
            List<ColumnInfo> reducedCols = new ArrayList<>(larger.columnTypes);
            if (i == larger.columnTypes.size() - 1 && i >= 1 && reducedCols.get(i - 1).type instanceof BlankColumnType)
                continue; // Don't remove last one if one before is blank
            reducedCols.remove(i);
            // Don't let them all be blank or all text/blank:
            if (reducedCols.stream().allMatch(GenFormat::canBeBlank) || reducedCols.stream().allMatch(c -> c.type instanceof TextColumnType || c.type instanceof BlankColumnType))
                continue;
            FinalTextFormat smaller = f(larger.trimChoice.trimFromTop, ImmutableList.copyOf(reducedCols), larger.initialTextFormat.separator, larger.initialTextFormat.quote, Charset.forName("UTF-8"));
            if (reducedCols.size() >= 2) // TODO allow one column
                r.add(smaller);
        }
        return r;
    }

    private static boolean canBeBlank(ColumnInfo columnInfo)
    {
        return columnInfo.type instanceof BlankColumnType;
    }
}
