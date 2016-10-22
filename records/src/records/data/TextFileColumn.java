package records.data;

import records.error.FetchException;
import records.error.UserException;
import utility.Utility;
import utility.Utility.ReadState;

import java.io.File;
import java.io.IOException;

/**
 * Created by neil on 22/10/2016.
 */
public abstract class TextFileColumn extends Column
{
    protected final File textFile;
    private final int headerRows;
    protected final byte sep;
    protected final int columnIndex;
    private final String columnName;
    protected ReadState lastFilePosition;
    private long rowCount = -1;

    protected TextFileColumn(File textFile, int headerRows, byte sep, String columnName, int columnIndex) throws IOException
    {
        this.textFile = textFile;
        this.headerRows = headerRows;
        this.sep = sep;
        this.lastFilePosition = Utility.skipFirstNRows(textFile, headerRows);
        this.columnName = columnName;
        this.columnIndex = columnIndex;
    }

    @Override
    public final boolean indexValid(int index) throws UserException
    {
        if (rowCount == -1)
        {
            try
            {
                rowCount = Utility.countLines(textFile) - headerRows;
            }
            catch (IOException e)
            {
                throw new FetchException("Error counting rows", e);
            }
        }
        return index < rowCount;
    }


    @Override
    public final long getVersion()
    {
        return 1;
    }

    @Override
    public final String getName()
    {
        return columnName;
    }
}
