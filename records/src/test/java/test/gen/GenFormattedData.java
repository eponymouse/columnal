package test.gen;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.columntype.CleanDateColumnType;
import records.data.columntype.NumericColumnType;
import records.importers.ColumnInfo;
import records.importers.TextFormat;
import test.TestUtil;
import test.gen.GenFormattedData.FormatAndData;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by neil on 23/12/2016.
 */
public class GenFormattedData extends Generator<FormatAndData>
{
    public GenFormattedData()
    {
        super(FormatAndData.class);
    }

    public static class FormatAndData
    {
        public final TextFormat format;
        public final List<String> content;
        public final List<List<List<Object>>> loadedContent;

        public FormatAndData(TextFormat format, List<String> content, List<List<List<Object>>> loadedContent)
        {
            this.format = format;
            this.content = content;
            this.loadedContent = loadedContent;
        }
    }

    @Override
    public FormatAndData generate(SourceOfRandomness r, GenerationStatus generationStatus)
    {
        List<String> fileContent = new ArrayList<>();
        List<List<List<Object>>> intendedContent = new ArrayList<>();
        TextFormat format = new GenFormat().generate(r, generationStatus);

        fileContent.add(format.columnTypes.stream().map(c -> c.title.getOutput()).collect(Collectors.joining("" + format.separator)));
        for (int row = 0; row < 100; row++)
        {
            StringBuilder line = new StringBuilder();
            List<ColumnInfo> columnTypes = format.columnTypes;
            for (int i = 0; i < columnTypes.size(); i++)
            {
                // TODO generate X-or-blank column types
                ColumnInfo c = columnTypes.get(i);
                // TODO add random spaces, randomise content using generators
                if (c.type.isNumeric())
                {
                    NumericColumnType numericColumnType = (NumericColumnType) c.type;
                    line.append(r.nextBoolean() ? "" : numericColumnType.unit.getDisplayPrefix()).append(String.format(format.separator == ',' ? "%d" : "%,d", r.nextLong()));
                    if (numericColumnType.minDP > 0)
                    {
                        line.append("." + String.format("%0" + numericColumnType.minDP + "d", Math.abs(r.nextInt())).substring(0, numericColumnType.minDP));
                    }
                    line.append(r.nextBoolean() ? "" : numericColumnType.unit.getDisplaySuffix());
                } else if (c.type.isText())
                    line.append(TestUtil.makeString(r, generationStatus));
                else if (c.type.isDate())
                {
                    int year = 1900 + r.nextInt(199);
                    int month = 1 + r.nextInt(12);
                    int day = 1 + r.nextInt(28);
                    LocalDate date = LocalDate.of(year, month, day);
                    line.append(date.format(((CleanDateColumnType) c.type).getDateTimeFormatter()));
                } else if (!c.type.isBlank())
                    throw new UnsupportedOperationException("Missing case for column columntype? " + c.type.getClass());
                if (i < columnTypes.size() - 1)
                    line.append(format.separator);
            }

            String lineString = line.toString();
            // Don't add all-blank rows because they weren't intentional and it can screw up guess:
            if (!lineString.replace("" + format.separator, "").isEmpty())
                fileContent.add(lineString);
        }

        return new FormatAndData(format, fileContent, intendedContent);
    }

}
