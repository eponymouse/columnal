package records.data;

import annotation.qual.Value;
import records.data.datatype.DataTypeUtility;
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
public class MemoryStringColumn extends EditableColumn
{
    private final StringColumnStorage storage;
    private final @Value String defaultValue;

    public MemoryStringColumn(RecordSet recordSet, ColumnId title, List<String> values, String defaultValue) throws InternalException
    {
        super(recordSet, title);
        this.defaultValue = DataTypeUtility.value(defaultValue);
        this.storage = new StringColumnStorage();
        this.storage.addAll(values);
    }

    public void add(String value) throws InternalException
    {
        storage.add(value);
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
        return new MemoryStringColumn(rs, getName(), storage._test_getShrunk(shrunkLength), "");
    }

    @Override
    public @OnThread(Tag.Simulation) SimulationRunnable insertRows(int index, int count) throws InternalException
    {
        return storage.insertRows(index, Utility.replicate(count, defaultValue));
    }

    @Override
    public @OnThread(Tag.Simulation) SimulationRunnable removeRows(int index, int count) throws InternalException
    {
        return storage.removeRows(index, count);
    }

    @Override
    @OnThread(Tag.Any)
    public @Value Object getDefaultValue()
    {
        return defaultValue;
    }

    // Used by InferTypeColumn to easily directly access the values:
    public @Value String getValue(int index) throws UserException, InternalException
    {
        return storage.get(index, null);
    }

    // Used by InferTypeColumn to easily directly access the values:
    public void setValue(int index, @Value String value) throws InternalException
    {
        storage.setValue(index, value);
    }

    // Used by InferTypeColumn to easily directly access the values:
    public List<String> getAll() throws InternalException, UserException
    {
        return storage.getAllCollapsed(storage.filled());
    }
}
