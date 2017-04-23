package records.data;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataTypeValue;
import records.error.FetchException;
import records.error.InternalException;
import records.error.UserException;
import records.gui.DisplayValue;
import records.gui.EnteredDisplayValue;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;
import utility.Utility.ReadState;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;

/**
 * Created by neil on 22/10/2016.
 */
public abstract class TextFileColumn<S extends ColumnStorage<?>> extends Column
{
    protected final @Nullable String sep;
    protected final int columnIndex;
    private final ColumnId columnName;
    private final boolean lastColumn;
    protected ReadState reader;
    @MonotonicNonNull
    @OnThread(Tag.Any)
    private S storage;


    protected TextFileColumn(RecordSet recordSet, ReadState reader, @Nullable String sep, ColumnId columnName, int columnIndex, int totalColumns)
    {
        super(recordSet);
        this.sep = sep;
        this.reader = reader;
        this.columnName = columnName;
        this.columnIndex = columnIndex;
        this.lastColumn = columnIndex == totalColumns - 1;
    }

    protected final void fillUpTo(final int rowIndex) throws UserException, InternalException
    {
        if (storage == null)
            throw new InternalException("Missing storage for " + getClass());
        try
        {
            while (rowIndex >= storage.filled())
            {
                // Should we share loading across columns for the same file?
                ArrayList<String> next = new ArrayList<>();
                reader = Utility.readColumnChunk(reader, sep, columnIndex, next);
                addValues(next);
            }
        }
        catch (IOException e)
        {
            throw new FetchException("Error reading file " + reader.getAbsolutePath(), e);
        }

    }


    void setStorage(S storage)
    {
        this.storage = storage;
    }

    S getStorage() throws InternalException
    {
        if (storage == null)
            throw new InternalException("Missing storage for " + getClass());
        return storage;
    }

    @Override
    @OnThread(Tag.Any)
    public final synchronized DataTypeValue getType() throws UserException, InternalException
    {
        if (storage == null)
            throw new InternalException("Missing storage for " + getClass());
        return storage.getType();
    }

    protected abstract void addValues(ArrayList<String> values) throws InternalException, UserException;

    @Override
    @OnThread(Tag.Any)
    public final ColumnId getName()
    {
        return columnName;
    }

    @Override
    public DisplayValue storeValue(EnteredDisplayValue writtenValue) throws InternalException, UserException
    {
        throw new InternalException("Cannot edit data which is linked to a text file");
    }
}
