package records.data;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
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
    private final StringColumnStorage storage;
    @MonotonicNonNull
    private DataType dataType;

    public MemoryStringColumn(RecordSet recordSet, String title, List<String> values) throws InternalException
    {
        super(recordSet);
        this.title = title;
        this.storage = new StringColumnStorage();
        this.storage.addAll(values);
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
    public DataType getType()
    {
        if (dataType == null)
        {
            dataType = new DataType()
            {
                @Override
                public <R> R apply(DataTypeVisitorGet<R> visitor) throws UserException, InternalException
                {
                    return visitor.text(MemoryStringColumn.this::getWithProgress);
                }
            };
        }
        return dataType;
    }

    private String getWithProgress(int index, @Nullable ProgressListener progressListener) throws UserException, InternalException
    {
        return storage.get(index);
    }
}
