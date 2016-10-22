package records.data;

import records.error.FetchException;
import records.error.UserException;
import utility.Utility;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by neil on 20/10/2016.
 */
public class TextFileColumn extends Column<Object>
{
    private static final int CHUNK_LINES = 100;
    private final ArrayList<String> loadedValues = new ArrayList<>();
    private final File textFile;
    private final String sep;
    private final int columnIndex;
    private long lastFilePosition = 0;
    private long rowCount = -1;

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
    public String get(int index) throws UserException
    {
        String line = "";
        try
        {
            // TODO share loading across columns?  Maybe have boolean indicating whether to do so;
            // true if user scrolled in table, false if we are performing a calculation
            while (index >= loadedValues.size())
            {
                try (BufferedReader br = new BufferedReader(new FileReader(textFile)))
                {
                    if (lastFilePosition != br.skip(lastFilePosition))
                        throw new IOException("Skip didn't work");
                    int read = 0;
                    while ((line = br.readLine()) != null && read++ < CHUNK_LINES)
                    {
                        lastFilePosition += line.length() + 1;// TODO this is a hack which assumes only Unix endings
                        loadedValues.add(line.split(sep)[columnIndex]);
                        System.err.println("Loaded: " + loadedValues.size());
                    }
                }
                // TODO prevent infinite loop, in the case that the file changed.
            }

            return loadedValues.get(index);
        }
        catch (ArrayIndexOutOfBoundsException e)
        {
            System.err.println("Error on line: \"" + line + "\"");
            e.printStackTrace();
            throw new UserException("TODO");
        }
        catch (IOException e)
        {
            throw new FetchException(e);
        }
    }

    @Override
    public boolean indexValid(int index) throws UserException
    {
        if (rowCount == -1)
        {
            try
            {
                rowCount = Utility.countLines(textFile);
            }
            catch (IOException e)
            {
                throw new FetchException(e);
            }
        }
        return index < rowCount;
    }

    @Override
    public Class<?> getType()
    {
        return String.class; // Allow other types?
    }

    @Override
    public long getVersion()
    {
        return 1;
    }
}
