package records.data;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by neil on 20/10/2016.
 */
public class TextFileColumn extends Column<String>
{
    private static final int CHUNK_LINES = 100;
    private final ArrayList<String> loadedValues = new ArrayList<>();
    private final File textFile;
    private final String sep;
    private final int columnIndex;
    private long lastFilePosition = 0;

    public TextFileColumn(File textFile, String sep, int columnIndex)
    {
        this.textFile = textFile;
        this.sep = sep;
        this.columnIndex = columnIndex;
    }

    @Override
    public String getName()
    {
        return "Src" + columnIndex;
    }

    @Override
    public String get(int index) throws Exception
    {
        // TODO share loading across columns?  Maybe have boolean indicating whether to do so;
        // true if user scrolled in table, false if we are performing a calculation
        while (index >= loadedValues.size())
        {
            try (BufferedReader br = new BufferedReader(new FileReader(textFile)))
            {
                if (lastFilePosition != br.skip(lastFilePosition))
                    throw new IOException("Skip didn't work");
                String line;
                int read = 0;
                while ((line = br.readLine()) != null && read++ < CHUNK_LINES)
                {
                    lastFilePosition += line.length();
                    loadedValues.add(line.split(sep)[columnIndex]);
                }
            }
            // TODO prevent infinite loop, in the case that the file changed.
        }

        return loadedValues.get(index);
    }
}
