package records.data;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.DumbObjectPool;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by neil on 04/11/2016.
 */
public class StringColumnStorage implements ColumnStorage<String>
{
    private final ArrayList<String> values;
    private final DumbObjectPool<String> pool = new DumbObjectPool<>(String.class, 1000);
    @MonotonicNonNull
    @OnThread(value = Tag.Any,requireSynchronized = true)
    private DataTypeValue dataType;

    public StringColumnStorage()
    {
        values = new ArrayList<>();
    }

    @Override
    public int filled()
    {
        return values.size();
    }
    
    @Override
    public String get(int index) throws InternalException
    {
        return values.get(index);
    }

    @Override
    public void addAll(List<String> items) throws InternalException
    {
        this.values.ensureCapacity(this.values.size() + items.size());
        for (String s : items)
        {
            this.values.add(pool.pool(s));
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
            dataType = DataTypeValue.text((i, prog) -> get(i));
        }
        return dataType;
    }
}
