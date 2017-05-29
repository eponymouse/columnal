package records.data;

import annotation.qual.Value;
import javafx.application.Platform;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.data.datatype.DataTypeValue.DataTypeVisitorGet;
import records.data.datatype.DataTypeValue.GetValue;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeId;
import records.error.FunctionInt;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import utility.Pair;
import utility.SimulationSupplier;
import utility.TaggedValue;
import utility.Utility;
import utility.Utility.ListEx;

import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by neil on 08/03/2017.
 */
public class EditableRecordSet extends RecordSet
{
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
    public EditableRecordSet(List<? extends FunctionInt<RecordSet, ? extends Column>> columns, SimulationSupplier<Integer> loadLength) throws InternalException, UserException
    {
        super(columns);
        this.curLength = loadLength.get();
    }

    public EditableRecordSet(RecordSet copyFrom) throws InternalException, UserException
    {
        super(Utility.mapList(copyFrom.getColumns(), EditableRecordSet::copyColumn));
        curLength = copyFrom.getLength();
    }

    private static FunctionInt<RecordSet, Column> copyColumn(@NonNull Column original)
    {
        return rs -> original.getType().applyGet(new DataTypeVisitorGet<Column>()
        {
            private <T> List<T> getAll(GetValue<T> g) throws InternalException, UserException
            {
                List<T> r = new ArrayList<>();
                for (int i = 0; original.indexValid(i); i++)
                {
                    r.add(g.get(i));
                }
                return r;
            }

            @Override
            public Column number(GetValue<Number> g, NumberInfo displayInfo) throws InternalException, UserException
            {
                return new MemoryNumericColumn(rs, original.getName(), displayInfo, getAll(g));
            }

            @Override
            public Column text(GetValue<String> g) throws InternalException, UserException
            {
                return new MemoryStringColumn(rs, original.getName(), getAll(g));
            }

            @Override
            public Column bool(GetValue<Boolean> g) throws InternalException, UserException
            {
                return new MemoryBooleanColumn(rs, original.getName(), getAll(g));
            }

            @Override
            public Column date(DateTimeInfo dateTimeInfo, GetValue<TemporalAccessor> g) throws InternalException, UserException
            {
                return new MemoryTemporalColumn(rs, original.getName(), dateTimeInfo, getAll(g));
            }

            @Override
            public Column tagged(TypeId typeName, List<TagType<DataTypeValue>> tagTypes, GetValue<Integer> g) throws InternalException, UserException
            {
                List<TaggedValue> r = new ArrayList<>();
                for (int i = 0; original.indexValid(i); i++)
                {
                    int tagIndex = g.get(i);
                    @Nullable DataTypeValue inner = tagTypes.get(tagIndex).getInner();
                    r.add(new TaggedValue(tagIndex, inner == null ? null : inner.getCollapsed(i)));
                }
                return new MemoryTaggedColumn(rs, original.getName(), typeName, Utility.mapList(tagTypes, t -> new TagType<>(t.getName(), t.getInner())), r);
            }

            @Override
            public Column tuple(List<DataTypeValue> types) throws InternalException, UserException
            {
                List<Object[]> r = new ArrayList<>();
                for (int index = 0; original.indexValid(index); index++)
                {
                    @Value Object[] array = new Object[types.size()];
                    for (int tupleIndex = 0; tupleIndex < types.size(); tupleIndex++)
                    {
                        array[tupleIndex] = types.get(tupleIndex).applyGet(this);
                    }
                    r.add(array);
                }
                return new MemoryTupleColumn(rs, original.getName(), Utility.mapList(types, t -> t), r);
            }

            @Override
            public Column array(@Nullable DataType inner, GetValue<Pair<Integer, DataTypeValue>> g) throws InternalException, UserException
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
                return new MemoryArrayColumn(rs, original.getName(), inner, r);
            }
        });
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

    public void addRows(int count) throws InternalException
    {
        int newRowIndex = curLength;
        curLength += count;
        if (listener != null)
        {
            RecordSetListener listenerFinal = listener;
            Platform.runLater(() -> listenerFinal.removedAddedRows(newRowIndex, 0, count));
        }
        // TODO re-run dependents
    }
}
