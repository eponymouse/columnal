import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.type.ColumnType;
import records.data.type.CleanDateColumnType;
import records.data.type.NumericColumnType;
import records.data.type.TextColumnType;
import utility.Import.ColumnInfo;
import utility.Import.TextFormat;

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
        int columnCount = sourceOfRandomness.nextInt(1, 40);
        for (int i = 0; i < columnCount; i++)
        {
            ColumnType type = sourceOfRandomness.choose(Arrays.asList(
                ColumnType.BLANK,
                new TextColumnType(),
                new NumericColumnType(sourceOfRandomness.nextBoolean() ? "" : sourceOfRandomness.choose(currencies)),
                new CleanDateColumnType(sourceOfRandomness.choose(dateFormats))));
            // Don't end with blank:
            if (i == columnCount - 1 && type.isBlank())
                type = new TextColumnType();
            String title = hasTitle ? "C" + i : "";
            columns.add(new ColumnInfo(type, title));
        }
        TextFormat format = new TextFormat(garbageBeforeTitle + garbageAfterTitle + (hasTitle ? 1 : 0), columns, sep);
        return format;
    }
}
