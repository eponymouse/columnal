package records.data;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column.ProgressListener;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import records.gui.DisplayValue;
import records.gui.EnteredDisplayValue;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.DumbObjectPool;
import utility.ExBiConsumer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 04/11/2016.
 */
public class StringColumnStorage implements ColumnStorage<String>
{
    private final ArrayList<@Value String> values;
    private final DumbObjectPool<@Value String> pool = new DumbObjectPool<>((Class<@Value String>)(Class)String.class, 1000, null);
    private final @Nullable ExBiConsumer<Integer, @Nullable ProgressListener> beforeGet;
    @MonotonicNonNull
    @OnThread(value = Tag.Any,requireSynchronized = true)
    private DataTypeValue dataType;

    public StringColumnStorage(@Nullable ExBiConsumer<Integer, @Nullable ProgressListener> beforeGet)
    {
        values = new ArrayList<>();
        this.beforeGet = beforeGet;
    }

    public StringColumnStorage()
    {
        this(null);
    }

    @Override
    public int filled()
    {
        return values.size();
    }
    
    public @Value String get(int index, @Nullable ProgressListener progressListener) throws InternalException, UserException
    {
        if (beforeGet != null)
            beforeGet.accept(index, progressListener);
        if (index < 0 || index >= values.size())
            throw new InternalException("Attempting to access invalid element: " + index + " of " + values.size());
        return values.get(index);
    }

    @Override
    public void addAll(List<String> items) throws InternalException
    {
        this.values.ensureCapacity(this.values.size() + items.size());
        for (String s : items)
        {
            this.values.add(pool.pool(DataTypeUtility.value(s)));
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
            dataType = DataTypeValue.text((i, prog) -> get(i, prog));
        }
        return dataType;
    }

    @Override
    public DisplayValue storeValue(EnteredDisplayValue writtenValue) throws InternalException, UserException
    {
        values.set(writtenValue.getRowIndex(), pool.pool(writtenValue.getString()));
        return new DisplayValue(writtenValue.getRowIndex(), writtenValue.getString());
    }

    @Override
    public void addRow() throws InternalException, UserException
    {
        addAll(Collections.singletonList(""));
    }
}
