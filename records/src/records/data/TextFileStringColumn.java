package records.data;

import records.error.FetchException;
import records.error.UserException;
import utility.CompleteStringPool;
import utility.Utility;
import utility.Utility.ReadState;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by neil on 20/10/2016.
 */
public class TextFileStringColumn extends TextFileColumn
{
    private final ArrayList<String> loadedValues = new ArrayList<>();
    private final CompleteStringPool pool = new CompleteStringPool(1000);

    public TextFileStringColumn(File textFile, int headerRows, byte sep, String columnName, int columnIndex) throws IOException
    {
        super(textFile, headerRows, sep, columnName, columnIndex);
    }

    @Override
    public String get(int index) throws UserException
    {
        try
        {
            // TODO share loading across columns?  Maybe have boolean indicating whether to do so;
            // true if user scrolled in table, false if we are performing a calculation
            while (index >= loadedValues.size())
            {
                ArrayList<String> next = new ArrayList<>();
                lastFilePosition = Utility.readColumnChunk(textFile, lastFilePosition, sep, columnIndex, next);
                if (!lastFilePosition.isEOF())
                {
                    loadedValues.ensureCapacity(loadedValues.size() + next.size());
                    for (String s : next)
                        loadedValues.add(pool.pool(s));
                }
                else
                    throw new FetchException("Error reading line", new EOFException());
                // TODO handle case where file changed outside.
            }

            return loadedValues.get(index);
        }
        catch (IOException e)
        {
            throw new FetchException("Error reading " + textFile, e);
        }
    }

    @Override
    public Class<String> getType()
    {
        return String.class;
    }
}
