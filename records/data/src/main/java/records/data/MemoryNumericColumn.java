package records.data;

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

    private MemoryNumericColumn(RecordSet rs, ColumnId title, NumberInfo numberInfo) throws InternalException
    {
        super(rs, title);
        storage = new NumericColumnStorage(numberInfo);
    }

    public MemoryNumericColumn(RecordSet rs, ColumnId title, NumberInfo numberInfo, List<Number> values) throws InternalException
    {
        this(rs, title, numberInfo);
        storage.addAll(values);
    }

    public MemoryNumericColumn(RecordSet rs, ColumnId title, NumberInfo numberInfo, Stream<String> values) throws InternalException, UserException
    {
        this(rs, title, numberInfo);
        for (String value : Utility.iterableStream(values))
        {
            storage.addRead(value);
        }
    }

    public void add(String value) throws InternalException, UserException
    {
        storage.addRead(value);
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
        return new MemoryNumericColumn(rs, getName(), storage.getDisplayInfo(), storage._test_getShrunk(shrunkLength));
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
