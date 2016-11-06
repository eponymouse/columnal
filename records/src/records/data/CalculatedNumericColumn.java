package records.data;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.NumberDisplayInfo;
import records.data.datatype.DataType.SpecificDataTypeVisitor;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Collections;

/**
 * Created by neil on 05/11/2016.
 */
public abstract class CalculatedNumericColumn extends CalculatedColumn
{
    @OnThread(Tag.Any)
    private final DataType copyType;
    @MonotonicNonNull
    @OnThread(value = Tag.Any, requireSynchronized = true)
    private DataType type;
    protected final NumericColumnStorage cache;

    public CalculatedNumericColumn(RecordSet recordSet, String name, DataType copyType, Column... dependencies) throws InternalException, UserException
    {
        super(recordSet, name, dependencies);
        this.copyType = copyType;
        cache = this.copyType.apply(new SpecificDataTypeVisitor<NumericColumnStorage>()
        {
            @Override
            public NumericColumnStorage number(NumberDisplayInfo displayInfo) throws InternalException, UserException
            {
                return new NumericColumnStorage(displayInfo);
            }
        });
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
    @OnThread(Tag.Any)
    public synchronized DataType getType() throws UserException, InternalException
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
