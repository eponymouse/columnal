package records.data;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.TagType;
import records.error.InternalException;
import records.error.UserException;
import utility.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 05/11/2016.
 */
public abstract class CalculatedTaggedColumn extends CalculatedColumn
{
    // This stores tags.  It may also be re-used to store the
    // first numeric value, if applicable:
    @NonNull
    protected final NumericColumnStorage tagCache;
    // This may include tagCache for the first numeric store:
    protected final List<ColumnStorage<?>> valueStores = new ArrayList<>();
    private final DataType dataType;

    @SuppressWarnings("initialization")
    public CalculatedTaggedColumn(RecordSet recordSet, String name, List<TagType> copyTagTypes, Column... dependencies) throws InternalException, UserException
    {
        super(recordSet, name, dependencies);

        tagCache = new NumericColumnStorage(copyTagTypes.size(), -1);
        List<TagType> tagTypes = new ArrayList<>();
        for (int i = 0; i < copyTagTypes.size(); i++)
        {
            TagType tagType = copyTagTypes.get(i);
            if (tagType.getInner() != null)
            {
                int iFinal = i;
                Pair<DataType, List<ColumnStorage<?>>> result = tagType.getInner().apply(new DataTypeVisitor<Pair<DataType, List<ColumnStorage<?>>>>()
                {
                    boolean nested = false;

                    @Override
                    public Pair<DataType, List<ColumnStorage<?>>> number() throws InternalException, UserException
                    {
                        if (tagCache.getNumericTag() != -1 || nested)
                        {
                            // Already re-used tag cache; need another column
                            NumericColumnStorage storage = new NumericColumnStorage();
                            return new Pair<>(storage.getType(), Collections.singletonList(storage));
                        } else
                        {
                            // Will re-use tag cache
                            tagCache.setNumericTag(iFinal);
                            return new Pair<>(tagCache.getType(), Collections.emptyList());
                        }
                    }

                    @Override
                    public Pair<DataType, List<ColumnStorage<?>>> text() throws InternalException, UserException
                    {
                        StringColumnStorage storage = new StringColumnStorage();
                        return new Pair<>(storage.getType(), Collections.singletonList(storage));
                    }

                    @Override
                    public Pair<DataType, List<ColumnStorage<?>>> tagged(List<TagType> tags) throws InternalException, UserException
                    {
                        // Flatten, no re-use for nested columns at the moment:
                        ArrayList<ColumnStorage<?>> stores = new ArrayList<>();
                        ArrayList<TagType> nestedTagTypes = new ArrayList<>();
                        NumericColumnStorage nestedTagStorage = new NumericColumnStorage(tags.size(), -1);
                        stores.add(nestedTagStorage);
                        boolean oldNested = nested;
                        nested = true;
                        for (TagType tt : tags)
                        {
                            if (tt.getInner() != null)
                            {
                                Pair<DataType, List<ColumnStorage<?>>> p = tt.getInner().apply(this);
                                stores.addAll(p.getSecond());
                                nestedTagTypes.add(new TagType(tt.getName(), p.getFirst()));
                            } else
                                nestedTagTypes.add(new TagType(tt.getName(), null));
                        }
                        nested = oldNested;
                        return new Pair<>(new DataType()
                        {
                            @Override
                            public <R> R apply(DataTypeVisitorGet<R> visitor) throws InternalException, UserException
                            {
                                return visitor.tagged(nestedTagTypes, (i, prog) -> nestedTagStorage.getTag(i));
                            }
                        }, stores);
                    }
                });
                valueStores.addAll(result.getSecond());
                tagTypes.add(new TagType(tagType.getName(), result.getFirst()));
            }
            else
                tagTypes.add(new TagType(tagType.getName(), null));
        }
        dataType = new DataType()
        {
            @Override
            public <R> R apply(DataTypeVisitorGet<R> visitor) throws InternalException, UserException
            {
                return visitor.tagged(tagTypes, (i, prog) -> {
                    // This should also fill all other relevant storages with values or null:
                    fillCacheWithProgress(i, prog);
                    return tagCache.getTag(i);
                });
            }
        };
    }

    @Override
    public DataType getType() throws InternalException, UserException
    {
        return dataType;
    }

    @Override
    protected void clearCache() throws InternalException
    {
        tagCache.clear();
        for (ColumnStorage s : valueStores)
            s.clear();
    }

    @Override
    protected int getCacheFilled()
    {
        return tagCache.filled();
    }

    protected void addUnpacked(List<Object> values)
    {
        //TODO walk the tag structure, adding either next value or null to each cache depending on tag
    }
}
