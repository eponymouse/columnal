package records.data;

import annotation.qual.Value;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.SimulationRunnable;
import utility.Utility;

import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.stream.Stream;

/**
 * Created by neil on 30/11/2016.
 */
public class MemoryTemporalColumn extends EditableColumn
{
    private final TemporalColumnStorage storage;
    @OnThread(Tag.Any)
    private final @Value TemporalAccessor defaultValue;

    public MemoryTemporalColumn(RecordSet rs, ColumnId title, DateTimeInfo dateTimeInfo, List<Either<String, TemporalAccessor>> list, TemporalAccessor defaultValue) throws InternalException
    {
        super(rs, title);
        this.defaultValue = DataTypeUtility.value(dateTimeInfo, defaultValue);
        this.storage = new TemporalColumnStorage(dateTimeInfo);
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
        return new MemoryTemporalColumn(rs, getName(), getType().getDateTimeInfo(), storage.getAllCollapsed(0, shrunkLength), defaultValue);
    }

    public void add(Either<String, TemporalAccessor> value) throws InternalException
    {
        storage.addAll(Stream.of(value));
    }


    @Override
    public @OnThread(Tag.Simulation) SimulationRunnable insertRows(int index, int count) throws InternalException, UserException
    {
        return storage.insertRows(index, Utility.replicate(count, Either.<String, TemporalAccessor>right(defaultValue)));
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
