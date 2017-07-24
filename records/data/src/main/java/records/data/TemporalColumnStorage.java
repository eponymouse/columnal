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
import utility.SimulationRunnable;
import utility.Utility;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Created by neil on 04/11/2016.
 */
public class TemporalColumnStorage implements ColumnStorage<TemporalAccessor>
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
            throw new InternalException("Attempting to access invalid element: " + index + " of " + filled());
        return values.get(index);
    }

    @Override
    public void addAll(List<TemporalAccessor> items) throws InternalException
    {
        this.values.ensureCapacity(this.values.size() + items.size());
        for (TemporalAccessor t : items)
        {
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
            dataType = DataTypeValue.date(dateTimeInfo, new GetValue<TemporalAccessor>()
            {
                @Override
                public TemporalAccessor getWithProgress(int i, @Nullable ProgressListener prog) throws UserException, InternalException
                {
                    return TemporalColumnStorage.this.get(i, prog);
                }

                @Override
                public void set(int index, TemporalAccessor value) throws InternalException, UserException
                {
                    values.set(index, value);
                }
            });
        }
        return dataType;
    }

    @Override
    public SimulationRunnable insertRows(int index, List<TemporalAccessor> items) throws InternalException
    {
        if (index < 0 || index > values.size())
            throw new InternalException("Trying to insert rows at invalid index: " + index + " length is: " + values.size());
        values.addAll(index, Utility.mapListInt(items, pool::pool));
        int count = items.size();
        return () -> removeRows(index, count);
    }

    @Override
    public SimulationRunnable removeRows(int index, int count) throws InternalException
    {
        if (index < 0 || index > values.size())
            throw new InternalException("Trying to remove rows at invalid index: " + index + " length is: " + values.size());
        List<@Value TemporalAccessor> old = new ArrayList<>(values.subList(index, index + count));
        values.subList(index, index + count).clear();
        return () -> values.addAll(index, old);
    }
}
