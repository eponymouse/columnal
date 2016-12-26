package records.data;

import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.File;

/**
 * Created by neil on 26/12/2016.
 */
public class TextFileDateColumn extends TextFileColumn
{
    private final DateColumnStorage storage;

    public TextFileDateColumn(RecordSet recordSet, File textFile, long initialFilePosition, byte sep, ColumnId columnName, int columnIndex, DateTimeInfo dateTimeInfo)
    {
        super(recordSet, textFile, initialFilePosition, sep, columnName, columnIndex);
        storage = new DateColumnStorage(dateTimeInfo);
    }

    @Override
    public @OnThread(Tag.Any) DataTypeValue getType() throws InternalException, UserException
    {
        return storage.getType();
    }
}
