package records.data;

import annotation.qual.UnknownIfValue;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import javafx.application.Platform;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.data.datatype.DataTypeValue.DataTypeVisitorGet;
import records.data.datatype.DataTypeValue.GetValue;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeId;
import records.error.InternalException;
import records.error.UserException;
import utility.ExFunction;
import utility.Pair;
import utility.SimulationFunction;
import utility.SimulationRunnable;
import utility.SimulationSupplier;
import utility.TaggedValue;
import utility.Utility;
import utility.Utility.ListEx;
import utility.Utility.ListExList;
import utility.gui.FXUtility;

import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 08/03/2017.
 */
public class EditableRecordSet extends RecordSet
{
    private List<EditableColumn> editableColumns;
    // Not final, because we are editable.  But it is the exact length of the dataset
    // (not the length loaded so far, or similar)
    private int curLength;

    /**
     *
     * @param columns
     * @param loadLength Loads the data, and returns the length of the data set
     * @throws InternalException
     * @throws UserException
     */
    @SuppressWarnings("initialization") // For getColumns()
    public EditableRecordSet(List<? extends ExFunction<RecordSet, ? extends EditableColumn>> columns, SimulationSupplier<Integer> loadLength) throws InternalException, UserException
    {
        super(columns);
        // Can't fail given the type we require above:
        this.editableColumns = Utility.mapList(this.getColumns(), c -> (EditableColumn)c);
        this.curLength = loadLength.get();
    }

    public EditableRecordSet(RecordSet copyFrom) throws InternalException, UserException
    {
        this(Utility.mapList(copyFrom.getColumns(), EditableRecordSet::copyColumn), copyFrom::getLength);
    }

    private static ExFunction<RecordSet, EditableColumn> copyColumn(@NonNull Column original)
    {
        @Nullable @Value Object defaultValue = (original instanceof EditableColumn) ? ((EditableColumn)original).getDefaultValue() : null;

        return rs -> original.getType().applyGet(new DataTypeVisitorGet<EditableColumn>()
        {
            private <T> List<@UnknownIfValue T> getAll(GetValue<@Value T> g) throws InternalException, UserException
            {
                List<@UnknownIfValue T> r = new ArrayList<>();
                for (int i = 0; original.indexValid(i); i++)
                {
                    @SuppressWarnings("value")
                    T t = g.get(i);
                    r.add(t);
                }
                return r;
            }

            @Override
            public EditableColumn number(GetValue<@Value Number> g, NumberInfo displayInfo) throws InternalException, UserException
            {
                return new MemoryNumericColumn(rs, original.getName(), displayInfo, getAll(g), Utility.cast(Utility.replaceNull(defaultValue, DataTypeUtility.value(Integer.valueOf(0))), Number.class));
            }

            @Override
            public EditableColumn text(GetValue<@Value String> g) throws InternalException, UserException
            {
                return new MemoryStringColumn(rs, original.getName(), getAll(g), Utility.cast(Utility.replaceNull(defaultValue, DataTypeUtility.value("")), String.class));
            }

            @Override
            public EditableColumn bool(GetValue<@Value Boolean> g) throws InternalException, UserException
            {
                return new MemoryBooleanColumn(rs, original.getName(), getAll(g), Utility.cast(Utility.replaceNull(defaultValue, DataTypeUtility.value(Boolean.FALSE)), Boolean.class));
            }

            @Override
            public EditableColumn date(DateTimeInfo dateTimeInfo, GetValue<@Value TemporalAccessor> g) throws InternalException, UserException
            {
                return new MemoryTemporalColumn(rs, original.getName(), dateTimeInfo, getAll(g), Utility.cast(Utility.replaceNull(defaultValue, dateTimeInfo.getDefaultValue()), TemporalAccessor.class));
            }

            @Override
            public EditableColumn tagged(TypeId typeName, ImmutableList<DataType> typeVars, ImmutableList<TagType<DataTypeValue>> tagTypes, GetValue<Integer> g) throws InternalException, UserException
            {
                List<TaggedValue> r = new ArrayList<>();
                for (int i = 0; original.indexValid(i); i++)
                {
                    int tagIndex = g.get(i);
                    @Nullable DataTypeValue inner = tagTypes.get(tagIndex).getInner();
                    r.add(new TaggedValue(tagIndex, inner == null ? null : inner.getCollapsed(i)));
                }
                return new MemoryTaggedColumn(rs, original.getName(), typeName, typeVars, Utility.mapList(tagTypes, t -> new TagType<>(t.getName(), t.getInner())), r, Utility.cast(Utility.replaceNull(defaultValue, DataTypeUtility.makeDefaultTaggedValue(tagTypes)), TaggedValue.class));
            }

            @Override
            public EditableColumn tuple(ImmutableList<DataTypeValue> types) throws InternalException, UserException
            {
                List<@Value Object @Value []> r = new ArrayList<>();
                for (int index = 0; original.indexValid(index); index++)
                {
                    @Value Object @Value [] array = DataTypeUtility.value(new Object[types.size()]);
                    for (int tupleIndex = 0; tupleIndex < types.size(); tupleIndex++)
                    {
                        array[tupleIndex] = types.get(tupleIndex).getCollapsed(index);
                    }
                    r.add(array);
                }
                @Value Object @Value [] tupleOfDefaults = DataTypeUtility.value(new Object[types.size()]);
                for (int i = 0; i < tupleOfDefaults.length; i++)
                {
                    tupleOfDefaults[i] = DataTypeUtility.makeDefaultValue(types.get(i));
                }
                return new MemoryTupleColumn(rs, original.getName(), Utility.mapList(types, t -> t), r, Utility.cast(Utility.replaceNull(defaultValue, tupleOfDefaults), (Class<@Value Object[]>) Object[].class));
            }

            @Override
            public EditableColumn array(@Nullable DataType inner, GetValue<Pair<Integer, DataTypeValue>> g) throws InternalException, UserException
            {
                List<ListEx> r = new ArrayList<>();
                for (int index = 0; original.indexValid(index); index++)
                {
                    List<Object> array = new ArrayList<>();
                    @NonNull Pair<Integer, DataTypeValue> details = g.get(index);
                    for (int indexInArray = 0; indexInArray < details.getFirst(); indexInArray++)
                    {
                        // Need to look for indexInArray, not index, to get full list:
                        array.add(details.getSecond().getCollapsed(indexInArray));
                    }
                    r.add(DataTypeUtility.value(array));
                }
                return new MemoryArrayColumn(rs, original.getName(), inner, r, Utility.cast(Utility.replaceNull(defaultValue, new ListExList(Collections.emptyList())), ListEx.class));
            }

            @Override
            public EditableColumn inferred(GetValue<@Value String> g) throws InternalException, UserException
            {
                return new InferTypeColumn(rs, original.getName(), getAll(g));
            }
        });
    }

