package records.data;

import annotation.qual.Value;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import records.data.Column.ProgressListener;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.data.datatype.DataTypeValue.GetValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExBiConsumer;

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
    private final @Nullable BeforeGet<BooleanColumnStorage> beforeGet;

    @SuppressWarnings("initialization") // getWithProgress method reference
    public BooleanColumnStorage(@Nullable BeforeGet<BooleanColumnStorage> beforeGet)
    {
        this.beforeGet = beforeGet;
        this.type = DataTypeValue.bool(new GetValue<Boolean>()
        {
            @Override
            public Boolean getWithProgress(int i, ProgressListener progressListener) throws UserException, InternalException
            {
                return BooleanColumnStorage.this.getWithProgress(i, progressListener);
            }

            @Override
            public @OnThread(Tag.Simulation) void set(int index, Boolean value) throws InternalException
            {
                data.set(index, value);
            }
        });
    }

    public BooleanColumnStorage()
    {
        this(null);
    }


    private @Value Boolean getWithProgress(int i, @Nullable ProgressListener progressListener) throws UserException, InternalException
    {
        if (beforeGet != null)
            beforeGet.beforeGet(this, i, progressListener);
        if (i < 0 || i >= filled())
            throw new InternalException("Attempting to access invalid element: " + i + " of " + filled());
        return DataTypeUtility.value(data.get(i));
    }

    @Override
    public int filled(@UnknownInitialization(Object.class) BooleanColumnStorage this)
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

    @Override
    public void addRow() throws InternalException, UserException
    {
        add(false);
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
