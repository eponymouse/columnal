package records.data;

import annotation.qual.Value;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.SimulationRunnable;
import utility.Utility;

import java.util.List;

/**
 * Created by neil on 30/11/2016.
 */
public class MemoryBooleanColumn extends EditableColumn
{
    private final BooleanColumnStorage storage;
    @OnThread(Tag.Any)
    private final @Value Boolean defaultValue;

    public MemoryBooleanColumn(RecordSet rs, ColumnId title, List<Either<String, Boolean>> list, Boolean defaultValue) throws InternalException
    {
        super(rs, title);
        this.defaultValue = DataTypeUtility.value(defaultValue);
        this.storage = new BooleanColumnStorage();
        this.storage.addAll(list.stream());
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
        return new MemoryBooleanColumn(rs, getName(), storage.getAllCollapsed(0, shrunkLength), false);
    }

    public void add(boolean b) throws InternalException
    {
        storage.add(b);
    }

    @Override
    public @OnThread(Tag.Simulation) SimulationRunnable insertRows(int index, int count) throws InternalException, UserException
    {
        return storage.insertRows(index, Utility.replicate(count, Either.<String, Boolean>right(defaultValue)));
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
