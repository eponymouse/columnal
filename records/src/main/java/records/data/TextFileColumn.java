package records.data;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import records.data.datatype.DataTypeValue;
import records.error.FetchException;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;
import utility.Utility.ReadState;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by neil on 22/10/2016.
 */
public abstract class TextFileColumn<T> extends Column
{
    protected final File textFile;
    protected final byte sep;
    protected final int columnIndex;
    private final ColumnId columnName;
    protected ReadState lastFilePosition;
    protected final ColumnStorage<T> storage;
    @MonotonicNonNull
    @OnThread(value = Tag.Any, requireSynchronized = true)
    private DataTypeValue dataType;


    protected TextFileColumn(RecordSet recordSet, File textFile, long initialFilePosition, byte sep, ColumnId columnName, int columnIndex, ColumnStorage<T> storage)
    {
        super(recordSet);
        this.textFile = textFile;
        this.sep = sep;
        this.lastFilePosition = new ReadState(initialFilePosition);
        this.columnName = columnName;
        this.columnIndex = columnIndex;
        this.storage = storage;
    }

    protected final void fillUpTo(final int rowIndex) throws UserException, InternalException
    {
        try
        {
            while (rowIndex >= storage.filled())
            {
                // Should we share loading across columns for the same file?
                ArrayList<String> next = new ArrayList<>();
                lastFilePosition = Utility.readColumnChunk(textFile, lastFilePosition, sep, columnIndex, next);
                if (!lastFilePosition.isEOF())
                {
                    addValues(next);
                }
                else
                    throw new FetchException("Error reading line of " + textFile.getAbsolutePath() + " got " + storage.filled() + " searching for " + rowIndex, new EOFException());
            }
        }
        catch (IOException e)
        {
            throw new FetchException("Error reading file " + textFile.getAbsolutePath(), e);
        }

    }

    @Override
    @OnThread(Tag.Any)
    public final synchronized DataTypeValue getType() throws UserException, InternalException
    {
        if (dataType == null)
        {
            dataType = makeDataType();
        }
        return dataType;
    }

    @OnThread(Tag.Any)
    protected abstract DataTypeValue makeDataType() throws InternalException, UserException;

    protected abstract void addValues(ArrayList<String> values) throws InternalException, UserException;

    @Override
    @OnThread(Tag.Any)
    public final ColumnId getName()
    {
        return columnName;
    }
}
