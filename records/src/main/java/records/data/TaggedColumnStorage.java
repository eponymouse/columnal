package records.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.NumberDisplayInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.UnitType;
import utility.Utility;

/**
 * Created by neil on 05/11/2016.
 */
public class TaggedColumnStorage implements ColumnStorage<List<Object>>
{
    // This stores the tag index as a tag, not a number.
    private final NumericColumnStorage tagStore;
    // This stores the index at which each inner value resides.
    // For example, let's stay you have the type:
    // \A:Number \B \C:Date \D:Number
    // And you add the following data:
    // \A:0
    // \B
    // \A:4
    // \C:...
    // \D:6
    // \A:5
    // tagIndexStore will have 0, 1, 0, 2, 3, 0 (the tag indexes)
    // innerValueIndex will have 0, -1, 1, 0, 0, 2 (effectively, the per-tag index
    // within the corresponding value store)
    private final NumericColumnStorage innerValueIndex;
    // This is the list of storage columns for inner types.
    // We could share them among same-types, but it doesn't save memory so
    // we keep it simple.  We put a null entry where there is no inner type.
    // We could also collapse nested tagged columns, but we choose not to,
    // again to keep it simple:
    protected final List<@Nullable ColumnStorage<?>> valueStores;
    private final List<TagType<DataTypeValue>> tagTypes;
    // Effectively a cached version of tagTypes:
    @OnThread(Tag.Any)
    private final DataTypeValue dataType;

    @SuppressWarnings("initialization")
    public <DT extends DataType> TaggedColumnStorage(List<TagType<DT>> copyTagTypes) throws InternalException, UserException
    {
        tagStore = new NumericColumnStorage(copyTagTypes.size());
        innerValueIndex = new NumericColumnStorage();
        valueStores = new ArrayList<>();
        tagTypes = new ArrayList<>();
        for (int i = 0; i < copyTagTypes.size(); i++)
        {
            TagType<? extends DataType> tagType = copyTagTypes.get(i);
            DataType inner = tagType.getInner();
            if (inner != null)
            {
                Pair<ColumnStorage<?>, DataTypeValue> result = inner.apply(new DataTypeVisitor<Pair<ColumnStorage<?>, DataTypeValue>>()
                {
                    private Pair<ColumnStorage<?>, DataTypeValue> simple(ColumnStorage<?> storage) throws InternalException, UserException
                    {
                        return new Pair<>(storage, inner.copy((rowIndex, prog) -> Collections.singletonList(storage.get(innerValueIndex.get(rowIndex).intValue()))));
                    }

                    @Override
                    public Pair<ColumnStorage<?>, DataTypeValue> number(NumberDisplayInfo displayInfo) throws InternalException, UserException
                    {
                        return simple(new NumericColumnStorage(displayInfo));
                    }

                    @Override
                    public Pair<ColumnStorage<?>, DataTypeValue> bool() throws InternalException, UserException
                    {
                        return simple(new BooleanColumnStorage());
                    }

                    @Override
                    public Pair<ColumnStorage<?>, DataTypeValue> text() throws InternalException, UserException
                    {
                        return simple(new StringColumnStorage());
                    }

                    @Override
                    public Pair<ColumnStorage<?>, DataTypeValue> date() throws InternalException, UserException
                    {
                        return simple(new DateColumnStorage());
                    }

                    @Override
                    public Pair<ColumnStorage<?>, DataTypeValue> tagged(List<TagType<DataType>> tags) throws InternalException, UserException
                    {
                        TaggedColumnStorage storage = new TaggedColumnStorage(tags);
                        // In contrast to simple, we flatten things by returning the inner list
                        // instead of nesting it in another list:
                        return new Pair<>(storage, inner.copy((rowIndex, prog) -> storage.get(innerValueIndex.get(rowIndex).intValue())));
                    }
                });
                tagTypes.add(new TagType<DataTypeValue>(tagType.getName(), result.getSecond()));
                valueStores.add(result.getFirst());
            }
            else
            {
                tagTypes.add(new TagType<DataTypeValue>(tagType.getName(), null));
                valueStores.add(null);
            }
        }
        dataType = DataTypeValue.tagged(tagTypes, (i, prog) -> {
                return tagStore.getTag(i);
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
        return tagStore.filled();
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
        //Walk the tag structure, adding to store depending on tag:
        Object tag = values.get(0);
        if (!(tag instanceof Integer))
            throw new InternalException("Tag not integer: " + tag.getClass());
        int tagIndex = (Integer) tag;
        tagStore.addTag(tagIndex);

        @Nullable DataTypeValue inner = tagTypes.get(tagIndex).getInner();
        if (inner == null)
        {
            innerValueIndex.add(-1);
            return;
        }
        inner.apply(new DataType.DataTypeVisitorEx<UnitType, InternalException>()
        {
            @Override
            public UnitType number(NumberDisplayInfo displayInfo) throws InternalException
            {
                return storeSimple();
            }

            @SuppressWarnings("unchecked")
            private UnitType storeSimple() throws InternalException
            {
                ColumnStorage storage = valueStores.get(tagIndex);
                if (storage == null)
                    throw new InternalException("Value but no store for tag " + tagIndex);
                innerValueIndex.add(storage.filled());
                storage.add(values.get(1));
                return UnitType.UNIT;
            }

            @Override
            public UnitType text() throws InternalException
            {
                return storeSimple();
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
                ColumnStorage storage = valueStores.get(tagIndex);
                if (storage == null)
                    throw new InternalException("Value but no store for tag " + tagIndex);
                innerValueIndex.add(storage.filled());
                storage.add(values.subList(1, values.size()));
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
