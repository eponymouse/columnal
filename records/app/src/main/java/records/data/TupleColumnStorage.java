package records.data;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column.ProgressListener;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExBiConsumer;
import utility.Utility;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by neil on 03/01/2017.
 */
public class TupleColumnStorage implements ColumnStorage<Object[]>
{
    // For tuples, each element is one column-major part of tuple (i.e. a column)
    private final ImmutableList<ColumnStorage<?>> storage;
    @OnThread(Tag.Any)
    private final DataTypeValue type;

    public TupleColumnStorage(List<DataType> innerToCopy) throws InternalException
    {
        this(innerToCopy, null);
    }

    public TupleColumnStorage(List<DataType> innerToCopy, @Nullable ExBiConsumer<Integer, @Nullable ProgressListener> beforeGet) throws InternalException
    {
        ArrayList<ColumnStorage<?>> buildList = new ArrayList<>();
        for (DataType anInnerToCopy : innerToCopy)
        {
            buildList.add(DataTypeUtility.makeColumnStorage(anInnerToCopy, beforeGet));
        }
        storage = ImmutableList.copyOf(buildList);
        type = DataTypeValue.tupleV(Utility.<ColumnStorage<?>, DataTypeValue>mapList(storage, s -> s.getType()));
    }

    @Override
    public int filled()
    {
        return storage.stream().mapToInt(ColumnStorage::filled).min().orElse(0);
    }

    /*
    public Object @NonNull [] get(int index) throws InternalException, UserException
    {
        Object[] r = new Object[storage.size()];
        for (int i = 0; i < r.length; i++)
        {
            r[i] = storage.get(i).get(index);
        }
        return r;
    }*/

    @Override
    public void addAll(List<Object[]> items) throws InternalException
    {
        if (type.isTuple())
        {
            // Each Object[] is one tuple record, add each element to each storage
            for (Object[] tuple : items)
            {
                for (int i = 0; i < tuple.length; i++)
                {
                    ((ColumnStorage)storage.get(i)).add(tuple[i]);
                }
            }
        }
    }

    @Override
    @OnThread(Tag.Any)
    public DataTypeValue getType()
    {
        return type;
    }
}
