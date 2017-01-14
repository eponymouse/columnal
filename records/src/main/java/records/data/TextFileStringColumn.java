package records.data;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by neil on 20/10/2016.
 */
public class TextFileStringColumn extends TextFileColumn<StringColumnStorage>
{
    @SuppressWarnings("initialization")
    public TextFileStringColumn(RecordSet recordSet, File textFile, long initialFilePosition, byte sep, ColumnId columnName, int columnIndex)
    {
        super(recordSet, textFile, initialFilePosition, sep, columnName, columnIndex);
        setStorage(new StringColumnStorage((index, prog) -> fillUpTo(index)));
    }

    @Override
    protected void addValues(ArrayList<String> values) throws InternalException
    {
        getStorage().addAll(values);
    }
}
