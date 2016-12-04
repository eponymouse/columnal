package records.data;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Collections;

/**
 * Created by neil on 06/11/2016.
 */
public abstract class CalculatedStringColumn extends CalculatedColumn
{
    @OnThread(Tag.Any)
    private final DataType copyType;
    @MonotonicNonNull
    @OnThread(value = Tag.Any, requireSynchronized = true)
    private DataTypeValue type;
    protected final StringColumnStorage cache;
    public CalculatedStringColumn(RecordSet recordSet, ColumnId name, DataType copyType)
    {
        super(recordSet, name);
        this.copyType = copyType;
        cache = new StringColumnStorage();
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
        if (type == null)
        {
            type = copyType.copy((i, prog) ->
            {
                fillCacheWithProgress(i, prog);
                return Collections.singletonList(cache.get(i));
            });
        }
        return type;
    }
}
