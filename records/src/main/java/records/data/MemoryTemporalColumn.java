package records.data;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.time.temporal.Temporal;
import java.util.List;

/**
 * Created by neil on 30/11/2016.
 */
public class MemoryTemporalColumn extends Column
{
    private final ColumnId title;
    private final DateColumnStorage storage;

    public MemoryTemporalColumn(RecordSet rs, ColumnId title, List<Temporal> list) throws InternalException
    {
        super(rs);
        this.title = title;
        this.storage = new DateColumnStorage();
        this.storage.addAll(list);
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

    private Temporal getWithProgress(int index, @Nullable ProgressListener progressListener) throws UserException, InternalException
    {
        return storage.get(index);
    }
}
