package records.data;

import java.util.ArrayList;
import java.util.List;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.NumberInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.data.datatype.TypeId;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.UnitType;

/**
 * Created by neil on 05/11/2016.
 */
public class TaggedColumnStorage implements ColumnStorage<Pair<Integer, @Nullable Object>>
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
    public <DT extends DataType> TaggedColumnStorage(TypeId typeName, List<TagType<DT>> copyTagTypes) throws InternalException
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
                ColumnStorage<?> result = DataTypeUtility.makeColumnStorage(inner);
                tagTypes.add(new TagType<DataTypeValue>(tagType.getName(), result.getType().copy((rowIndex, prog) -> {
                    return result.get(innerValueIndex.get(rowIndex).intValue());
                })));
                valueStores.add(result);
            }
            else
            {
                tagTypes.add(new TagType<DataTypeValue>(tagType.getName(), null));
                valueStores.add(null);
            }
        }
        dataType = DataTypeValue.tagged(typeName, tagTypes, (i, prog) -> {
                return tagStore.getTag(i);
                });
    }

    @OnThread(Tag.Any)
    public DataTypeValue getType()
    {
        return dataType;
    }

    @Override
    public int filled()
    {
        return tagStore.filled();
    }

    @Override
    public Pair<Integer, @Nullable Object> get(int index) throws InternalException, UserException
    {
        if (index < 0 || index >= filled())
            throw new InternalException("Attempting to access invalid element: " + index + " of " + filled());
        return (Pair<Integer, @Nullable Object>) dataType.getCollapsed(index);
    }

    public void addAll(List<Pair<Integer, @Nullable Object>> values) throws InternalException
    {
        for (Pair<Integer, @Nullable Object> v : values)
            addUnpacked(v);
    }

    protected void addUnpacked(Pair<Integer, @Nullable Object> value) throws InternalException
    {
        //Walk the tag structure, adding to store depending on tag:
        int tagIndex = value.getFirst();
        tagStore.addTag(tagIndex);

        @Nullable DataTypeValue inner = tagTypes.get(tagIndex).getInner();
        if (inner == null)
        {
            innerValueIndex.add(-1);
            return;
        }
        ColumnStorage storage = valueStores.get(tagIndex);
        if (storage == null)
            throw new InternalException("Value but no store for tag " + tagIndex);
        innerValueIndex.add(storage.filled());
        @Nullable Object innerValue = value.getSecond();
        if (innerValue == null)
            throw new InternalException("Inner value despite no inner type");
        storage.add(innerValue);
    }

    public List<Pair<Integer, @Nullable Object>> getShrunk(int shrunkLength) throws UserException, InternalException
    {
        List<Pair<Integer, @Nullable Object>> s = new ArrayList<>();
        for (int i = 0; i < shrunkLength; i++)
        {
            s.add(get(i));
        }
        return s;
    }
}
