package records.data;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.FetchException;
import records.error.UserException;
import utility.DumbStringPool;
import utility.Utility;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Created by neil on 20/10/2016.
 */
public class TextFileStringColumn extends TextFileColumn
{
    private String[] loadedValues = new String[0];
    private final DumbStringPool pool = new DumbStringPool(1000);

    public TextFileStringColumn(RecordSet recordSet, File textFile, long initialFilePosition, byte sep, String columnName, int columnIndex)
    {
        super(recordSet, textFile, initialFilePosition, sep, columnName, columnIndex);
    }

    @Override
    public String getWithProgress(int index, @Nullable ProgressListener progressListener) throws UserException
    {
        try
        {
            // TODO share loading across columns?  Maybe have boolean indicating whether to do so;
            // true if user scrolled in table, false if we are performing a calculation
            while (index >= loadedValues.length)
            {
                ArrayList<String> next = new ArrayList<>();
                lastFilePosition = Utility.readColumnChunk(textFile, lastFilePosition, sep, columnIndex, next);
                if (!lastFilePosition.isEOF())
                {
                    int prevSize = loadedValues.length;
                    // Yes they do become null, but they won't be null
                    // after we've finished the loop:
                    @SuppressWarnings("nullness")
                    String[] newLoadedValues = Arrays.copyOf(loadedValues, prevSize + next.size());
                    for (int i = 0; i < next.size(); i++)
                    {
                        newLoadedValues[prevSize + i] = pool.pool(next.get(i));
                    }
                    loadedValues = newLoadedValues;
                    if (progressListener != null)
                        progressListener.progressUpdate((double)loadedValues.length / index);
                }
                else
                    throw new FetchException("Error reading line", new EOFException());
                // TODO handle case where file changed outside.
            }

            return loadedValues[index];
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

    @Override
    public Optional<List<@NonNull ? extends Object>> fastDistinct() throws UserException
    {
        //indexValid(0);
        //return (loadedValues.size() < rowCount || pool.isFull()) ? Optional.<List<@NonNull ? extends Object>>empty() : Optional.<List<@NonNull ? extends Object>>of(pool.get());
        return Optional.empty();
    }
}
