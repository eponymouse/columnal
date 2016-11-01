package records.data;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.DumbStringPool;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by neil on 31/10/2016.
 */
public class MemoryStringColumn extends Column
{
    private final String title;
    private final List<String> values;
    private final DumbStringPool pool = new DumbStringPool(1000);

    public MemoryStringColumn(RecordSet recordSet, String title, List<String> values)
    {
        super(recordSet);
        this.title = title;
        this.values = new ArrayList<>(values.size());
        int nextSkip = 0;
        for (String s : values)
        {
            this.values.add(pool.pool(s));
        }
    }

    @Override
    @OnThread(Tag.Any)
    public String getName()
    {
        return title;
    }

    @Override
    public long getVersion()
    {
        return 1;
    }

    @Override
    public Class<?> getType()
    {
        return String.class;
    }

    @Override
    public Object getWithProgress(int index, @Nullable ProgressListener progressListener) throws UserException, InternalException
    {
        return values.get(index);
    }
}
