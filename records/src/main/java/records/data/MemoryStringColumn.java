package records.data;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

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
    private DataTypeValue dataType;

    public MemoryStringColumn(RecordSet recordSet, ColumnId title, List<String> values) throws InternalException
    {
        super(recordSet);
        this.title = title;
        this.storage = new StringColumnStorage();
        this.storage.addAll(values);
    }

    @Override
    @OnThread(Tag.Any)
    public ColumnId getName()
    {
        return title;
    }

    @Override
    @OnThread(Tag.Any)
    public synchronized DataTypeValue getType()
    {
        return storage.getType();
    }

    private String getWithProgress(int index, @Nullable ProgressListener progressListener) throws UserException, InternalException
    {
        return storage.get(index);
    }

    @Override
    public Column shrink(RecordSet rs, int shrunkLength) throws InternalException
    {
        return new MemoryStringColumn(rs, title, storage.getShrunk(shrunkLength));
    }
}
