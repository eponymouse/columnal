package records.data;

import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.SimulationRunnable;

import java.util.List;

/**
 * Created by neil on 30/11/2016.
 */
public class MemoryBooleanColumn extends EditableColumn
{
    private final BooleanColumnStorage storage;

    public MemoryBooleanColumn(RecordSet rs, ColumnId title, List<Boolean> list) throws InternalException
    {
        super(rs, title);
        this.storage = new BooleanColumnStorage();
        this.storage.addAll(list);
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
        return new MemoryBooleanColumn(rs, getName(), storage.getShrunk(shrunkLength));
    }

    public void add(boolean b) throws InternalException
    {
        storage.add(b);
    }

    @Override
    public @OnThread(Tag.Simulation) SimulationRunnable insertRows(int index, int count) throws InternalException, UserException
    {
        return storage.insertRows(index, count);
    }

    @Override
    public @OnThread(Tag.Simulation) SimulationRunnable removeRows(int index, int count) throws InternalException, UserException
    {
        return storage.removeRows(index, count);
    }
}
