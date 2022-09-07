package xyz.columnal.data;

import annotation.qual.Value;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.SimulationRunnable;
import xyz.columnal.utility.Utility;

import java.util.List;
import java.util.stream.Stream;

/**
 * Created by neil on 31/10/2016.
 */
public class MemoryNumericColumn extends EditableColumn
{
    private final NumericColumnStorage storage;
    @OnThread(Tag.Any)
    private final @Value Number defaultValue;

    private MemoryNumericColumn(RecordSet rs, ColumnId title, NumberInfo numberInfo, @Value Number defaultValue) throws InternalException
    {
        super(rs, title);
        this.defaultValue = defaultValue;
        storage = new NumericColumnStorage(numberInfo, true);
    }

    public MemoryNumericColumn(RecordSet rs, ColumnId title, NumberInfo numberInfo, List<Either<String, Number>> values, @Value Number defaultValue) throws InternalException
    {
        this(rs, title, numberInfo, defaultValue);
        storage.addAll(values.stream());
    }

    public MemoryNumericColumn(RecordSet rs, ColumnId title, NumberInfo numberInfo, Stream<String> values) throws InternalException, UserException
    {
        this(rs, title, numberInfo, DataTypeUtility.value(0));
        for (String value : Utility.iterableStream(values))
        {
            storage.addRead(value);
        }
    }

    public void add(Either<String, Number> value) throws InternalException, UserException
    {
        storage.addAll(Stream.of(value));
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
        return new MemoryNumericColumn(rs, getName(), storage.getDisplayInfo(), storage.getAllCollapsed(0, shrunkLength), DataTypeUtility.value(0));
    }

    @Override
    public @OnThread(Tag.Simulation) SimulationRunnable insertRows(int index, int count) throws InternalException, UserException
    {
        return storage.insertRows(index, Utility.<Either<String, Number>>replicate(count, Either.<String, Number>right(defaultValue)));
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