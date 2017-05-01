package records.data;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.InternalException;
import utility.Utility.ReadState;

import java.util.ArrayList;

/**
 * Created by neil on 20/10/2016.
 */
public class TextFileStringColumn extends TextFileColumn<StringColumnStorage>
{
    @SuppressWarnings("initialization")
    public TextFileStringColumn(RecordSet recordSet, ReadState reader, @Nullable String sep, ColumnId columnName, int columnIndex, int totalColumns)
    {
        super(recordSet, reader, sep, columnName, columnIndex, totalColumns);
        setStorage(new StringColumnStorage((index, prog) -> fillUpTo(index)));
    }

    @Override
    protected void addValues(ArrayList<String> values) throws InternalException
    {
        getStorage().addAll(values);
    }
}
