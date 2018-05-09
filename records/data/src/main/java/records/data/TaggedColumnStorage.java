package records.data;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column.ProgressListener;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.data.datatype.DataTypeValue.DataTypeVisitorGetEx;
import records.data.datatype.DataTypeValue.GetValue;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeId;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.SimulationRunnable;
import utility.TaggedValue;
import utility.Utility;

import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;

/**
 * Created by neil on 05/11/2016.
 */
public class TaggedColumnStorage implements ColumnStorage<TaggedValue>
{
    // This stores the tag index of each item.
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
    // tagStore will have 0, 1, 0, 2, 3, 0 (the tag indexes)
    // innerValueIndex will have 0, -1 [none], 1, 0, 0, 2 (effectively, the per-tag index
    // within the corresponding value store).  i.e. we compress each
    // storage so that we don't leave gaps.  The indexes must be
    // in ascending order
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

    public <DT extends DataType> TaggedColumnStorage(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, List<TagType<DT>> copyTagTypes) throws InternalException
    {
        this(typeName, typeVars, copyTagTypes, null);
    }

    @SuppressWarnings("initialization")
    public <DT extends DataType> TaggedColumnStorage(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, List<TagType<DT>> copyTagTypes, @Nullable BeforeGet<TaggedColumnStorage> beforeGet) throws InternalException
    {
        tagStore = new NumericColumnStorage();
        innerValueIndex = new NumericColumnStorage();
        valueStores = new ArrayList<>();
        tagTypes = new ArrayList<>();
        for (int i = 0; i < copyTagTypes.size(); i++)
        {
            TagType<? extends DataType> tagType = copyTagTypes.get(i);
            DataType inner = tagType.getInner();
            if (inner != null)
            {
                ColumnStorage<?> result = DataTypeUtility.makeColumnStorage(inner, null);
                tagTypes.add(new TagType<DataTypeValue>(tagType.getName(), reMap(result.getType())));
                valueStores.add(result);
            }
            else
            {
                tagTypes.add(new TagType<DataTypeValue>(tagType.getName(), null));
                valueStores.add(null);
            }
        }
        dataType = DataTypeValue.tagged(typeName, typeVars, ImmutableList.copyOf(tagTypes), new GetValue<Integer>()
        {
            @Override
            public Integer getWithProgress(int i, ProgressListener prog) throws UserException, InternalException
            {
                if (beforeGet != null)
                    beforeGet.beforeGet(TaggedColumnStorage.this, i, prog);
                return tagStore.getInt(i);
            }

            @Override
            public void set(int index, Integer newTag) throws InternalException, UserException
            {
                int oldTag = tagStore.getInt(index);
                if (newTag.intValue() == oldTag)
                    return; // No need to change anything here.

                tagStore.set(OptionalInt.of(index), newTag);

                // Must remove old value:
                @Nullable ColumnStorage<?> oldInner = valueStores.get(oldTag);
                if (oldInner != null)
                {
                    oldInner.removeRows(innerValueIndex.getInt(index), 1);
                    // No need to change index itself, as it's about to be replaced
                    for (int i = index + 1; i < tagStore.filled(); i++)
                    {
                        if (tagStore.getInt(i) == oldTag)
                        {
                            innerValueIndex.set(OptionalInt.of(i), innerValueIndex.getInt(i) - 1);
                        }
                    }
                }
                @Nullable ColumnStorage<?> newInner = valueStores.get(newTag);
                if (newInner != null)
                {
                    // Work out destination position by seeing how many are before us:
                    int newValueIndex = 0;
                    for (int i = 0; i < index; i++)
                    {
                        if (tagStore.getInt(i) == newTag)
                        {
                            newValueIndex++;
                        }
                    }
                    innerValueIndex.set(OptionalInt.of(index), newValueIndex);
                    @SuppressWarnings("unchecked")
                    List single = Collections.singletonList(DataTypeUtility.makeDefaultValue(newInner.getType()));
                    @SuppressWarnings("unchecked")
                    SimulationRunnable _r = newInner.insertRows(newValueIndex, single);
                    for (int i = index + 1; i < tagStore.filled(); i++)
                    {
                        if (tagStore.getInt(i) == newTag)
                        {
                            innerValueIndex.set(OptionalInt.of(i), innerValueIndex.getInt(i) + 1);
                        }
                    }
                }
                else
                {
                    // If no new inner store, blank the inner value index:
                    innerValueIndex.set(OptionalInt.of(index), -1);
                }
            }
        });
    }

