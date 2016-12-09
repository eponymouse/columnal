package records.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.NumberDisplayInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UnimplementedException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.UnitType;

/**
 * Created by neil on 05/11/2016.
 */
public class TaggedColumnStorage implements ColumnStorage<List<Object>>
{
    // This stores tags.  It may also be re-used to store the
    // first numeric value, if applicable.  It is the first item
    // in valueStores but kept out here too for convenience:
    @NonNull
    protected final NumericColumnStorage tagCache;
    // This will include tagCache once at the beginning, and maybe again for the first numeric store:
    protected final List<ColumnStorage<?>> valueStores = new ArrayList<>();
    @OnThread(Tag.Any)
    private final DataTypeValue dataType;

    @SuppressWarnings("initialization")
    public <DT extends DataType> TaggedColumnStorage(List<TagType<DT>> copyTagTypes) throws InternalException, UserException
    {
        tagCache = new NumericColumnStorage(copyTagTypes.size());
        valueStores.add(tagCache);
        List<TagType<DataTypeValue>> tagTypes = new ArrayList<>();
        for (int i = 0; i < copyTagTypes.size(); i++)
        {
            TagType<? extends DataType> tagType = copyTagTypes.get(i);
            if (tagType.getInner() != null)
            {
                int iFinal = i;
                Pair<DataTypeValue, List<ColumnStorage<?>>> result = tagType.getInner().apply(new DataTypeVisitor<Pair<DataTypeValue, List<ColumnStorage<?>>>>()
                {
                    boolean nested = false;

                    @Override
                    public Pair<DataTypeValue, List<ColumnStorage<?>>> number(NumberDisplayInfo displayInfo) throws InternalException, UserException
                    {
                        if (tagCache.getNumericTag() != -1 || nested)
                        {
                            // Already re-used tag cache; need another column
                            NumericColumnStorage storage = new NumericColumnStorage(displayInfo);
                            return new Pair<>(storage.getType(), Collections.singletonList(storage));
                        } else
                        {
                            // Will re-use tag cache
                            tagCache.setNumericTag(iFinal);
                            tagCache.setDisplayInfo(displayInfo);
                            return new Pair<>(tagCache.getType(), Collections.singletonList(tagCache));
                        }
                    }

                    @Override
                    public Pair<DataTypeValue, List<ColumnStorage<?>>> bool() throws InternalException, UserException
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
                            return new Pair<>(tagCache.getType(), Collections.singletonList(tagCache));
                        }
                    }

                    @Override
                    public Pair<DataTypeValue, List<ColumnStorage<?>>> text() throws InternalException, UserException
                    {
                        StringColumnStorage storage = new StringColumnStorage();
                        return new Pair<>(storage.getType(), Collections.singletonList(storage));
                    }

                    @Override
                    public Pair<DataTypeValue, List<ColumnStorage<?>>> date() throws InternalException, UserException
                    {
                        throw new UnimplementedException();
                    }

                    @Override
                    public Pair<DataTypeValue, List<ColumnStorage<?>>> tagged(List<TagType<DataType>> tags) throws InternalException, UserException
                    {
                        // Flatten, no re-use for nested columns at the moment:
                        ArrayList<ColumnStorage<?>> stores = new ArrayList<>();
                        ArrayList<TagType<DataTypeValue>> nestedTagTypes = new ArrayList<>();
                        NumericColumnStorage nestedTagStorage = new NumericColumnStorage(tags.size());
                        stores.add(nestedTagStorage);
                        boolean oldNested = nested;
                        nested = true;
                        for (TagType tt : tags)
                        {
                            if (tt.getInner() != null)
                            {
                                Pair<DataTypeValue, List<ColumnStorage<?>>> p = tt.getInner().apply(this);
                                nestedTagTypes.add(new TagType<DataTypeValue>(tt.getName(), p.getFirst(), stores.size()));
                                stores.addAll(p.getSecond());
                            } else
                                nestedTagTypes.add(new TagType<DataTypeValue>(tt.getName(), null));
                        }
                        nested = oldNested;
                        return new Pair<>(DataTypeValue.tagged(nestedTagTypes, (i, prog) -> nestedTagStorage.getTag(i)), stores);
                    }
                });
                tagTypes.add(new TagType<DataTypeValue>(tagType.getName(), result.getFirst(), valueStores.size()));
                valueStores.addAll(result.getSecond());

            }
            else
                tagTypes.add(new TagType<DataTypeValue>(tagType.getName(), null));
        }
        dataType = DataTypeValue.tagged(tagTypes, (i, prog) -> {
                return tagCache.getTag(i);
                });
    }

    @OnThread(Tag.Any)
    public DataTypeValue getType() throws InternalException
    {
        return dataType;
    }

    @Override
    public int filled()
    {
        return tagCache.filled();
    }

    @Override
    public List<Object> get(int index) throws InternalException, UserException
    {
        return dataType.getCollapsed(index);
    }

    public void addAll(List<List<Object>> values) throws InternalException
    {
        for (List<Object> v : values)
            addUnpacked(v);
    }

    protected void addUnpacked(List<Object> values) throws InternalException
    {
        //Walk the tag structure, adding either next value or null to each cache depending on tag:

        getType().apply(new DataType.DataTypeVisitorEx<UnitType, InternalException>()
        {
            Iterator<Object> it = values.iterator();
            int storeOffset = 0;

            @Override
            public UnitType number(NumberDisplayInfo displayInfo) throws InternalException
            {
                return storeSimple();
            }

            @SuppressWarnings("unchecked")
            private UnitType storeSimple() throws InternalException
            {
                ((ColumnStorage<Object>)valueStores.get(storeOffset)).add(it.next());
                for (int i = storeOffset + 1; i < valueStores.size(); i++)
                    valueStores.get(i).add(null);
                return UnitType.UNIT;
            }

            @Override
            public UnitType text() throws InternalException
            {
                return storeSimple(); // Same process
            }

            @Override
            public UnitType date() throws InternalException
            {
                return storeSimple();
            }

            @Override
            public UnitType bool() throws InternalException
            {
                return storeSimple();
            }

            @Override
            public UnitType tagged(List<TagType<DataType>> tags) throws InternalException
            {
                int tagIndex = (Integer)it.next();
                TagType t = tags.get(tagIndex);
                int tagStoreOffset = t.getExtra();
                ((NumericColumnStorage)valueStores.get(storeOffset)).addTag(tagIndex);
                // We must add everything up to that item:
                for (int i = storeOffset + 1; i < ((tagStoreOffset == -1) ? valueStores.size() : (storeOffset + tagStoreOffset)); i++)
                    valueStores.get(i).add(null);
                @Nullable DataType inner = t.getInner();
                if (inner != null)
                {
                    storeOffset += tagStoreOffset;
                    inner.apply(this);
                }
                return UnitType.UNIT;
            }
        });
    }

    public List<List<Object>> getShrunk(int shrunkLength) throws UserException, InternalException
    {
        List<List<Object>> s = new ArrayList<>();
        for (int i = 0; i < shrunkLength; i++)
        {
            s.add(get(i));
        }
        return s;
    }
}
