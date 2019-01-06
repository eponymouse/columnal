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
import utility.Either;
import utility.ExBiConsumer;
import utility.SimulationRunnable;
import utility.Utility;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.stream.Stream;

/**
 * Created by neil on 08/12/2016.
 */
public class BooleanColumnStorage extends SparseErrorColumnStorage<Boolean> implements ColumnStorage<Boolean>
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
        this.type = DataTypeValue.bool(new GetValue<@Value Boolean>()
        {
            @Override
            public @Value Boolean getWithProgress(int i, ProgressListener progressListener) throws UserException, InternalException
            {
                return BooleanColumnStorage.this.getWithProgress(i, progressListener);
            }

            @Override
            public @OnThread(Tag.Simulation) void set(int index, @Value Boolean value) throws InternalException
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
            throw new UserException("Attempting to access invalid element: " + i + " of " + filled());
        return DataTypeUtility.value(data.get(i));
    }

    @Override
    public int filled(@UnknownInitialization(Object.class) BooleanColumnStorage this)
    {
        return length;
    }

    @Override
    public void addAll(Stream<Either<String, Boolean>> items) throws InternalException
    {
        for (Either<String, Boolean> item : Utility.iterableStream(items))
        {
            item.either_(e -> setError(length, e), b -> data.set(length, b));
            length += 1;
        }
    }

    @OnThread(Tag.Any)
    public DataTypeValue getType()
    {
        return type;
    }

    @Override
    public SimulationRunnable _insertRows(int index, List<@Nullable Boolean> items) throws InternalException, UserException
    {
        int count = items.size();
        // Could probably do this faster:
        // Shift up the existing bits; go downwards to avoid overlap issues:
        for (int i = length - 1; i >= index;i--)
            data.set(i + count, data.get(i));
        // Initialise bits to false:
        data.clear(index, index + count);
        for (int i = 0; i < count; i++)
        {
            int destIndex = index + i;
            Boolean item = items.get(i);
            if (item != null)
                data.set(destIndex, item);
        }
        length += count;
        return () -> removeRows(index, count);
    }

    @Override
    public SimulationRunnable _removeRows(int index, int count) throws InternalException, UserException
    {
        // Could save some memory here:
        BitSet old = (BitSet)data.clone();
        for (int i = index; i < length - count; i++)
            data.set(i, data.get(i + count));
        length -= count;
        return () -> {
            data.clear();
            data.or(old);
            length += count;
        };
    }
}
