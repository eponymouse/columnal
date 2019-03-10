package records.data;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.data.datatype.DataTypeValue.GetValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.SimulationRunnable;
import utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by neil on 03/01/2017.
 */
public class TupleColumnStorage extends SparseErrorColumnStorage<Object[]> implements ColumnStorage<Object[]>
{
    // For tuples, each element is one column-major part of tuple (i.e. a column)
    private final ImmutableList<ColumnStorage<?>> storage;
    @OnThread(Tag.Any)
    private final DataTypeValue type;

    public TupleColumnStorage(List<DataType> innerToCopy, boolean isImmediateData) throws InternalException
    {
        this(innerToCopy, null, isImmediateData);
    }

    public TupleColumnStorage(List<DataType> innerToCopy, @Nullable BeforeGet<?> beforeGet, boolean isImmediateData) throws InternalException
    {
        super(isImmediateData);
        ArrayList<ColumnStorage<?>> buildList = new ArrayList<>();
        for (DataType anInnerToCopy : innerToCopy)
        {
            buildList.add(DataTypeUtility.makeColumnStorage(anInnerToCopy, beforeGet, isImmediateData));
        }
        storage = ImmutableList.copyOf(buildList);
        type = DataTypeValue.tuple(Utility.<ColumnStorage<?>, DataType>mapList(storage, s -> s.getType()), new GetValue<@Value Object @Value[]>()
        {
            @Override
            @OnThread(Tag.Simulation)
            public @Value Object @Value [] getWithProgress(int index, Column.@Nullable ProgressListener progressListener) throws UserException, InternalException
            {
                @Value Object[] tuple = new @Value Object[storage.size()];
                for (int i = 0; i < storage.size(); i++)
                {
                    ColumnStorage<?> columnStorage = storage.get(i);
                    tuple[i] = columnStorage.getType().getCollapsed(i);
                }
                return DataTypeUtility.value(tuple);
            }

            @Override
            public @OnThread(Tag.Simulation) void set(int index, Either<String, @Value Object[]> value) throws InternalException, UserException
            {
                value.eitherEx_(err -> {
                    setError(index, err);
                    for (ColumnStorage<?> columnStorage : storage)
                    {
                        columnStorage.getType().setCollapsed(index, Either.left(err));
                    }
                }, tuple -> {
                    unsetError(index);
                    for (int i = 0; i < storage.size(); i++)
                    {
                        storage.get(i).getType().setCollapsed(index, Either.right(tuple[i]));
                    }
                });
            }
        });
    }

    @Override
    public int filled()
    {
        return storage.stream().mapToInt(s -> s.filled()).min().orElse(0);
    }

    /*
    public Object @NonNull [] get(int index) throws InternalException, UserException
    {
        Object[] r = new Object[storage.size()];
        for (int i = 0; i < r.length; i++)
        {
            r[i] = storage.get(i).get(index);
        }
        return r;
    }*/

    @SuppressWarnings({"unchecked", "all"})
    @Override
    public void addAll(Stream<Either<String, Object[]>> items) throws InternalException
    {
        // Each Object[] is one tuple record, add each element to each storage
        for (Either<String, Object[]> item : Utility.iterableStream(items))
        {
            item.eitherInt_(s -> {
                setError(filled(), s);
                for (ColumnStorage columnStorage : storage)
                {
                    columnStorage.addAll(Stream.<Either<String, Object>>of(Either.<String, Object>left(s)));
                }
            }, tuple -> {

                for (int i = 0; i < tuple.length; i++)
                {
                    ((ColumnStorage) storage.get(i)).add(tuple[i]);
                }
            });
        }
    }

    @Override
    @OnThread(Tag.Any)
    public DataTypeValue getType()
    {
        return type;
    }

    @Override
    public SimulationRunnable _insertRows(int index, List<Object @Nullable []> items) throws InternalException
    {
        List<SimulationRunnable> reverts = new ArrayList<>();
        try
        {
            for (int column = 0; column < storage.size(); column++)
            {
                ColumnStorage<?> columnStorage = storage.get(column);
                int columnFinal = column;
                // Note: can't use ImmutableList here as it contains nulls
                // Declaration to use suppression:
                @SuppressWarnings({"unchecked", "all"})
                boolean b = reverts.add(columnStorage.insertRows(index, 
                        (List)items.stream().map(x -> x == null ? Either.<String, Object>left("") : Either.<String, Object>right(x[columnFinal])).collect(Collectors.toList())));
            }
            return () ->
            {
                for (SimulationRunnable revert : reverts)
                {
                    revert.run();
                }
            };
        }
        catch (InternalException e)
        {
            for (SimulationRunnable revert : reverts)
            {
                revert.run();
            }
            throw e;
        }
    }

    @Override
    public SimulationRunnable _removeRows(int index, int count) throws InternalException
    {
        List<SimulationRunnable> reverts = new ArrayList<>();
        try
        {
            for (ColumnStorage<?> columnStorage : storage)
            {
                reverts.add(columnStorage.removeRows(index, count));
            }
            return () ->
            {
                for (SimulationRunnable revert : reverts)
                {
                    revert.run();
                }
            };
        }
        catch (InternalException e)
        {
            for (SimulationRunnable revert : reverts)
            {
                revert.run();
            }
            throw e;
        }

    }
}
