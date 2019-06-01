package records.data;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
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
import utility.Utility.Record;
import utility.Utility.RecordMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by neil on 03/01/2017.
 */
public class RecordColumnStorage extends SparseErrorColumnStorage<@Value Record> implements ColumnStorage<@Value Record>
{
    // A record like {a: Text, b: Number} is stored in two column storages, one for all the a values, one for all the b
    private final ImmutableMap<@ExpressionIdentifier String, ColumnStorage<?>> storage;
    @OnThread(Tag.Any)
    private final DataTypeValue type;

    public RecordColumnStorage(ImmutableMap<@ExpressionIdentifier String, DataType> fields, boolean isImmediateData) throws InternalException
    {
        this(fields, null, isImmediateData);
    }

    public RecordColumnStorage(ImmutableMap<@ExpressionIdentifier String, DataType> fields, @Nullable BeforeGet<?> beforeGet, boolean isImmediateData) throws InternalException
    {
        super(isImmediateData);
        ImmutableMap.Builder<@ExpressionIdentifier String, ColumnStorage<?>> builder = ImmutableMap.builder();
        for (Entry<@ExpressionIdentifier String, DataType> field : fields.entrySet())
        {
            builder.put(field.getKey(), DataTypeUtility.makeColumnStorage(field.getValue(), beforeGet, isImmediateData));
        }
        storage = builder.build();
        type = DataTypeValue.record(Utility.<@ExpressionIdentifier String, ColumnStorage<?>, DataType>mapValues(storage, s -> s.getType().getType()), new GetValue<@Value Record>()
        {
            @Override
            @OnThread(Tag.Simulation)
            public @Value Record getWithProgress(int index, Column.@Nullable ProgressListener progressListener) throws UserException, InternalException
            {
                ImmutableMap.Builder<@ExpressionIdentifier String, @Value Object> record = ImmutableMap.builderWithExpectedSize(fields.size());
                for (Entry<@ExpressionIdentifier String, ColumnStorage<?>> entry : storage.entrySet())
                {
                    record.put(entry.getKey(), entry.getValue().getType().getCollapsed(index));
                }
                return DataTypeUtility.value(new RecordMap(record.build()));
            }

            @Override
            public @OnThread(Tag.Simulation) void set(int index, Either<String, @Value Record> value) throws InternalException, UserException
            {
                value.eitherEx_(err -> {
                    setError(index, err);
                    for (ColumnStorage<?> columnStorage : storage.values())
                    {
                        columnStorage.getType().setCollapsed(index, Either.left(err));
                    }
                }, record -> {
                    unsetError(index);
                    for (Entry<@ExpressionIdentifier String, ColumnStorage<?>> entry : storage.entrySet())
                    {
                        entry.getValue().getType().setCollapsed(index, Either.right(record.getField(entry.getKey())));
                    }
                });
            }
        });
    }

    @Override
    public int filled()
    {
        return storage.values().stream().mapToInt(s -> s.filled()).min().orElse(0);
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
    public void addAll(Stream<Either<String, @Value Record>> items) throws InternalException
    {
        // Each Object[] is one tuple record, add each element to each storage
        for (Either<String, Record> item : Utility.iterableStream(items))
        {
            item.eitherInt_(s -> {
                setError(filled(), s);
                for (ColumnStorage columnStorage : storage.values())
                {
                    columnStorage.addAll(Stream.<Either<String, Object>>of(Either.<String, Object>left(s)));
                }
            }, record -> {
                for (Entry<String, ColumnStorage<?>> entry : storage.entrySet())
                {
                    ((ColumnStorage)entry.getValue()).add(record.getField(entry.getKey()));
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
    public SimulationRunnable _insertRows(int index, List<@Nullable @Value Record> items) throws InternalException
    {
        List<SimulationRunnable> reverts = new ArrayList<>();
        try
        {
            for (Entry<@ExpressionIdentifier String, ColumnStorage<?>> entry : storage.entrySet())
            {
                @SuppressWarnings("unchecked")
                ColumnStorage<Object> storage = (ColumnStorage)entry.getValue();
                
                // Note: can't use mapList here as it contains nulls
                ArrayList<Either<String, Object>> r = new ArrayList<>();
                for (@Nullable Record item : items)
                {
                    if (item == null)
                        r.add(Either.<String, Object>left(""));
                    else
                        r.add(Either.<String, Object>right(item.getField(entry.getKey())));
                }
                
                reverts.add(storage.insertRows(index, r));
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
            for (ColumnStorage<?> columnStorage : storage.values())
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
