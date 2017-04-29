package records.data;

import annotation.qual.Value;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import records.data.Column.ProgressListener;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import records.gui.DisplayValue;
import records.gui.EnteredDisplayValue;
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
    private final @Nullable ExBiConsumer<Integer, @Nullable ProgressListener> beforeGet;

    public BooleanColumnStorage(@Nullable ExBiConsumer<Integer, @Nullable ProgressListener> beforeGet)
    {
        this.beforeGet = beforeGet;
        this.type = DataTypeValue.bool(this::getWithProgress);
    }

    public BooleanColumnStorage()
    {
        this(null);
    }

    @RequiresNonNull({"data"})
    private @Value Boolean getWithProgress(@UnknownInitialization(Object.class) BooleanColumnStorage this, int i, @Nullable ProgressListener progressListener) throws UserException, InternalException
    {
        if (beforeGet != null)
            beforeGet.accept(i, progressListener);
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
    public DisplayValue storeValue(EnteredDisplayValue writtenValue) throws InternalException, UserException
    {
        @Value boolean value;
        switch (writtenValue.getString())
        {
            case "true": value = true; break;
            case "false": value = false; break;
            default: throw new UserException("Invalid boolean value: \"" + writtenValue.getString() + "\"");
        }
        data.set(writtenValue.getRowIndex(), value);
        return DataTypeUtility.display(writtenValue.getRowIndex(), getType(), value);
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
