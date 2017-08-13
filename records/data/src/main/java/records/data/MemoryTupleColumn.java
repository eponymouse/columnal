package records.data;

import annotation.qual.Value;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.SimulationRunnable;
import utility.Utility;

import java.util.List;

/**
 * Created by neil on 31/10/2016.
 */
public class MemoryTupleColumn extends EditableColumn
{
    private final TupleColumnStorage storage;
    private final @Value Object @Value[] defaultValue;

    public MemoryTupleColumn(RecordSet recordSet, ColumnId title, List<DataType> dataTypes, @Value Object @Value[] defaultValue) throws InternalException
    {
        super(recordSet, title);
        this.defaultValue = defaultValue;
        this.storage = new TupleColumnStorage(dataTypes);
    }

    public MemoryTupleColumn(RecordSet recordSet, ColumnId title, List<DataType> dataTypes, List<Object[]> values, @Value Object @Value[] defaultValue) throws InternalException
    {
        this(recordSet, title, dataTypes, defaultValue);
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
        MemoryTupleColumn shrunk = new MemoryTupleColumn(rs, getName(), storage.getType().getMemberType(), defaultValue);
        shrunk.storage.addAll(storage._test_getShrunk(shrunkLength));
        return shrunk;
    }

    public void add(Object[] tuple) throws InternalException
    {
        storage.add(tuple);
    }


    @Override
    public @OnThread(Tag.Simulation) SimulationRunnable insertRows(int index, int count) throws InternalException, UserException
    {
        return storage.insertRows(index, Utility.replicate(count, defaultValue));
    }

    @Override
    public @OnThread(Tag.Simulation) SimulationRunnable removeRows(int index, int count) throws InternalException, UserException
    {
        return storage.removeRows(index, count);
    }

    @Override
    public @Value Object getDefaultValue()
    {
        return defaultValue;
    }
}
