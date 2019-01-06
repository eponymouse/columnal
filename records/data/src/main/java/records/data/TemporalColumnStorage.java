package records.data;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column.ProgressListener;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.data.datatype.DataTypeValue.GetValue;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.DumbObjectPool;
import utility.Either;
import utility.SimulationRunnable;
import utility.Utility;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Created by neil on 04/11/2016.
 */
public class TemporalColumnStorage extends SparseErrorColumnStorage<TemporalAccessor> implements ColumnStorage<TemporalAccessor>
{
    private final ArrayList<@Value TemporalAccessor> values;
    private final DumbObjectPool<@Value TemporalAccessor> pool;
    @OnThread(Tag.Any)
    private final DateTimeInfo dateTimeInfo;
    @MonotonicNonNull
    @OnThread(value = Tag.Any,requireSynchronized = true)
    private DataTypeValue dataType;
    private final @Nullable BeforeGet<TemporalColumnStorage> beforeGet;

    public TemporalColumnStorage(DateTimeInfo dateTimeInfo) throws InternalException
    {
        this(dateTimeInfo, null);
    }

    public TemporalColumnStorage(DateTimeInfo dateTimeInfo, @Nullable BeforeGet<TemporalColumnStorage> beforeGet) throws InternalException
    {
        this.values = new ArrayList<>();
        this.pool = new DumbObjectPool<>((Class<@Value TemporalAccessor>)TemporalAccessor.class, 1000, (Comparator<@Value TemporalAccessor>)dateTimeInfo.getComparator(true));
        this.dateTimeInfo = dateTimeInfo;
        this.beforeGet = beforeGet;
    }

    @Override
    public int filled()
    {
        return values.size();
    }

    public @Value TemporalAccessor get(int index, @Nullable ProgressListener progressListener) throws InternalException, UserException
    {
        if (beforeGet != null)
            beforeGet.beforeGet(this, index, progressListener);
        if (index < 0 || index >= filled())
            throw new UserException("Attempting to access invalid element: " + index + " of " + filled());
        return values.get(index);
    }

    @Override
    public void addAll(Stream<Either<String, TemporalAccessor>> items) throws InternalException
    {
        for (Either<String, TemporalAccessor> item : Utility.iterableStream(items))
        {
            TemporalAccessor t = item.either(err -> {
                setError(values.size(), err);
                return Instant.EPOCH;
            }, v -> v);
            this.values.add(pool.pool(DataTypeUtility.value(dateTimeInfo, t)));
        }
    }

    @OnThread(Tag.Any)
    public synchronized DataTypeValue getType()
    {
        /*
        if (longs != null)
        {
            for (long l : longs)
                if (l == SEE_BIGDEC)
                    return v -> v.number();
        }
        */
        if (dataType == null)
        {
            dataType = DataTypeValue.date(dateTimeInfo, new GetValue<@Value TemporalAccessor>()
            {
                @Override
                public @Value TemporalAccessor getWithProgress(int i, @Nullable ProgressListener prog) throws UserException, InternalException
                {
                    return TemporalColumnStorage.this.get(i, prog);
                }

                @Override
                public void set(int index, @Value TemporalAccessor value) throws InternalException, UserException
                {
                    values.set(index, value);
                }
            });
        }
        return dataType;
    }

    @Override
    public SimulationRunnable _insertRows(int index, List<@Nullable TemporalAccessor> items) throws InternalException
    {
        if (index < 0 || index > values.size())
            throw new InternalException("Trying to insert rows at invalid index: " + index + " length is: " + values.size());
        values.ensureCapacity(values.size() + items.size());
        for (@Nullable TemporalAccessor item : items)
        {
            if (item == null)
            {
                @SuppressWarnings("value")
                @Value Instant dummy = Instant.EPOCH;
                values.add(pool.pool(dummy));
            }
            else
                values.add(pool.pool(DataTypeUtility.value(dateTimeInfo, item)));
        }
        int count = items.size();
        return () -> _removeRows(index, count);
    }

    @Override
    public SimulationRunnable _removeRows(int index, int count) throws InternalException
    {
        if (index < 0 || index > values.size())
            throw new InternalException("Trying to remove rows at invalid index: " + index + " length is: " + values.size());
        List<@Value TemporalAccessor> old = new ArrayList<>(values.subList(index, index + count));
        values.subList(index, index + count).clear();
        return () -> values.addAll(index, old);
    }
}
