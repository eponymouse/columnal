package records.data;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.DumbObjectPool;

import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by neil on 04/11/2016.
 */
public class DateColumnStorage implements ColumnStorage<Temporal>
{
    private final ArrayList<Temporal> values;
    private final DumbObjectPool<Temporal> pool = new DumbObjectPool<>(Temporal.class, 1000);
    @OnThread(Tag.Any)
    private final DateTimeInfo dateTimeInfo;
    @MonotonicNonNull
    @OnThread(value = Tag.Any,requireSynchronized = true)
    private DataTypeValue dataType;

    public DateColumnStorage(DateTimeInfo dateTimeInfo)
    {
        values = new ArrayList<>();
        this.dateTimeInfo = dateTimeInfo;
    }

    @Override
    public int filled()
    {
        return values.size();
    }

    @Override
    public Temporal get(int index) throws InternalException
    {
        if (index < 0 || index >= filled())
            throw new InternalException("Attempting to access invalid element: " + index + " of " + filled());
        return values.get(index);
    }

    @Override
    public void addAll(List<Temporal> items) throws InternalException
    {
        this.values.ensureCapacity(this.values.size() + items.size());
        for (Temporal t : items)
        {
            this.values.add(pool.pool(t));
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
            dataType = DataTypeValue.date(dateTimeInfo, (i, prog) -> get(i));
        }
        return dataType;
    }

    public List<Temporal> getShrunk(int shrunkLength)
    {
        return values.subList(0, shrunkLength);
    }
}
