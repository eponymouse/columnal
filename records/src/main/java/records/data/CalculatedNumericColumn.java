package records.data;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import records.data.datatype.DataType;
import records.data.datatype.DataType.NumberInfo;
import records.data.datatype.DataType.SpecificDataTypeVisitor;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 05/11/2016.
 */
public abstract class CalculatedNumericColumn extends CalculatedColumn
{
    protected final NumericColumnStorage cache;

    @SuppressWarnings("initialization")
    public CalculatedNumericColumn(RecordSet recordSet, ColumnId name, NumberInfo displayInfo) throws InternalException, UserException
    {
        super(recordSet, name);
        cache = new NumericColumnStorage(displayInfo, this::fillCacheWithProgress);
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
