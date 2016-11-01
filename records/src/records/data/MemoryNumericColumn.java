package records.data;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.type.NumericColumnType;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.List;
import java.util.Optional;

/**
 * Created by neil on 31/10/2016.
 */
public class MemoryNumericColumn extends Column
{
    private final String title;
    private final NumericColumnStorage storage = new NumericColumnStorage();

    public MemoryNumericColumn(RecordSet rs, String title, NumericColumnType type, List<Integer> skipIndexes, List<String> values) throws InternalException
    {
        super(rs);
        this.title = title;
        int nextSkip = 0;
        for (int i = 0; i < values.size(); i++)
        {
            if (nextSkip < skipIndexes.size() && skipIndexes.get(nextSkip) == i)
            {
                nextSkip += 1;
            }
            // Add it if it can't be blank, or if isn't blank
            else if (!type.mayBeBlank || !values.get(i).isEmpty())
            {
                String s = values.get(i);
                storage.add(type.removePrefix(s));
            }
            else
            {
                storage.addBlank();
            }
        }
    }

    @Override
    public @OnThread(Tag.Any) String getName()
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
        return storage.hasBlanks() ? Optional.class : Number.class;
    }

    @Override
    @SuppressWarnings("nullness")
    public Object getWithProgress(int index, @Nullable ProgressListener progressListener) throws UserException, InternalException
    {
        return storage.hasBlanks() ? Optional.ofNullable(storage.get(index)) : storage.get(index);
    }
}