    @NotNull
    public static EditableRecordSet newRecordSetSingleColumn() throws InternalException, UserException
    {
        return new EditableRecordSet(Collections.singletonList(rs -> new InferTypeColumn(rs, new ColumnId("C1"), ImmutableList.of(""))), () -> 0);
    }

    @Override
    public boolean indexValid(int index) throws UserException, InternalException
    {
        return index < curLength;
    }

    @Override
    public int getLength() throws UserException, InternalException
    {
        return curLength;
    }

    // The return is for testing
    public @Nullable SimulationRunnable insertRows(int index, int count)
    {
        List<SimulationRunnable> revert = new ArrayList<>();
        try
        {
            for (EditableColumn column : editableColumns)
            {
                revert.add(column.insertRows(index, count));
            }
        }
        catch (InternalException | UserException e)
        {
            Platform.runLater(() -> FXUtility.showError(e));
            for (SimulationRunnable revertOne : revert)
            {
                try
                {
                    revertOne.run();
                }
                catch (InternalException | UserException e2)
                {
                    Platform.runLater(() -> FXUtility.showError(e2));
                }
            }
            return null;
        }

        curLength += count;
        if (listener != null)
        {
            RecordSetListener listenerFinal = listener;
            Platform.runLater(() -> listenerFinal.removedAddedRows(index, 0, count));
        }
        // Re-run dependents:
        modified(null, null);

        return () -> {
            for (SimulationRunnable revertOne : revert)
            {
                revertOne.run();
            }
            curLength -= count;
        };
    }

    // The return is for testing
    public @Nullable SimulationRunnable removeRows(int deleteIndex, int count)
    {
        List<SimulationRunnable> revert = new ArrayList<>();
        try
        {
            for (EditableColumn column : editableColumns)
            {
                revert.add(column.removeRows(deleteIndex, count));
            }
        }
        catch (InternalException | UserException e)
        {
            Platform.runLater(() -> FXUtility.showError(e));
            for (SimulationRunnable revertOne : revert)
            {
                try
                {
                    revertOne.run();
                }
                catch (InternalException | UserException e2)
                {
                    Platform.runLater(() -> FXUtility.showError(e2));
                }
            }
            return null;
        }

        curLength -= count;
        if (listener != null)
        {
            RecordSetListener listenerFinal = listener;
            Platform.runLater(() -> listenerFinal.removedAddedRows(deleteIndex, count, 0));
        }
        // Re-run dependents
        modified(null, null);

        return () -> {
            for (SimulationRunnable revertOne : revert)
            {
                revertOne.run();
            }
            curLength += count;
        };
    }

    public void addColumn(SimulationFunction<RecordSet, ? extends EditableColumn> makeNewColumn) throws InternalException, UserException
    {
        EditableColumn col = makeNewColumn.apply(this);
        col.insertRows(0, getLength());
        columns.add(col);
        editableColumns.add(col);
        if (columns.stream().map(Column::getName).distinct().count() != columns.size())
        {
            throw new InternalException("Duplicate column names found");
        }
        Platform.runLater(() -> {
            if (listener != null)
                listener.addedColumn(col);
        });
        //Re-run dependents:
        modified(col.getName(), null);
    }

    public void deleteColumn(ColumnId deleteColumnName)
    {
        columns.removeIf(c -> c.getName().equals(deleteColumnName));
        editableColumns.removeIf(c -> c.getName().equals(deleteColumnName));
        Platform.runLater(() -> {
            if (listener != null)
                listener.removedColumn(deleteColumnName);
        });
        //Re-run dependents:
        modified(deleteColumnName, null);
    }
}
