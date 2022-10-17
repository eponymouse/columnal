/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.data;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.ProgressListener;;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.InvalidImmediateValueException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.simulation.SimulationRunnable;
import xyz.columnal.utility.TaggedValue;
import xyz.columnal.utility.Utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by neil on 05/11/2016.
 */
public class TaggedColumnStorage extends SparseErrorColumnStorage<@Value TaggedValue> implements ColumnStorage<@Value TaggedValue>
{
    // This stores the tag index of each item.
    private final NumericColumnStorage tagStore;
    // This stores the inner values
    private final ImmutableList<TagType<ColumnStorage<?>>> tagTypes;
    // Effectively a cached version of tagTypes:
    @OnThread(Tag.Any)
    private final DataTypeValue dataType;

    public <DT extends DataType> TaggedColumnStorage(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, List<TagType<DT>> copyTagTypes, @Nullable BeforeGet<TaggedColumnStorage> beforeGet, boolean isImmediateData) throws InternalException
    {
        super(isImmediateData);
        tagStore = new NumericColumnStorage(isImmediateData);
        ImmutableList.Builder<TagType<ColumnStorage<?>>> tagTypesBuilder = ImmutableList.builder();
        for (int i = 0; i < copyTagTypes.size(); i++)
        {
            TagType<? extends DataType> tagType = copyTagTypes.get(i);
            DataType inner = tagType.getInner();
            if (inner != null)
            {
                ColumnStorage<?> result = ColumnUtility.makeColumnStorage(inner, null, isImmediateData);
                tagTypesBuilder.add(new TagType<>(tagType.getName(), result));
            }
            else
            {
                tagTypesBuilder.add(new TagType<>(tagType.getName(), null));
            }
        }
        tagTypes = tagTypesBuilder.build();
        dataType = DataTypeValue.tagged(typeName, typeVars, Utility.<TagType<ColumnStorage<?>>, TagType<DataType>>mapListI(tagTypes, (TagType<ColumnStorage<?>> tt) -> tt.<DataType>map(t -> t.getType().getType())), new GetValueOrError<TaggedValue>()
        {
            @Override
            protected @OnThread(Tag.Simulation) void _beforeGet(int i, @Nullable ProgressListener prog) throws UserException, InternalException
            {
                if (beforeGet != null)
                    beforeGet.beforeGet(Utility.later(TaggedColumnStorage.this), i, prog);
            }

            @Override
            public TaggedValue _getWithProgress(int i, @Nullable ProgressListener prog) throws UserException, InternalException
            {
                int tagIndex = tagStore.getInt(i);
                if (tagIndex >= tagTypes.size())
                    throw new InternalException("Found tag index " + tagIndex + " but only know of " + tagTypes.size() + " tags");
                ColumnStorage<?> innerStorage = tagTypes.get(tagIndex).getInner();
                if (innerStorage == null)
                    return new TaggedValue(tagIndex, null, DataTypeUtility.fromTags(tagTypes));
                else
                    return new TaggedValue(tagIndex, innerStorage.getType().getCollapsed(i), DataTypeUtility.fromTags(tagTypes));
            }

            @Override
            public void _set(int index, @Nullable TaggedValue newValue) throws InternalException, UserException
            {
                int oldTag = tagStore.getInt(index);
                @Nullable Integer newTag = newValue == null ? null : newValue.getTagIndex();

                tagStore.set(OptionalInt.of(index), newTag == null ? 0 : newTag);

                for (int tagIndex = 0; tagIndex < tagTypes.size(); tagIndex++)
                {
                    ColumnStorage<?> colStore = tagTypes.get(tagIndex).getInner();
                    if (colStore != null)
                    {
                        // Remember that newTag may be null, hence this comparison of boxed integers:
                        if (Objects.equals((Integer) tagIndex, newTag) && newValue != null)
                        {
                            @Value Object newValueInner = newValue.getInner();
                            if (newValueInner == null)
                                throw new InternalException("Found blank inner value for tag which expects an inner value, tag " + tagIndex + " type: " + typeName);
                            colStore.getType().setCollapsed(index, Either.right(newValueInner));
                        }
                        else
                            colStore.getType().setCollapsed(index, Either.left("Attempting to fetch tagged inner value for invalid row"));
                    }
                    
                }
            }
        });
    }

    public <DT extends DataType> TaggedColumnStorage(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, List<TagType<DT>> copyTagTypes, boolean isImmediateData) throws InternalException
    {
        this(typeName, typeVars, copyTagTypes, null, isImmediateData);
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
        return () -> _removeRows(insertAtOriginalIndex, insertCount);
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
