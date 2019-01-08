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
import records.error.InvalidImmediateValueException;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by neil on 05/11/2016.
 */
public class TaggedColumnStorage extends SparseErrorColumnStorage<TaggedValue> implements ColumnStorage<TaggedValue>
{
    // This stores the tag index of each item.
    private final NumericColumnStorage tagStore;
    // This stores the inner values
    private final List<TagType<ColumnStorage<?>>> tagTypes;
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
        tagTypes = new ArrayList<>();
        for (int i = 0; i < copyTagTypes.size(); i++)
        {
            TagType<? extends DataType> tagType = copyTagTypes.get(i);
            DataType inner = tagType.getInner();
            if (inner != null)
            {
                ColumnStorage<?> result = DataTypeUtility.makeColumnStorage(inner, null);
                tagTypes.add(new TagType<>(tagType.getName(), result));
            }
            else
            {
                tagTypes.add(new TagType<>(tagType.getName(), null));
            }
        }
        dataType = DataTypeValue.tagged(typeName, typeVars, Utility.mapListI(tagTypes, (TagType<ColumnStorage<?>> tt) -> tt.map(t -> t.getType())), new GetValueOrError<Integer>()
        {
            @Override
            public Integer _getWithProgress(int i, ProgressListener prog) throws UserException, InternalException
            {
                if (beforeGet != null)
                    beforeGet.beforeGet(TaggedColumnStorage.this, i, prog);
                return tagStore.getInt(i);
            }

            @Override
            public void _set(int index, @Nullable Integer newTag) throws InternalException, UserException
            {
                int oldTag = tagStore.getInt(index);
                if (newTag.intValue() == oldTag)
                    return; // No need to change anything here.

                tagStore.set(OptionalInt.of(index), newTag);

                for (int i = 0; i < tagTypes.size(); i++)
                {
                    ColumnStorage<?> colStore = tagTypes.get(i).getInner();
                    if (i != index)
                        colStore.getType().setCollapsed(i, null);
                }
            }
        });
    }
    
    @OnThread(Tag.Any)
    public DataTypeValue getType()
    {
        return dataType;
    }

    @SuppressWarnings("unchecked")
    @Override
    public SimulationRunnable _insertRows(int insertAtOriginalIndex, List<@Nullable TaggedValue> insertItems) throws InternalException
    {
        int insertAtIndex = insertAtOriginalIndex;
        tagStore.addAll(insertAtIndex, insertItems.stream().map(t -> t == null ? 0 : t.getTagIndex()));
        for (int i = 0; i < tagTypes.size(); i++)
        {
            int iFinal = i;
            ColumnStorage colStore = tagTypes.get(i).getInner();
            if (colStore != null)
                colStore.insertRows(insertAtIndex, insertItems.stream().map((@Nullable TaggedValue t) -> {
                    if (t == null)
                        return Either.<String, Object>left("Internal tagged error");
                    else if (t.getTagIndex() == iFinal && t.getInner() != null)
                        return Either.<String, Object>right(t.getInner());
                    else 
                        return Either.<String, Object>left("Internal tagged error");
                }).collect(Collectors.<Either<String, Object>>toList()));
        }
        
        int insertCount = insertItems.size();
        return () -> removeRows(insertAtOriginalIndex, insertCount);
    }

    @Override
    public SimulationRunnable _removeRows(int index, int count) throws InternalException
    {
        ArrayList<SimulationRunnable> reInsert = new ArrayList<>();
        // Now actually remove everything:
        reInsert.add(tagStore.removeRows(index, count));
        for (TagType<ColumnStorage<?>> tagType : tagTypes)
        {
            ColumnStorage<?> inner = tagType.getInner();
            if (inner != null)
                reInsert.add(inner.removeRows(index, count));
        }
        
        return () -> {
            for (SimulationRunnable re : reInsert)
            {
                re.run();
            }
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

    @Override
    public void addAll(Stream<Either<String, TaggedValue>> values) throws InternalException
    {
        insertRows(filled(), values.collect(Collectors.<Either<String, TaggedValue>>toList()));
    }

    @SuppressWarnings("unchecked")
    private static void addNoWarning(ColumnStorage storage, Object innerValue) throws InternalException
    {
        storage.add(innerValue);
    }

    public List<Either<String, TaggedValue>> getShrunk(int shrunkLength) throws UserException, InternalException
    {
        List<Either<String, TaggedValue>> s = new ArrayList<>();
        for (int i = 0; i < shrunkLength; i++)
        {
            try
            {
                s.add(Either.right((TaggedValue) getType().getCollapsed(i)));
            }
            catch (InvalidImmediateValueException e)
            {
                s.add(Either.left(e.getInvalid()));
            }
            
        }
        return s;
    }
}
