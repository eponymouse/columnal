package records.data;

import annotation.qual.Value;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.SimulationRunnable;
import utility.Utility;

import java.time.temporal.TemporalAccessor;
import java.util.List;

/**
 * Created by neil on 30/11/2016.
 */
public class MemoryTemporalColumn extends EditableColumn
{
    private final TemporalColumnStorage storage;
    private final @Value TemporalAccessor defaultValue;

    public MemoryTemporalColumn(RecordSet rs, ColumnId title, DateTimeInfo dateTimeInfo, List<TemporalAccessor> list, TemporalAccessor defaultValue) throws InternalException
    {
        super(rs, title);
        this.defaultValue = DataTypeUtility.value(dateTimeInfo, defaultValue);
        this.storage = new TemporalColumnStorage(dateTimeInfo);
        this.storage.addAll(list);
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
        return new MemoryTemporalColumn(rs, getName(), getType().getDateTimeInfo(), storage._test_getShrunk(shrunkLength), defaultValue);
    }

    public void add(TemporalAccessor value) throws InternalException
    {
        storage.add(value);
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
