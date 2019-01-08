package records.data;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column.ProgressListener;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeValue;
import records.data.datatype.DataTypeValue.GetValue;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.ExBiConsumer;
import utility.Pair;
import utility.SimulationRunnable;
import utility.Utility;
import utility.Utility.ListEx;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * For some reason, thi is the difficult one to wrap your head around.  An array
 * is a vector of items, which could be a vector of strings, a vector of tagged items,
 * or a vector of arrays.  Several crucial facts:
 *
 *  - Since this is a column storage, each item is one row, which is itself an array.
 *  - This storage only stores the head (in Haskell terms)
 *    of the item, not the full item, which might be cached elsewhere (e.g. if this storage
 *    is the result of a sort, no point duplicating the array).
 *
 * Thus the storage is just a list of DataTypeValue, i.e. accessors of the array content.
 */
public class ArrayColumnStorage extends SparseErrorColumnStorage<ListEx> implements ColumnStorage<ListEx>
{
    // We are a column storage.  Each element here is one row, which is also
    // a list because it is an array.  We are a list of lists (column of arrays)
    private final ArrayList<ListEx> storage = new ArrayList<>();
    @OnThread(Tag.Any)
    private final DataTypeValue type;

    // Constructor for array version
    @SuppressWarnings("initialization") // Calling beforeGet
    public ArrayColumnStorage(@Nullable DataType innerToCopy, @Nullable BeforeGet<ArrayColumnStorage> beforeGet) throws InternalException
    {
        if (innerToCopy == null)
            this.type = DataTypeValue.arrayV();
        else
        {
            DataType innerFinal = innerToCopy;
            this.type = DataTypeValue.arrayV(innerToCopy, new GetValueOrError<Pair<Integer, DataTypeValue>>()
            {
                @Override
                public Pair<Integer, DataTypeValue> _getWithProgress(int i, ProgressListener prog) throws UserException, InternalException
                {
                    if (beforeGet != null)
                        beforeGet.beforeGet(ArrayColumnStorage.this, i, prog);
                    try
                    {
                        ListEx list = storage.get(i);
                        return new Pair<>(list.size(), innerFinal.fromCollapsed((i2, prog2) -> list.get(i2)));
                    }
                    catch (ClassCastException e)
                    {
                        throw new InternalException("Incorrect type in array storage", e);
                    }
                }

                @Override
                public void _set(int index, Pair<Integer, DataTypeValue> v) throws InternalException, UserException
                {
                    storage.set(index, new ListEx()
                    {
                        @Override
                        public int size() throws InternalException, UserException
                        {
                            return v.getFirst();
                        }

                        @Override
                        public @Value Object get(int index) throws InternalException, UserException
                        {
                            return v.getSecond().getCollapsed(index);
                        }
                    });
                }
            });
        }
    }

    @Override
    public int filled()
    {
        return storage.size();
    }
/*
    public List<Object> get(int index) throws InternalException, UserException
    {
        return (List)storage.get(index).getFullList(type.getArrayLength(index));
    }*/

    @Override
    public void addAll(Stream<Either<String, ListEx>> items) throws InternalException
    {
        for (Either<String, ListEx> item : Utility.iterableStream(items))
        {
            storage.add(item.either(err -> {
                setError(storage.size(), err);
                return ListEx.empty();
            }, l -> l));
        }
    }

    @Override
    @OnThread(Tag.Any)
    public DataTypeValue getType()
    {
        return type;
    }

    @Override
    public SimulationRunnable _insertRows(int index, List<@Nullable ListEx> items) throws InternalException
    {
        storage.ensureCapacity(storage.size() + items.size());
        for (ListEx item : items)
        {
            if (item == null)
                item = ListEx.empty();
            storage.add(item);
        }
        int count = items.size();
        return () -> _removeRows(index, count);
    }

    @Override
    public SimulationRunnable _removeRows(int index, int count) throws InternalException
    {
        List<ListEx> old = new ArrayList<>(storage.subList(index, index + count));
        storage.subList(index, index + count).clear();
        return () -> storage.addAll(index, old);
    }
}
