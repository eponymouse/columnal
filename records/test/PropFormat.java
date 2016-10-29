import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.runner.RunWith;
import utility.Import;
import utility.Import.ColumnInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * Created by neil on 29/10/2016.
 */
@RunWith(JUnitQuickcheck.class)
public class PropFormat
{

    @Property
    public void testGuessFormat(@From(GenFormat.class) Import.TextFormat format) throws IOException
    {
        List<String> fileContent = new ArrayList<>();
        fileContent.add(format.columnTypes.stream().map(c -> c.title).collect(Collectors.joining("" + format.separator)));
        for (int row = 0; row < 100; row++)
        {
            StringBuilder line = new StringBuilder();
            for (ColumnInfo c : format.columnTypes)
            {
                // TODO add random spaces, randomise content
                switch (c.type)
                {
                    case BLANK: break;
                    case NUMERIC: line.append(c.displayPrefix).append(0); break;
                    case TEXT: line.append("s"); break;
                }
                line.append(format.separator);
            }
            fileContent.add(line.toString());
        }
        PropFiles.assertEqualsMsg(format, Import.guessTextFormat(fileContent));
    }
}
