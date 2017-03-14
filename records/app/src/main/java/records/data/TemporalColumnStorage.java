package records.data;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column.ProgressListener;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import records.gui.DisplayValue;
import records.gui.EnteredDisplayValue;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.DumbObjectPool;
import utility.ExBiConsumer;

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
    private final @Nullable ExBiConsumer<Integer, @Nullable ProgressListener> beforeGet;

    public TemporalColumnStorage(DateTimeInfo dateTimeInfo) throws InternalException
    {
        this(dateTimeInfo, null);
    }

    public TemporalColumnStorage(DateTimeInfo dateTimeInfo, @Nullable ExBiConsumer<Integer, @Nullable ProgressListener> beforeGet) throws InternalException
    {
        this.values = new ArrayList<>();
        this.pool = new DumbObjectPool<>((Class<@Value TemporalAccessor>)TemporalAccessor.class, 1000, (Comparator<@Value TemporalAccessor>)dateTimeInfo.getComparator());
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
            beforeGet.accept(index, progressListener);
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
            dataType = DataTypeValue.date(dateTimeInfo, (i, prog) -> get(i, prog));
        }
        return dataType;
    }

    @Override
    public DisplayValue storeValue(EnteredDisplayValue writtenValue) throws InternalException, UserException
    {
        throw new UnimplementedException();
    }

    @Override
    public void addRow() throws InternalException, UserException
    {
        throw new UnimplementedException();
    }
}
