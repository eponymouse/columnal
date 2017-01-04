package records.data;

import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by neil on 03/01/2017.
 */
public class NestedColumnStorage implements ColumnStorage<ColumnStorage<?>>
{
    // For tuples, each element is one column-major part of tuple (i.e. a column)
    // For arrays, each element is storage for an individual array element (i.e. a row)
    private final ArrayList<ColumnStorage<?>> storage = new ArrayList<>();
    @OnThread(Tag.Any)
    private final DataTypeValue type;

    // Constructor for array version
    public NestedColumnStorage(DataType innerToCopy) throws InternalException
    {
        type = DataTypeValue.array(innerToCopy.copy((i, prog) -> {
            ColumnStorage<?> oneArrayStorage = this.storage.get(i);
            return oneArrayStorage.getFullList(oneArrayStorage.getType().getArrayLength());
        }), storage::size);
    }

    // Constructor for tuple version
    public NestedColumnStorage(List<DataType> innerToCopy) throws InternalException
    {
        for (DataType anInnerToCopy : innerToCopy)
        {
            this.storage.add(DataTypeUtility.makeColumnStorage(anInnerToCopy));
        }
        type = DataTypeValue.tupleV(Utility.mapList(storage, s -> s.getType()));
    }

    @Override
    public int filled()
    {
        return storage.size();
    }

    @Override
    public ColumnStorage<?> get(int index) throws InternalException, UserException
    {
        return storage.get(index);
    }

    @Override
    public void addAll(List<ColumnStorage<?>> items) throws InternalException
    {
        storage.addAll(items);
    }

    @Override
    @OnThread(Tag.Any)
    public DataTypeValue getType()
    {
        return type;
    }
}
