package records.data;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.SimulationRunnable;
import utility.Utility;
import utility.Utility.ListEx;

import java.util.List;

/**
 * Created by neil on 31/10/2016.
 */
public class MemoryArrayColumn extends EditableColumn
{
    private final ArrayColumnStorage storage;
    private final @Value ListEx defaultValue;

    public MemoryArrayColumn(RecordSet recordSet, ColumnId title, @Nullable DataType inner, List<ListEx> values, ListEx defaultValue) throws InternalException
    {
        super(recordSet, title);
        this.defaultValue = DataTypeUtility.value(defaultValue);
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
        MemoryArrayColumn shrunk = new MemoryArrayColumn(rs, getName(), storage.getType().getMemberType().get(0), storage.getAllCollapsed(0, shrunkLength), defaultValue);
        return shrunk;
    }

    public void add(ListEx listEx) throws InternalException
    {
        storage.add(listEx);
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
    @OnThread(Tag.Any)
    public @Value Object getDefaultValue()
    {
        return defaultValue;
    }
}
