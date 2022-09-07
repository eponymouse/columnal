package xyz.columnal.data;

import annotation.qual.Value;
import xyz.columnal.data.datatype.DataTypeUtility;
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
public class MemoryStringColumn extends EditableColumn
{
    private final StringColumnStorage storage;
    private final @Value String defaultValue;

    public MemoryStringColumn(RecordSet recordSet, ColumnId title, List<Either<String, String>> values, String defaultValue) throws InternalException
    {
        super(recordSet, title);
        this.defaultValue = DataTypeUtility.value(defaultValue);
        this.storage = new StringColumnStorage(true);
        this.storage.addAll(values.stream());
    }

    public void add(Either<String, String> value) throws InternalException
    {
        storage.addAll(Stream.of(value));
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
        return new MemoryStringColumn(rs, getName(), storage.getAllCollapsed(0, shrunkLength), "");
    }

    @Override
    public @OnThread(Tag.Simulation) SimulationRunnable insertRows(int index, int count) throws InternalException, UserException
    {
        return storage.insertRows(index, Utility.replicate(count, Either.<String, String>right(defaultValue)));
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

    // Used by InferTypeColumn to easily directly access the values:
    public void setValue(int index, @Value String value) throws InternalException
    {
        storage.setValue(index, value);
    }
}