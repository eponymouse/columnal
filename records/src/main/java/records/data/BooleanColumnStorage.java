package records.data;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column.ProgressListener;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExBiConsumer;
import utility.Utility;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Created by neil on 08/12/2016.
 */
public class BooleanColumnStorage implements ColumnStorage<Boolean>
{
    private int length = 0;
    private final BitSet data = new BitSet();
    @OnThread(Tag.Any)
    private final DataTypeValue type;
    private final @Nullable ExBiConsumer<Integer, @Nullable ProgressListener> beforeGet;

    @SuppressWarnings("initialization")
    public BooleanColumnStorage(@Nullable ExBiConsumer<Integer, @Nullable ProgressListener> beforeGet)
    {
        this.type = DataTypeValue.bool(this::getWithProgress);
        this.beforeGet = beforeGet;
    }

    public BooleanColumnStorage()
    {
        this(null);
    }

    private @Value Boolean getWithProgress(int i, @Nullable ProgressListener progressListener) throws UserException, InternalException
    {
        if (beforeGet != null)
            beforeGet.accept(i, progressListener);
        if (i < 0 || i >= filled())
            throw new InternalException("Attempting to access invalid element: " + i + " of " + filled());
        return Utility.value(data.get(i));
    }

    @Override
    public int filled()
    {
        return length;
    }

    @Override
    public void addAll(List<Boolean> items) throws InternalException
    {
        for (Boolean item : items)
        {
            data.set(length, item);
            length += 1;
        }
    }

    @OnThread(Tag.Any)
    public DataTypeValue getType()
    {
        return type;
    }

    public List<Boolean> getShrunk(int shrunkLength)
    {
        List<Boolean> r = new ArrayList<>(shrunkLength);
        for (int i = 0;i < shrunkLength; i++)
        {
            r.add(data.get(i));
        }
        return r;
    }
}
