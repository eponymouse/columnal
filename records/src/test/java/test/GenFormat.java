package test;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.ColumnId;
import records.data.columntype.ColumnType;
import records.data.columntype.CleanDateColumnType;
import records.data.columntype.NumericColumnType;
import records.data.columntype.TextColumnType;
import records.importers.ColumnInfo;
import records.importers.TextFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by neil on 29/10/2016.
 */
public class GenFormat extends Generator<TextFormat>
{
    public static List<Character> seps = Arrays.asList(',', ';', '\t', ':');
    public static List<String> currencies = Arrays.asList("$", "£", "€");
    public static List<String> dateFormats = CleanDateColumnType.DATE_FORMATS;

    public GenFormat()
    {
        super(TextFormat.class);
    }

    @Override
    public TextFormat generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus)
    {
        char sep = sourceOfRandomness.choose(seps);
        boolean hasTitle = true; //sourceOfRandomness.nextBoolean();
        int garbageBeforeTitle = 0; //sourceOfRandomness.nextInt(0, 10);
        int garbageAfterTitle = 0; //sourceOfRandomness.nextInt(0, 5);
        List<ColumnInfo> columns = new ArrayList<>();
        int columnCount = sourceOfRandomness.nextInt(2, 40);
        for (int i = 0; i < columnCount; i++)
        {
            ColumnType type = sourceOfRandomness.choose(Arrays.asList(
                ColumnType.BLANK,
                new TextColumnType(),
                new NumericColumnType(sourceOfRandomness.nextBoolean() ? "" : sourceOfRandomness.choose(currencies), sourceOfRandomness.nextInt(0, 6),sourceOfRandomness.nextBoolean()),
                new CleanDateColumnType(sourceOfRandomness.choose(dateFormats), LocalDate::from)));
            // Don't end with blank:
            if (i == columnCount - 1 && (type.isBlank() || columns.stream().allMatch(GenFormat::canBeBlank)))
                type = new TextColumnType();
            // Don't let all be text/blank:
            if (i == columnCount - 1 && columns.stream().allMatch(c -> c.type.isText() || c.type.isBlank()))
                type = new CleanDateColumnType(sourceOfRandomness.choose(dateFormats), LocalDate::from);
            String title = hasTitle ? "GenCol" + i : "";
            columns.add(new ColumnInfo(type, new ColumnId(title)));
        }
        TextFormat format = new TextFormat(garbageBeforeTitle + garbageAfterTitle + (hasTitle ? 1 : 0), columns, sep);
        return format;
    }

    @Override
    public List<TextFormat> doShrink(SourceOfRandomness random, TextFormat larger)
    {
        ArrayList<TextFormat> r = new ArrayList<>();
        for (int i = 0; i < larger.columnTypes.size(); i++)
        {
            List<ColumnInfo> reducedCols = new ArrayList<>(larger.columnTypes);
            if (i == larger.columnTypes.size() - 1 && i >= 1 && reducedCols.get(i - 1).type.isBlank())
                continue; // Don't remove last one if one before is blank
            reducedCols.remove(i);
            // Don't let them all be blank or all text/blank:
            if (reducedCols.stream().allMatch(GenFormat::canBeBlank) || reducedCols.stream().allMatch(c -> c.type.isText() || c.type.isBlank()))
                continue;
            TextFormat smaller = new TextFormat(larger.headerRows, reducedCols, larger.separator);
            if (reducedCols.size() >= 2) // TODO allow one column
                r.add(smaller);
        }
        return r;
    }

    private static boolean canBeBlank(ColumnInfo columnInfo)
    {
        return columnInfo.type.isBlank() || (columnInfo.type.isNumeric() && ((NumericColumnType)columnInfo.type).mayBeBlank);
    }
}
