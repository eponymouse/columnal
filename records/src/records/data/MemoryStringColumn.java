package records.data;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.DumbStringPool;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by neil on 31/10/2016.
 */
public class MemoryStringColumn extends Column
{
    private final ColumnId title;
    private final StringColumnStorage storage;
    @MonotonicNonNull
    @OnThread(value = Tag.Any, requireSynchronized = true)
    private DataType dataType;

    public MemoryStringColumn(RecordSet recordSet, ColumnId title, List<String> values) throws InternalException
    {
        super(recordSet);
        this.title = title;
        this.storage = new StringColumnStorage();
        this.storage.addAllNoNull(values);
    }

    @Override
    @OnThread(Tag.Any)
    public ColumnId getName()
    {
        return title;
    }

    @Override
    public long getVersion()
    {
        return 1;
    }

    @Override
    @OnThread(Tag.Any)
    public synchronized DataType getType()
    {
        if (dataType == null)
        {
            dataType = new DataType()
            {
                @Override
                public <R> R apply(DataTypeVisitorGet<R> visitor) throws UserException, InternalException
                {
                    return visitor.text(MemoryStringColumn.this::getWithProgress);
                }
            };
        }
        return dataType;
    }

    private String getWithProgress(int index, @Nullable ProgressListener progressListener) throws UserException, InternalException
    {
        return storage.get(index);
    }
}
