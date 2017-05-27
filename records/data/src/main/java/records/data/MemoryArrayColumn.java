package records.data;

import records.data.datatype.DataType;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility.ListEx;

import java.util.List;

/**
 * Created by neil on 31/10/2016.
 */
public class MemoryArrayColumn extends Column
{
    private final ArrayColumnStorage storage;

    public MemoryArrayColumn(RecordSet recordSet, ColumnId title, DataType inner, List<ListEx> values) throws InternalException
    {
        super(recordSet, title);
        this.storage = new ArrayColumnStorage(inner, null);
        this.storage.addAll(values);
    }

    @Override
    @OnThread(Tag.Any)
    public synchronized DataTypeValue getType()
    {
        return storage.getType();
    }

    @Override
    public Column _test_shrink(RecordSet rs, int shrunkLength) throws InternalException, UserException
    {
        MemoryArrayColumn shrunk = new MemoryArrayColumn(rs, getName(), storage.getType().getMemberType().get(0), storage._test_getShrunk(shrunkLength));
        return shrunk;
    }

    public void add(ListEx listEx) throws InternalException
    {
        storage.add(listEx);
    }
}
