package records.data;

import annotation.qual.Value;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.SpecificDataTypeVisitor;
import records.data.datatype.DataTypeValue;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.SimulationRunnable;
import xyz.columnal.utility.Utility;

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

    public MemoryTemporalColumn(RecordSet rs, ColumnId title, DateTimeInfo dateTimeInfo, List<Either<String, TemporalAccessor>> list, @Value TemporalAccessor defaultValue) throws InternalException
    {
        super(rs, title);
        this.defaultValue = defaultValue;
        this.storage = new TemporalColumnStorage(dateTimeInfo, true);
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
        return new MemoryTemporalColumn(rs, getName(), getType().getType().apply(new SpecificDataTypeVisitor<DateTimeInfo>() {
            @Override
            public DateTimeInfo date(DateTimeInfo dateTimeInfo) throws InternalException
            {
                return dateTimeInfo;
            }
        }), storage.getAllCollapsed(0, shrunkLength), defaultValue);
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
