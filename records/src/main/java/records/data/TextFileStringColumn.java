package records.data;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeValue;
import records.error.FetchException;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.DumbObjectPool;
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
public class TextFileStringColumn extends TextFileColumn<String>
{
    public TextFileStringColumn(RecordSet recordSet, File textFile, long initialFilePosition, byte sep, ColumnId columnName, int columnIndex)
    {
        super(recordSet, textFile, initialFilePosition, sep, columnName, columnIndex, new StringColumnStorage());
    }

    @Override
    @OnThread(Tag.Any)
    protected DataTypeValue makeDataType()
    {
        return DataTypeValue.text((i, prog) -> {
            fillUpTo(i);
            return storage.get(i);
        });
    }

    @Override
    protected void addValues(ArrayList<String> values) throws InternalException
    {
        storage.addAll(values);
    }
}
