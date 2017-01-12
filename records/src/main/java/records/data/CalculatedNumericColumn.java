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
    @OnThread(Tag.Any)
    private final DataType copyType;
    @MonotonicNonNull
    @OnThread(value = Tag.Any, requireSynchronized = true)
    private DataTypeValue type;
    protected final NumericColumnStorage cache;

    @SuppressWarnings("initialization")
    public CalculatedNumericColumn(RecordSet recordSet, ColumnId name, DataType copyType) throws InternalException, UserException
    {
        super(recordSet, name);
        this.copyType = copyType;
        cache = this.copyType.apply(new SpecificDataTypeVisitor<NumericColumnStorage>()
        {
            @Override
            public NumericColumnStorage number(NumberInfo displayInfo) throws InternalException, UserException
            {
                return new NumericColumnStorage(displayInfo) {
                    @Override
                    public void beforeGet(int index, ProgressListener progressListener) throws InternalException, UserException
                    {
                        fillCacheWithProgress(index, progressListener);
                    }
                };
            }
        });
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
