package records.data;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 06/11/2016.
 */
public abstract class CalculatedStringColumn extends CalculatedColumn
{
    protected final StringColumnStorage cache;

    @SuppressWarnings("initialization")
    public CalculatedStringColumn(RecordSet recordSet, ColumnId name)
    {
        super(recordSet, name);
        cache = new StringColumnStorage((index, prog) -> fillCacheWithProgress(index, prog));
    }

    @Override
    protected int getCacheFilled()
    {
        return cache.filled();
    }

    @Override
    @OnThread(Tag.Any)
    public synchronized DataTypeValue getType() throws UserException, InternalException
    {
        return cache.getType();
    }
}
