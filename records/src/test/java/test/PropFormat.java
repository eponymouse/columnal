package test;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.runner.RunWith;
import records.data.columntype.CleanDateColumnType;
import records.data.columntype.NumericColumnType;
import records.importers.GuessFormat;
import records.importers.ColumnInfo;
import records.importers.TextFormat;
import test.gen.GenFormat;
import test.gen.GenRandom;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * Created by neil on 29/10/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropFormat
{

    @Property
    public void testGuessFormat(@From(GenFormat.class) TextFormat format, @From(GenRandom.class) Random rnd) throws IOException
    {
        List<String> fileContent = new ArrayList<>();
        fileContent.add(format.columnTypes.stream().map(c -> c.title.getOutput()).collect(Collectors.joining("" + format.separator)));
        for (int row = 0; row < 100; row++)
        {
            StringBuilder line = new StringBuilder();
            List<ColumnInfo> columnTypes = format.columnTypes;
            for (int i = 0; i < columnTypes.size(); i++)
            {
                ColumnInfo c = columnTypes.get(i);
                // TODO add random spaces, randomise content using generators
                if (c.type.isNumeric())
                {
                    NumericColumnType numericColumnType = (NumericColumnType) c.type;
                    // TODO
                    //if (numericColumnType.mayBeBlank && row > 0 && (row == 10 || rnd.nextBoolean())) // Make sure to put at least one blank in, but don't put blank at top
                    //{
                        //line.append("");
                    //} else
                    {
                        line.append(numericColumnType.unit.getDisplayPrefix()).append(String.format(format.separator == ',' ? "%d" : "%,d", rnd.nextLong()));
                        if (numericColumnType.minDP > 0)
                        {
                            line.append("." + String.format("%0" + numericColumnType.minDP + "d", Math.abs(rnd.nextInt())).substring(0, numericColumnType.minDP));
                        }
                    }
                } else if (c.type.isText())
                    line.append("s");
                else if (c.type.isDate())
                {
                    int year = 1900 + rnd.nextInt(199);
                    int month = 1 + rnd.nextInt(12);
                    int day = 1 + rnd.nextInt(28);
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
        assertEquals("Failure with content: " + fileContent.stream().collect(Collectors.joining("\n")), format, GuessFormat.guessTextFormat(DummyManager.INSTANCE.getUnitManager(), fileContent));
    }
}
