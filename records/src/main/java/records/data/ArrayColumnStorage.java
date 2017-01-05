package records.data;

import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExFunction;
import utility.ExSupplier;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by neil on 03/01/2017.
 */
public class ArrayColumnStorage implements ColumnStorage<List<Object>>
{
    // For arrays, each element is storage for an individual array element (i.e. a row)
    // Thus confusing, ColumnStorage here is being used as RowStorage (think of it as VectorStorage)
    private final ArrayList<ColumnStorage<?>> storage = new ArrayList<>();
    private final DataType innerType;
    @OnThread(Tag.Any)
    private final DataTypeValue type;

    // Constructor for array version
    public ArrayColumnStorage(DataType innerToCopy, ExFunction<Integer, Integer> getArrayLength) throws InternalException
    {
        innerType = innerToCopy.copy((i, prog) -> {
            ColumnStorage<?> oneArrayStorage = this.storage.get(i);
            return (List)oneArrayStorage.getFullList(oneArrayStorage.getType().getArrayLength(i));
        });
        type = DataTypeValue.array(innerType, getArrayLength);
    }

    // Version which assumes all arrays are inserted in their entirety, not loaded later.
    public ArrayColumnStorage(DataType innerToCopy) throws InternalException
    {
        innerType = innerToCopy.copy((i, prog) -> {
            ColumnStorage<?> oneArrayStorage = this.storage.get(i);
            return (List)oneArrayStorage.getFullList(oneArrayStorage.getType().getArrayLength(i));
        });
        type = DataTypeValue.array(innerType, i -> storage.get(i).filled());
    }

    @Override
    public int filled()
    {
        return storage.size();
    }

    @Override
    public List<Object> get(int index) throws InternalException, UserException
    {
        return (List)storage.get(index).getFullList(type.getArrayLength(index));
    }

    @Override
    public void addAll(List<List<Object>> items) throws InternalException
    {
        // Each List<Object> is one array, add a new storage for that array to our list
        for (List<Object> item : items)
        {
            ColumnStorage<?> row = DataTypeUtility.makeColumnStorage(innerType);
            row.addAll((List)item);
            storage.add(row);
        }
    }

    @Override
    @OnThread(Tag.Any)
    public DataTypeValue getType()
    {
        return type;
    }
}
