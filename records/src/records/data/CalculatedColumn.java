package records.data;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DataTypeVisitorGet;
import records.data.datatype.DataType.GetValue;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by neil on 21/10/2016.
 */
public abstract class CalculatedColumn extends Column
{
    private final String name;
    private final ArrayList<Column> dependencies;
    // Version of each of the dependencies at last calculation:
    private final Map<Column, Long> calcVersions = new IdentityHashMap<>();
    private long version = 1;

    public CalculatedColumn(RecordSet recordSet, String name, Column... dependencies)
    {
        super(recordSet);
        this.name = name;
        this.dependencies = new ArrayList<>(Arrays.asList(dependencies));
    }

    public static interface FoldOperation<T, R>
    {
        List<R> start();
        List<R> process(T n);
        List<R> end() throws UserException;
    }

    // Preserves type
    /*
    public static CalculatedColumn fold(RecordSet rs, String name, Column src, DataTypeVisitor<FoldOperation> op) throws UserException, InternalException
    {
        return src.getType().apply(new DataTypeVisitorGet<CalculatedColumn>()
        {
            @Override
            public CalculatedColumn number(GetValue<Number> g) throws InternalException, UserException
            {
                return new PrimitiveCalculatedColumn<Number>(rs, name, src, new NumericColumnStorage(0), op.number(), g)
                {
                    private DataType dataType = new DataTypeOf(src.getType())
                    {
                        @Override
                        public <R> R apply(DataTypeVisitorGet<R> visitor) throws InternalException, UserException
                        {
                            return visitor.number((index, prog) -> {
                                fillCacheWithProgress(index, prog);
                                return cache.get(index);
                            });
                        }
                    };

                    @Override
                    protected void clearCache() throws InternalException
                    {
                        cache = new NumericColumnStorage(0);
                    }

                    @Override
                    public DataType getType()
                    {
                        return dataType;
                    }
                };
            }

            @Override
            public CalculatedColumn text(GetValue<String> g) throws InternalException, UserException
            {
                return new PrimitiveCalculatedColumn<String>(rs, name, src, new StringColumnStorage(), op.text(), g)
                {
                    private DataType dataType = new DataTypeOf(src.getType())
                    {
                        @Override
                        public <R> R apply(DataTypeVisitorGet<R> visitor) throws InternalException, UserException
                        {
                            return visitor.text((index, prog) -> {
                                fillCacheWithProgress(index, prog);
                                return cache.get(index);
                            });
                        }
                    };

                    @Override
                    protected void clearCache() throws InternalException
                    {
                        cache = new StringColumnStorage();
                    }

                    @Override
                    public DataType getType()
                    {
                        return dataType;
                    }
                };
            }

            @Override
            public CalculatedColumn tagged(int tagIndex, String tag, DataType inner) throws InternalException, UserException
            {
                throw new UnimplementedException("");
            }
        });
    }
*/
    protected final void fillCacheWithProgress(int index, @Nullable ProgressListener progressListener) throws UserException, InternalException
    {
        if (!checkCacheValid())
        {
            clearCache();
            version += 1;
        }
        // Fetch values:
        while (index >= getCacheFilled())
        {
            fillNextCacheChunk();
        }
    }

    protected abstract void fillNextCacheChunk() throws UserException, InternalException;

    protected abstract void clearCache() throws InternalException;

    protected abstract int getCacheFilled();

    //protected final void updateProgress(double d) { }

    private boolean checkCacheValid()
    {
        boolean allValid = true;
        for (Column c : dependencies)
        {
            Long lastVer = calcVersions.get(c);
            if (lastVer == null || lastVer.longValue() != c.getVersion())
            {
                calcVersions.put(c, c.getVersion());
                allValid = false;
            }
        }
        return allValid;
    }

    @Override
    @OnThread(Tag.Any)
    public final String getName()
    {
        return name;
    }

    @Override
    public final long getVersion()
    {
        return version;
    }
/*
    private static abstract class PrimitiveCalculatedColumn<T> extends CalculatedColumn
    {
        protected ColumnStorage<T> cache;
        protected final FoldOperation<T, T> op;
        private final Column src;
        private GetValue<T> get;
        private int chunkSize = 100;

        public PrimitiveCalculatedColumn(RecordSet rs, String name, Column src, ColumnStorage<T> storage, FoldOperation<T, T> op, GetValue<T> get)
        {
            super(rs, name, src);
            this.src = src;
            this.cache = storage;
            this.op = op;
            this.get = get;
        }


        @Override
        protected int getCacheFilled()
        {
            return cache.filled();
        }

        int nextSource = -1;

        @Override
        protected void fillNextCacheChunk() throws UserException, InternalException
        {
            if (nextSource == -1)
            {
                cache.addAll(op.start());
                nextSource = 0;
            }

            int limit = nextSource + chunkSize;
            int i = nextSource;
            for (; i < limit && src.indexValid(i); i++)
            {
                cache.addAll(op.process(get.getWithProgress(i, null)));
            }
            if (i != nextSource && !src.indexValid(i))
            {
                cache.addAll(op.end());
            }
            nextSource = i;
        }
    }
    */
}
