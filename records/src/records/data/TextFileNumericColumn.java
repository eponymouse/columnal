package records.data;

import records.error.FetchException;
import records.error.InternalException;
import records.error.UserException;
import utility.Utility;
import utility.Utility.ReadState;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by neil on 20/10/2016.
 */
public class TextFileNumericColumn extends TextFileColumn
{
    private final NumericColumnStorage loadedValues = new NumericColumnStorage();

    public TextFileNumericColumn(File textFile, int headerRows, byte sep, String columnName, int columnIndex) throws IOException
    {
        super(textFile, headerRows, sep, columnName, columnIndex);
    }

    @Override
    public Number get(int index) throws InternalException, UserException
    {
        try
        {
            // TODO share loading across columns?  Maybe have boolean indicating whether to do so;
            // true if user scrolled in table, false if we are performing a calculation
            while (index >= loadedValues.filled())
            {
                ArrayList<String> next = new ArrayList<>();
                lastFilePosition = Utility.readColumnChunk(textFile, lastFilePosition, sep, columnIndex, next);
                if (!lastFilePosition.isEOF())
                {
                    for (String s : next)
                    {
                        try
                        {
                            loadedValues.add(s);
                        }
                        catch (NumberFormatException e)
                        {
                            throw new FetchException("Could not parse number: \"" + s + "\"", e);
                        }
                    }
                }
                else
                    throw new FetchException("Error reading line: " + loadedValues.filled(), new EOFException());
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
    public Class<Number> getType()
    {
        return Number.class;
    }
}
