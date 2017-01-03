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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
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
        int rowCount = r.nextInt(50, 200);
        for (int row = 0; row < 100; row++)
        {
            List<List<Object>> data = new ArrayList<>();
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
                    line.append(r.nextBoolean() ? "" : numericColumnType.unit.getDisplayPrefix());
                    long value = r.nextLong();

                    line.append(String.format(format.separator == ',' ? "%d" : "%,d", value));
                    if (numericColumnType.minDP > 0)
                    {
                        String decimalDigs = String.format("%0" + numericColumnType.minDP + "d", Math.abs(r.nextInt())).substring(0, numericColumnType.minDP);
                        line.append("." + decimalDigs);
                        data.add(Collections.<Object>singletonList(new BigDecimal(Long.toString(value) + "." + decimalDigs)));
                    }
                    else
                        data.add(Collections.<Object>singletonList((Long)value));
                    line.append(r.nextBoolean() ? "" : numericColumnType.unit.getDisplaySuffix());
                }
                else if (c.type.isText())
                {
                    String str = TestUtil.makeString(r, generationStatus).replace("\n", "").replace("\r", "");
                    // TODO quote separators instead of removing them:
                    str = str.replace("" + format.separator, "");
                    data.add(Collections.singletonList(str));
                    line.append(str);
                }
                else if (c.type.isDate())
                {
                    CleanDateColumnType dateColumnType = (CleanDateColumnType) c.type;
                    int year;
                    if (dateColumnType.isShortYear())
                        year = 1950 + r.nextInt(84); // Might need to adjust this in 2030
                    else
                        year = 1900 + r.nextInt(199);
                    int month = 1 + r.nextInt(12);
                    int day = 1 + r.nextInt(28);
                    LocalDate date = LocalDate.of(year, month, day);
                    data.add(Collections.singletonList(date));

                    line.append(date.format(dateColumnType.getDateTimeFormatter()));
                }
                else if (c.type.isBlank())
                {
                    //data.add(Collections.emptyList());
                }
                else
                    throw new UnsupportedOperationException("Missing case for column columntype? " + c.type.getClass());
                if (i < columnTypes.size() - 1)
                    line.append(format.separator);
            }

            String lineString = line.toString();
            // Don't add all-blank rows because they weren't intentional and it can screw up guess:
            if (!lineString.replace("" + format.separator, "").isEmpty())
            {
                fileContent.add(lineString);
                intendedContent.add(data);
            }
        }

        if (r.nextBoolean())
            fileContent.add(""); // Add trailing newline

        return new FormatAndData(format, fileContent, intendedContent);
    }

}
