package records.data;

import records.error.InternalException;
import utility.DumbStringPool;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by neil on 04/11/2016.
 */
public class StringColumnStorage implements ColumnStorage<String>
{
    private final ArrayList<String> values;
    private final DumbStringPool pool = new DumbStringPool(1000);

    public StringColumnStorage()
    {
        values = new ArrayList<>();
    }

    @Override
    public int filled()
    {
        return values.size();
    }

    @Override
    public String get(int index) throws InternalException
    {
        return values.get(index);
    }

    @Override
    public void addAll(List<String> items) throws InternalException
    {
        this.values.ensureCapacity(this.values.size() + items.size());
        for (String s : items)
        {
            this.values.add(pool.pool(s));
        }
    }
}
