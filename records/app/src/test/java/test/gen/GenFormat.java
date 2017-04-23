package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.ColumnId;
import records.data.columntype.BlankColumnType;
import records.data.columntype.ColumnType;
import records.data.columntype.CleanDateColumnType;
import records.data.columntype.NumericColumnType;
import records.data.columntype.TextColumnType;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.importers.ColumnInfo;
import records.importers.TextFormat;
import records.transformations.function.ToDate;
import test.DummyManager;
import test.TestUtil;
import utility.Utility;

import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by neil on 29/10/2016.
 */
public class GenFormat extends Generator<TextFormat>
{
    public static List<Charset> CHARSETS = Utility.mapList(Arrays.asList("ISO-8859-1", "UTF-8", "UTF-16"), Charset::forName);
    public static List<Character> seps = Arrays.asList(',', ';', '\t', ':');
    public static List<Unit> currencies;
    static {
        try
        {
            currencies = Arrays.asList(
                DummyManager.INSTANCE.getUnitManager().loadUse("$"),
                DummyManager.INSTANCE.getUnitManager().loadUse("£"),
                DummyManager.INSTANCE.getUnitManager().loadUse("€")
            );
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    public GenFormat()
    {
        super(TextFormat.class);
    }

    @Override
    @SuppressWarnings("initialization")
    public TextFormat generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        String sep = "" + sourceOfRandomness.choose(seps);
        boolean hasTitle = true; //sourceOfRandomness.nextBoolean();
        int garbageBeforeTitle = 0; //sourceOfRandomness.nextInt(0, 10);
        int garbageAfterTitle = 0; //sourceOfRandomness.nextInt(0, 5);
        List<ColumnInfo> columns = new ArrayList<>();
        int columnCount = sourceOfRandomness.nextInt(2, 40);
        for (int i = 0; i < columnCount; i++)
        {
            List<DateTimeFormatter> dateFormats = ToDate.FORMATS.stream().flatMap(List::stream).collect(Collectors.toList());
            ColumnType type = sourceOfRandomness.choose(Arrays.asList(
                ColumnType.BLANK,
                new TextColumnType(),
                new NumericColumnType(sourceOfRandomness.nextBoolean() ? Unit.SCALAR : sourceOfRandomness.choose(currencies), sourceOfRandomness.nextInt(0, 6), null),
                new CleanDateColumnType(sourceOfRandomness.choose(dateFormats), LocalDate::from)));
                //TODO tag?, boolean
            // Don't end with blank:
            if (i == columnCount - 1 && (type instanceof BlankColumnType || columns.stream().allMatch(GenFormat::canBeBlank)))
                type = new TextColumnType();
            // Don't let all be text/blank:
            if (i == columnCount - 1 && columns.stream().allMatch(c -> c.type instanceof TextColumnType || c.type instanceof BlankColumnType))
                type = new CleanDateColumnType(sourceOfRandomness.choose(dateFormats), LocalDate::from);
            String title = hasTitle ? "GenCol" + i : "";
            columns.add(new ColumnInfo(type, new ColumnId(title)));
        }
        TextFormat format = new TextFormat(garbageBeforeTitle + garbageAfterTitle + (hasTitle ? 1 : 0), columns, sep, sourceOfRandomness.choose(CHARSETS));
        return format;
    }

    @Override
    public List<TextFormat> doShrink(SourceOfRandomness random, TextFormat larger)
    {
        ArrayList<TextFormat> r = new ArrayList<>();
        for (int i = 0; i < larger.columnTypes.size(); i++)
        {
            List<ColumnInfo> reducedCols = new ArrayList<>(larger.columnTypes);
            if (i == larger.columnTypes.size() - 1 && i >= 1 && reducedCols.get(i - 1).type instanceof BlankColumnType)
                continue; // Don't remove last one if one before is blank
            reducedCols.remove(i);
            // Don't let them all be blank or all text/blank:
            if (reducedCols.stream().allMatch(GenFormat::canBeBlank) || reducedCols.stream().allMatch(c -> c.type instanceof TextColumnType || c.type instanceof BlankColumnType))
                continue;
            TextFormat smaller = new TextFormat(larger.headerRows, reducedCols, larger.separator, Charset.forName("UTF-8"));
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
