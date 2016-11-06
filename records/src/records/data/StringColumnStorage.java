package records.data;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;
import utility.DumbStringPool;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by neil on 04/11/2016.
 */
public class StringColumnStorage implements ColumnStorage<String>
{
    private final ArrayList<String> values;
    private final DumbStringPool pool = new DumbStringPool(1000);
    @MonotonicNonNull
    private DataType dataType;

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
    public void clear()
    {
        values.clear();
        pool.clear();
    }

    @Override
    public String get(int index) throws InternalException
    {
        return values.get(index);
    }

    @Override
    public void addAll(List<@Nullable String> items) throws InternalException
    {
        this.values.ensureCapacity(this.values.size() + items.size());
        for (String s : items)
        {
            if (s == null)
                s = "";
            this.values.add(pool.pool(s));
        }
    }

    public DataType getType()
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
            dataType = new DataType()
            {
                @Override
                public <R> R apply(DataTypeVisitorGet<R> visitor) throws InternalException, UserException
                {
                    return visitor.text((i, prog) -> get(i));
                }
            };
        }
        return dataType;
    }
}
