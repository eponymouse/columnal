package records.data;

import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Workers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Created by neil on 21/10/2016.
 */
public abstract class CalculatedColumn<T extends Object> extends Column
{
    private final String name;
    private final ArrayList<T> cachedValues = new ArrayList<T>();
    private final ArrayList<Column> dependencies;
    // Version of each of the dependencies at last calculation:
    private final Map<Column, Long> calcVersions = new IdentityHashMap<>();
    private long version = 1;

    public CalculatedColumn(RecordSet recordSet, String name, Column... dependencies)
    {
        super(recordSet);
        this.name = name;
        this.dependencies = new ArrayList<>(Arrays.asList(dependencies));
    }

    @Override
    public final T get(int index) throws UserException, InternalException
    {
        if (checkCacheValid())
        {
            if (index < cachedValues.size())
                return cachedValues.get(index);
        }
        else
        {
            cachedValues.clear();
            version += 1;
        }
        // Fetch values:
        for (int i = cachedValues.size(); i <= index; i++)
        {
            cachedValues.add(calculate(i));
            if (isSingleExpensive() || (i % 100) == 0)
            {
                gotMore();
                Workers.maybeYield();
            }
        }
        gotMore();
        return cachedValues.get(index);
    }

    protected abstract boolean isSingleExpensive();

    @Override
    protected final double indexProgress(int index) throws UserException
    {
        if (index < cachedValues.size())
            return 2.0;
        else if (index == 0)
            return 0.0;
        else
            return (double)(cachedValues.size() - 1) / (double)index;
    }

    private boolean checkCacheValid()
    {
        boolean allValid = true;
        for (Column c : dependencies)
        {
            Long lastVer = calcVersions.get(c);
            if (lastVer == null || lastVer.longValue() != c.getVersion())
            {
                calcVersions.put(c, c.getVersion());
                allValid = false;
            }
        }
        return allValid;
    }

    @Override
    @OnThread(Tag.Any)
    public final String getName()
    {
        return name;
    }

    protected abstract T calculate(int index) throws UserException, InternalException;

    @Override
    public final long getVersion()
    {
        return version;
    }
}
