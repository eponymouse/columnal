package records.data;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column.ProgressListener;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExBiConsumer;
import utility.Pair;
import utility.Utility.ListEx;

import java.util.ArrayList;
import java.util.List;

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
public class ArrayColumnStorage implements ColumnStorage<ListEx>
{
    // For arrays, each element is storage for an individual array element (i.e. a row)
    // Thus confusing, ColumnStorage here is being used as RowStorage (think of it as VectorStorage)
    private final ArrayList<ListEx> storage = new ArrayList<>();
    @OnThread(Tag.Any)
    private final DataTypeValue type;

    // Constructor for array version
    public ArrayColumnStorage(@Nullable DataType innerToCopy, @Nullable ExBiConsumer<Integer, @Nullable ProgressListener> beforeGet) throws InternalException
    {
        if (innerToCopy == null)
            this.type = DataTypeValue.arrayV();
        else
        {
            DataType innerFinal = innerToCopy;
            this.type = DataTypeValue.arrayV(innerToCopy, (i, prog) ->
            {
                if (beforeGet != null)
                    beforeGet.accept(i, prog);
                try
                {
                    ListEx list = storage.get(i);
                    return new Pair<>(list.size(), innerFinal.fromCollapsed((i2, prog2) -> list.get(i2)));
                } catch (ClassCastException e)
                {
                    throw new InternalException("Incorrect type in array storage", e);
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
    public void addAll(List<ListEx> items) throws InternalException
    {
        for (Object item : items)
        {
            if (!(item instanceof ListEx))
                throw new InternalException("Not ListEx: " + item.getClass());
        }
        storage.addAll(items);
    }

    @Override
    @OnThread(Tag.Any)
    public DataTypeValue getType()
    {
        return type;
    }
}
