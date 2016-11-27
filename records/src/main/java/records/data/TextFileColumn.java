package records.data;

import records.error.FetchException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
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
    protected final byte sep;
    protected final int columnIndex;
    private final ColumnId columnName;
    protected ReadState lastFilePosition;


    protected TextFileColumn(RecordSet recordSet, File textFile, long initialFilePosition, byte sep, ColumnId columnName, int columnIndex)
    {
        super(recordSet);
        this.textFile = textFile;
        this.sep = sep;
        this.lastFilePosition = new ReadState(initialFilePosition);
        this.columnName = columnName;
        this.columnIndex = columnIndex;
    }

    @Override
    @OnThread(Tag.Any)
    public final ColumnId getName()
    {
        return columnName;
    }
}
