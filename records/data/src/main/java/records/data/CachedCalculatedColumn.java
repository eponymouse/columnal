package records.data;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnStorage.BeforeGet;
import records.data.Table.Display;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExBiConsumer;
import utility.ExConsumer;
import utility.ExFunction;
import utility.FunctionInt;
import utility.Pair;
import utility.Utility;

import java.util.function.Predicate;

/**
 * Created by neil on 14/01/2017.
 */
public class CachedCalculatedColumn<S extends ColumnStorage<?>> extends CalculatedColumn<S>
{
    private final S cache;
    private final ExConsumer<S> addToCache;

    public CachedCalculatedColumn(RecordSet recordSet, ColumnId name, FunctionInt<BeforeGet<S>, S> cache, ExConsumer<S> addToCache) throws InternalException
    {
        super(recordSet, name);
        this.addToCache = addToCache;
        this.cache = cache.apply(Utility.later(this));
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

    @Override
    @OnThread(Tag.Any)
    public boolean isAltered()
    {
        return true;
    }
}