    /**
     * Given the inner storage used to store those items, returns a DataTypeValue
     * for the wrapped type, which has to map the indexes.  Recall that if you have
     * tags A:Bool, B, C:Integer, the index of the value for C in the store for C
     * is totally independent of the index in the store of A for values of A.  We must
     * re map.  Remapping while getting is quite straightforward: just use the innerValueIndex
     * mapping.  The tricky part is updating the mapping when storing.
     *
     * @param innerStore The storage to store in
     * @param innerStoreTagIndex If present, it means we are responsible for updating the indexes,
     *                           and should update them for the given inner store.  If missing,
     *                           it's not up to us (e.g. we're second in a tuple, and first is responsible)
     *                           so leave them alone, and assume they've already been done.
     */
    private DataTypeValue reMap(DataTypeValue dataTypeValue) throws InternalException
    {
        return dataTypeValue.applyGet(new DataTypeVisitorGetEx<DataTypeValue, InternalException>()
        {
            // We are given the GetValue for the inner storage
            // We must map through innerValueIndex
            private <T> GetValue<T> reMapGV(GetValue<T> g)
            {
                return new GetValue<T>()
                {
                    @Override
                    public @NonNull T getWithProgress(int index, @Nullable ProgressListener progressListener) throws UserException, InternalException
                    {
                        return g.getWithProgress(innerValueIndex.getInt(index), progressListener);
                    }

                    @Override
                    public void set(int index, T value) throws InternalException, UserException
                    {
                        g.set(innerValueIndex.getInt(index), value);
                    }
                };
            }

            @Override
            public DataTypeValue number(GetValue<@Value Number> g, NumberInfo displayInfo) throws InternalException
            {
                return DataTypeValue.number(displayInfo, reMapGV(g));
            }

            @Override
            public DataTypeValue text(GetValue<@Value String> g) throws InternalException
            {
                return DataTypeValue.text(reMapGV(g));
            }

            @Override
            public DataTypeValue bool(GetValue<@Value Boolean> g) throws InternalException
            {
                return DataTypeValue.bool(reMapGV(g));
            }

            @Override
            public DataTypeValue date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> g) throws InternalException
            {
                return DataTypeValue.date(dateTimeInfo, reMapGV(g));
            }

            @Override
            public DataTypeValue tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataTypeValue>> tagTypes, GetValue<Integer> g) throws InternalException
            {
                return DataTypeValue.tagged(typeName, typeVars, Utility.mapListInt(tagTypes, (TagType<DataTypeValue> tt) -> new TagType<DataTypeValue>(tt.getName(), tt.getInner() == null ? null : reMap(tt.getInner()))), reMapGV(g));
            }

            @Override
            public DataTypeValue tuple(ImmutableList<DataTypeValue> types) throws InternalException
            {
                List<DataTypeValue> reMapped = new ArrayList<>();
                for (int i = 0; i < types.size(); i++)
                {
                    reMapped.add(reMap(types.get(i)));
                }
                return DataTypeValue.tupleV(reMapped);
            }

            @Override
            public DataTypeValue array(@Nullable DataType inner, GetValue<Pair<Integer, DataTypeValue>> g) throws InternalException
            {
                if (inner == null)
                    throw new InternalException("Tagged type cannot contain empty array");
                return DataTypeValue.arrayV(inner, reMapGV(g));
            }
        });
    }

    @OnThread(Tag.Any)
    public DataTypeValue getType()
    {
        return dataType;
    }

    @Override
    public SimulationRunnable insertRows(int insertAtOriginalIndex, List<TaggedValue> insertItems) throws InternalException, UserException
    {
        int insertAtIndex = insertAtOriginalIndex;
        for (TaggedValue insertItem : insertItems)
        {
            tagStore.addAll(insertAtIndex, Collections.singletonList(insertItem.getTagIndex()));
            if (insertItem.getInner() == null)
            {
                // Empty tags are easy:

                innerValueIndex.addAll(insertAtIndex, Utility.<Number>replicate(1, -1));
            }
            else
            {
                // TODO could do this more efficiently by incrementing all following items once for each tag index
                int lastInnerValueIndex = -1;
                // Find last used index before us:
                for (int i = 0; i < insertAtIndex; i++)
                {
                    if (tagStore.getInt(i) == insertItem.getTagIndex())
                        lastInnerValueIndex = innerValueIndex.getInt(i);
                }
                // Add a new one:
                innerValueIndex.addAll(insertAtIndex, Collections.singletonList(lastInnerValueIndex + 1));
                @Nullable ColumnStorage<?> valueStore = valueStores.get(insertItem.getTagIndex());
                if (valueStore == null)
                    throw new InternalException("No value store for inner type tag: " + insertItem.getTagIndex());
                @SuppressWarnings("unchecked")
                List single = Collections.singletonList(insertItem.getInner());
                @SuppressWarnings("unchecked")
                SimulationRunnable _r = valueStore.insertRows(lastInnerValueIndex + 1, single);
                // Increment all after us accordingly:
                for (int i = insertAtIndex + 1; i < innerValueIndex.filled(); i++)
                {
                    if (tagStore.getInt(i) == insertItem.getTagIndex())
                        innerValueIndex.set(OptionalInt.of(i), innerValueIndex.getInt(i) + 1);
                }
            }
            insertAtIndex += 1;
        }

        int insertCount = insertItems.size();
        return () -> removeRows(insertAtOriginalIndex, insertCount);
    }

    @Override
    public SimulationRunnable removeRows(int index, int count) throws InternalException, UserException
    {
        List<TaggedValue> prevValues = getAllCollapsed(index, index + count);

        int[] firstRemovedInnerValueIndex = new int[valueStores.size()];
        int[] lastRemovedInnerValueIndex = new int[valueStores.size()];
        Arrays.fill(firstRemovedInnerValueIndex, -1);
        Arrays.fill(lastRemovedInnerValueIndex, -1);
        // Go through items and remove from innerValue storage:
        for (int i = index; i < index + count; i++)
        {
            int tag = tagStore.getInt(i);
            int valIndex = innerValueIndex.getInt(i);
            if (valIndex != -1)
            {
                if (firstRemovedInnerValueIndex[tag] == -1)
                    firstRemovedInnerValueIndex[tag] = valIndex;
                lastRemovedInnerValueIndex[tag] = valIndex;
            }
        }
        for (int i = index + count; i < filled(); i++)
        {
            int tag = tagStore.getInt(i);
            if (firstRemovedInnerValueIndex[tag] != -1)
            {
                int diff = lastRemovedInnerValueIndex[tag] - firstRemovedInnerValueIndex[tag] + 1;
                // Push all the indexes down:
                innerValueIndex.set(OptionalInt.of(i), innerValueIndex.getInt(i) - diff);
            }
        }
        // Now actually remove everything:
        tagStore.removeRows(index, count);
        innerValueIndex.removeRows(index, count);
        for (int i = 0; i < firstRemovedInnerValueIndex.length; i++)
        {
            if (firstRemovedInnerValueIndex[i] != -1)
            {
                ColumnStorage<?> columnStorage = valueStores.get(i);
                if (columnStorage == null)
                {
                    throw new InternalException("Column storage null yet some indexes point into it: " + i);
                }
                columnStorage.removeRows(firstRemovedInnerValueIndex[i], lastRemovedInnerValueIndex[i] - firstRemovedInnerValueIndex[i] + 1);
            }
        }
        return () -> {
            insertRows(index, prevValues);
        };
    }

    @Override
    public int filled()
    {
        return tagStore.filled();
    }

    /*
    @Override
    public TaggedValue get(int index) throws InternalException, UserException
    {
        if (index < 0 || index >= filled())
            throw new InternalException("Attempting to access invalid element: " + index + " of " + filled());
        return (TaggedValue) dataType.getCollapsed(index);
    }
    */

    public void addAll(List<TaggedValue> values) throws InternalException
    {
        for (TaggedValue v : values)
            addUnpacked(v);
    }

    protected void addUnpacked(TaggedValue value) throws InternalException
    {
        //Walk the tag structure, adding to store depending on tag:
        int tagIndex = value.getTagIndex();
        tagStore.add(tagIndex);

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
        @Nullable Object innerValue = value.getInner();
        if (innerValue == null)
            throw new InternalException("Inner value despite no inner type");
        addNoWarning(storage, innerValue);
    }

    @SuppressWarnings("unchecked")
    private static void addNoWarning(ColumnStorage storage, Object innerValue) throws InternalException
    {
        storage.add(innerValue);
    }

    public List<TaggedValue> getShrunk(int shrunkLength) throws UserException, InternalException
    {
        List<TaggedValue> s = new ArrayList<>();
        for (int i = 0; i < shrunkLength; i++)
        {
            s.add((TaggedValue)getType().getCollapsed(i));
        }
        return s;
    }
}
