package records.data;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.util.List;

/**
 * Created by neil on 30/11/2016.
 */
public class MemoryTemporalColumn extends Column
{
    private final ColumnId title;
    private final DateColumnStorage storage;

    public MemoryTemporalColumn(RecordSet rs, ColumnId title, DateTimeInfo dateTimeInfo, List<TemporalAccessor> list) throws InternalException
    {
        super(rs);
        this.title = title;
        this.storage = new DateColumnStorage(dateTimeInfo);
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

    @Override
    public Column _test_shrink(RecordSet rs, int shrunkLength) throws InternalException
    {
        return new MemoryTemporalColumn(rs, title, getType().getDateTimeInfo(), storage.getShrunk(shrunkLength));
    }
}
