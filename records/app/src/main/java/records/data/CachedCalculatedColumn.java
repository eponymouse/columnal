package records.data;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExBiConsumer;
import utility.ExConsumer;
import utility.ExFunction;

/**
 * Created by neil on 14/01/2017.
 */
public class CachedCalculatedColumn<S extends ColumnStorage> extends CalculatedColumn
{
    private final S cache;
    private final ExConsumer<S> addToCache;

    public CachedCalculatedColumn(RecordSet recordSet, ColumnId name, ExFunction<ExBiConsumer<Integer, @Nullable ProgressListener>, S> cache, ExConsumer<S> addToCache) throws InternalException, UserException
    {
        super(recordSet, name);
        this.cache = cache.apply(this::fillCacheWithProgress);
        this.addToCache = addToCache;
    }

    @Override
    @OnThread(Tag.Any)
    public DataTypeValue getType() throws InternalException, UserException
    {
        return cache.getType();
    }

    @Override
    protected void fillNextCacheChunk() throws UserException, InternalException
    {
        addToCache.accept(cache);
    }

    @Override
    protected int getCacheFilled()
    {
        return cache.filled();
    }
}
