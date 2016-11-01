import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.runner.RunWith;
import records.data.type.CleanDateColumnType;
import records.data.type.NumericColumnType;
import records.importers.GuessFormat;
import records.importers.ColumnInfo;
import records.importers.TextFormat;

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
    public void testGuessFormat(@From(GenFormat.class) TextFormat format) throws IOException
    {
        List<String> fileContent = new ArrayList<>();
        fileContent.add(format.columnTypes.stream().map(c -> c.title).collect(Collectors.joining("" + format.separator)));
        Random rnd = new Random();
        for (int row = 0; row < 100; row++)
        {
            StringBuilder line = new StringBuilder();
            for (ColumnInfo c : format.columnTypes)
            {
                // TODO add random spaces, randomise content
                if (c.type.isNumeric())
                {
                    NumericColumnType numericColumnType = (NumericColumnType) c.type;
                    if (numericColumnType.mayBeBlank && rnd.nextBoolean())
                    {
                        line.append(numericColumnType.displayPrefix).append("");
                    }
                    else
                    {
                        line.append(numericColumnType.displayPrefix).append(0);
                    }
                }
                else if (c.type.isText())
                    line.append("s");
                else if (c.type.isDate())
                {
                    int year = 1900 + rnd.nextInt(199);
                    int month = 1 + rnd.nextInt(12);
                    int day = 1 + rnd.nextInt(28); // TODO have dates with 30th, etc
                    LocalDate date = LocalDate.of(year, month, day);
                    line.append(date.format(((CleanDateColumnType)c.type).getDateTimeFormatter()));
                }
                else if (!c.type.isBlank())
                    throw new UnsupportedOperationException("Missing case for column type? " + c.type.getClass());
                line.append(format.separator);
            }
            fileContent.add(line.toString());
        }
        PropFiles.assertEqualsMsg(format, GuessFormat.guessTextFormat(fileContent));
    }
}
