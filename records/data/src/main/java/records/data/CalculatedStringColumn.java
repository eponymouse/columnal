package records.data;

import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 06/11/2016.
 */
public abstract class CalculatedStringColumn extends CalculatedColumn<StringColumnStorage>
{
    protected final StringColumnStorage cache;

    @SuppressWarnings("initialization")
    public CalculatedStringColumn(RecordSet recordSet, ColumnId name)
    {
        super(recordSet, name);
        cache = new StringColumnStorage(this);
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
