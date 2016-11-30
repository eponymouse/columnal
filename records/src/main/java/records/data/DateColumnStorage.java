package records.data;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
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
    @MonotonicNonNull
    @OnThread(value = Tag.Any,requireSynchronized = true)
    private DataTypeValue dataType;

    public DateColumnStorage()
    {
        values = new ArrayList<>();
    }

    @Override
    public int filled()
    {
        return values.size();
    }

    @Override
    public void clear()
    {
        values.clear();
        pool.clear();
    }

    @Override
    public Temporal get(int index) throws InternalException
    {
        return values.get(index);
    }

    @Override
    public void addAll(List<@Nullable Temporal> items) throws InternalException
    {
        this.values.ensureCapacity(this.values.size() + items.size());
        for (Temporal t : items)
        {
            if (t != null)
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
            dataType = DataTypeValue.date((i, prog) -> get(i));
        }
        return dataType;
    }
}
