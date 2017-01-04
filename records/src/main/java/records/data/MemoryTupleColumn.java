package records.data;

import records.data.datatype.DataType;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.List;

/**
 * Created by neil on 31/10/2016.
 */
public class MemoryTupleColumn extends Column
{
    private final ColumnId title;
    private final NestedColumnStorage storage;

    public MemoryTupleColumn(RecordSet recordSet, ColumnId title, List<DataType> dataTypes) throws InternalException
    {
        super(recordSet);
        this.title = title;
        this.storage = new NestedColumnStorage(dataTypes);
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
        return new MemoryTupleColumn(rs, title, storage.getShrunk(shrunkLength));
    }
}
