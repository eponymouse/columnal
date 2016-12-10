package records.data;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column.ProgressListener;
import records.data.datatype.DataType.Kind;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

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

    @SuppressWarnings("initialization")
    public BooleanColumnStorage()
    {
        this.type = DataTypeValue.bool(this::getWithProgress);
    }

    private Boolean getWithProgress(int i, @Nullable ProgressListener progressListener) throws UserException, InternalException
    {
        return get(i);
    }

    @Override
    public int filled()
    {
        return length;
    }

    @Override
    public Boolean get(int index) throws InternalException, UserException
    {
        if (index < 0 || index >= filled())
            throw new InternalException("Attempting to access invalid element: " + index + " of " + filled());
        return data.get(index);
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
