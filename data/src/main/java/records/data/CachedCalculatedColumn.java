package records.data;

import log.Log;
import org.checkerframework.checker.nullness.qual.NonNull;
import records.data.ColumnStorage.BeforeGet;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.ExFunction;
import utility.FunctionInt;
import utility.Utility;

import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Created by neil on 14/01/2017.
 */
public class CachedCalculatedColumn<T, S extends ColumnStorage<T>> extends CalculatedColumn<S>
{
    private final S cache;
    private final ExFunction<Integer, @NonNull T> calculateItem;
    @OnThread(Tag.Any)
    private final DataTypeValue cacheType;

    public CachedCalculatedColumn(RecordSet recordSet, ColumnId name, FunctionInt<BeforeGet<S>, S> cache, ExFunction<Integer, @NonNull T> calculateItem, FunctionInt<DataTypeValue, DataTypeValue> addManualEdit) throws InternalException
    {
        super(recordSet, name);
        this.calculateItem = calculateItem;
        this.cache = cache.apply(Utility.later(this));
        this.cacheType = addManualEdit.apply(this.cache.getType());
    }

    @Override
    @OnThread(Tag.Any)
    public DataTypeValue getType() throws InternalException, UserException
    {
        return cacheType;
    }

    @Override
    protected void fillNextCacheChunk() throws InternalException
    {
        Either<String, @NonNull T> value;
        try
        {
            value = Either.right(calculateItem.apply(cache.filled()));
        }
        catch (InternalException | UserException e)
        {
            if (e instanceof InternalException)
                Log.log(e);
            value = Either.left(e.getLocalizedMessage());
        }
        
        cache.addAll(Stream.<Either<String, @NonNull T>>of(value));
    }

    @Override
    protected int getCacheFilled()
    {
        return cache.filled();
    }

    @Override
    public @OnThread(Tag.Any) AlteredState getAlteredState()
    {
        return AlteredState.OVERWRITTEN;
    }
}
