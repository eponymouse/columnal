package records.data;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import records.data.datatype.DataType;
import records.error.InternalException;
import records.error.UserException;

import java.util.Collections;

/**
 * Created by neil on 05/11/2016.
 */
public abstract class CalculatedNumericColumn extends CalculatedColumn
{
    private final DataType copyType;
    @MonotonicNonNull
    private DataType type;
    protected final NumericColumnStorage cache;

    public CalculatedNumericColumn(RecordSet recordSet, String name, DataType copyType, Column... dependencies) throws InternalException, UserException
    {
        super(recordSet, name, dependencies);
        this.copyType = copyType;
        cache = new NumericColumnStorage();
    }

    @Override
    protected void clearCache() throws InternalException
    {
        cache.clear();
    }

    @Override
    protected int getCacheFilled()
    {
        return cache.filled();
    }

    @Override
    public DataType getType() throws UserException, InternalException
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
