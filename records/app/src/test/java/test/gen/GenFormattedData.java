package test.gen;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import records.data.columntype.BlankColumnType;
import records.data.columntype.CleanDateColumnType;
import records.data.columntype.NumericColumnType;
import records.data.columntype.TextColumnType;
import records.data.datatype.DataTypeUtility;
import records.importers.ColumnInfo;
import records.importers.TextFormat;
import test.TestUtil;
import test.gen.GenFormattedData.FormatAndData;

import java.math.BigDecimal;
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
        // Outermost list is list of rows
        // Next list in is list of columns.
        public final List<List<@Value Object>> loadedContent;

        public FormatAndData(TextFormat format, List<String> content, List<List<@Value Object>> loadedContent)
        {
            this.format = format;
            this.content = content;
            this.loadedContent = loadedContent;
        }

        @Override
        public String toString()
        {
            return "FormatAndData{" +
                "format=" + format +
                /*", content=" + content +
                ", loadedContent=" + loadedContent +*/
                '}';
        }
    }

    @Override
    public FormatAndData generate(SourceOfRandomness r, GenerationStatus generationStatus)
    {
        List<String> fileContent = new ArrayList<>();
        List<List<@Value Object>> intendedContent = new ArrayList<>();
        TextFormat format = new GenFormat().generate(r, generationStatus);

        fileContent.add(format.columnTypes.stream().map(c -> c.title.getOutput()).collect(Collectors.joining("" + format.separator)));
        int rowCount = r.nextInt(50, 200);
        for (int row = 0; row < 100; row++)
        {
            List<@Value Object> data = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            List<ColumnInfo> columnTypes = format.columnTypes;
            for (int i = 0; i < columnTypes.size(); i++)
            {
                // TODO generate X-or-blank column types
                ColumnInfo c = columnTypes.get(i);
                // TODO add random spaces, randomise content using generators
                if (c.type instanceof NumericColumnType)
                {
                    NumericColumnType numericColumnType = (NumericColumnType) c.type;
                    if (!format.charset.newEncoder().canEncode(numericColumnType.unit.getDisplayPrefix()))
                        throw new RuntimeException("Cannot encode prefix: " + numericColumnType.unit.getDisplayPrefix());
                    line.append(r.nextBoolean() ? "" : numericColumnType.unit.getDisplayPrefix());
                    long value = r.nextLong();

                    line.append(String.format(",".equals(format.separator) ? "%d" : "%,d", value));
                    if (numericColumnType.minDP > 0)
                    {
                        String decimalDigs = String.format("%0" + numericColumnType.minDP + "d", Math.abs(r.nextInt())).substring(0, numericColumnType.minDP);
                        line.append("." + decimalDigs);
                        data.add(DataTypeUtility.value(new BigDecimal(Long.toString(value) + "." + decimalDigs)));
                    }
                    else
                        data.add(DataTypeUtility.value((Long)value));
                    line.append(r.nextBoolean() ? "" : numericColumnType.unit.getDisplaySuffix());
                }
                else if (c.type instanceof TextColumnType)
                {
                    String str = TestUtil.makeString(r, generationStatus).replace("\n", "").replace("\r", "");
                    // Get rid of any characters which can't be saved in that encoding:
                    str = str.chars().filter(ch -> format.charset.newEncoder().canEncode((char)ch)).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
                    // TODO quote separators instead of removing them:
                    str = str.replace("" + format.separator, "");
                    data.add(DataTypeUtility.value(str));
                    line.append(str);
                }
                else if (c.type instanceof CleanDateColumnType)
                {
                    CleanDateColumnType dateColumnType = (CleanDateColumnType) c.type;
                    int year;
                    if (dateColumnType.isShortYear())
                        year = 1950 + r.nextInt(84); // Might need to adjust this in 2030
                    else
                        year = 1900 + r.nextInt(199);
                    int month = 1 + r.nextInt(12);
                    int day = 1 + r.nextInt(28);
                    @Value LocalDate date = LocalDate.of(year, month, day);
                    data.add(date);

                    line.append(date.format(dateColumnType.getDateTimeFormatter()));
                }
                else if (c.type instanceof BlankColumnType)
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
