package records.data;

import records.data.datatype.DataType;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.List;

/**
 * Created by neil on 31/10/2016.
 */
public class MemoryTupleColumn extends Column
{
    private final TupleColumnStorage storage;

    public MemoryTupleColumn(RecordSet recordSet, ColumnId title, List<DataType> dataTypes) throws InternalException
    {
        super(recordSet, title);
        this.storage = new TupleColumnStorage(dataTypes);
    }

    public MemoryTupleColumn(RecordSet recordSet, ColumnId title, List<DataType> dataTypes, List<Object[]> values) throws InternalException
    {
        this(recordSet, title, dataTypes);
        storage.addAll(values);
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
        MemoryTupleColumn shrunk = new MemoryTupleColumn(rs, getName(), storage.getType().getMemberType());
        shrunk.storage.addAll(storage._test_getShrunk(shrunkLength));
        return shrunk;
    }

    @Override
    public DisplayValue storeValue(EnteredDisplayValue writtenValue) throws InternalException, UserException
    {
        return storage.storeValue(writtenValue);
    }

    @Override
    public void addRow() throws InternalException, UserException
    {
        storage.addRow();
    }

    public void add(Object[] tuple) throws InternalException
    {
        storage.add(tuple);
    }
}
