package records.data;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.SpecificDataTypeVisitor;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Either;
import xyz.columnal.utility.SimulationRunnable;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;

import java.util.List;
import java.util.stream.Stream;

/**
 * Created by neil on 31/10/2016.
 */
public class MemoryArrayColumn extends EditableColumn
{
    private final ArrayColumnStorage storage;
    private final @Value ListEx defaultValue;

    public MemoryArrayColumn(RecordSet recordSet, ColumnId title, DataType inner, List<Either<String, ListEx>> values, ListEx defaultValue) throws InternalException
    {
        super(recordSet, title);
        this.defaultValue = DataTypeUtility.value(defaultValue);
        this.storage = new ArrayColumnStorage(inner, null, true);
        this.storage.addAll(values.stream());
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
        MemoryArrayColumn shrunk = new MemoryArrayColumn(rs, getName(), storage.getType().getType().apply(new SpecificDataTypeVisitor<DataType>() {
            @Override
            public DataType array(DataType inner) throws InternalException
            {
                return inner;
            }
        }), storage.getAllCollapsed(0, shrunkLength), defaultValue);
        return shrunk;
    }

    public void add(Either<String, ListEx> listEx) throws InternalException
    {
        storage.addAll(Stream.of(listEx));
    }


    @Override
    public @OnThread(Tag.Simulation) SimulationRunnable insertRows(int index, int count) throws InternalException, UserException
    {
        return storage.insertRows(index, Utility.<Either<String, ListEx>>replicate(count, Either.<String, ListEx>right(defaultValue)));
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
