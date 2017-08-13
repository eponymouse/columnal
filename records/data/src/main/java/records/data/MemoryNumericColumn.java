package records.data;

import annotation.qual.Value;
import records.data.datatype.NumberInfo;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.SimulationRunnable;
import utility.Utility;

import java.util.List;
import java.util.stream.Stream;

/**
 * Created by neil on 31/10/2016.
 */
public class MemoryNumericColumn extends EditableColumn
{
    private final NumericColumnStorage storage;
    private final @Value Number defaultValue;

    private MemoryNumericColumn(RecordSet rs, ColumnId title, NumberInfo numberInfo, Number defaultValue) throws InternalException
    {
        super(rs, title);
        this.defaultValue = defaultValue;
        storage = new NumericColumnStorage(numberInfo);
    }

    public MemoryNumericColumn(RecordSet rs, ColumnId title, NumberInfo numberInfo, List<Number> values, Number defaultValue) throws InternalException
    {
        this(rs, title, numberInfo, defaultValue);
        storage.addAll(values);
    }

    public MemoryNumericColumn(RecordSet rs, ColumnId title, NumberInfo numberInfo, Stream<String> values) throws InternalException, UserException
    {
        this(rs, title, numberInfo, 0);
        for (String value : Utility.iterableStream(values))
        {
            storage.addRead(value);
        }
    }

    public void add(Number value) throws InternalException, UserException
    {
        storage.add(value);
    }

    @Override
    @OnThread(Tag.Any)
    public DataTypeValue getType()
    {
        return storage.getType();
    }

    @Override
    public Column _test_shrink(RecordSet rs, int shrunkLength) throws InternalException, UserException
    {
        return new MemoryNumericColumn(rs, getName(), storage.getDisplayInfo(), storage._test_getShrunk(shrunkLength), 0);
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
