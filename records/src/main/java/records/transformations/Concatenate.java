package records.transformations;

import annotation.qual.Value;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.ColumnId;
import records.data.KnownLengthRecordSet;
import records.data.RecordSet;
import records.data.Table;
import records.data.TableId;
import records.data.TableManager;
import records.data.Transformation;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.NumberInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeValue;
import records.data.datatype.TypeId;
import records.error.FunctionInt;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

/**
 * Created by neil on 18/01/2017.
 */
@OnThread(Tag.Simulation)
public class Concatenate extends Transformation
{
    @OnThread(Tag.Any)
    private final List<TableId> sources;

    // If there is a column which is not in every source table, it must appear in this map
    // If any column is mapped to Optional.empty then it should be omitted in the concatenated version, even if it appears in all.
    // If it's mapped to Optional.of then it should be given that value in the concatenated version
    // for any source table which lacks the column.
    private final Map<ColumnId, Optional<@Value Object>> missingValues;

    @OnThread(Tag.Any)
    private @Nullable String error = null;
    @OnThread(Tag.Any)
    private final @Nullable RecordSet recordSet;
    private final String title;

    @SuppressWarnings("initialization")
    public Concatenate(TableManager mgr, @Nullable TableId tableId, String title, List<TableId> sources, Map<ColumnId, Optional<@Value Object>> missingVals) throws InternalException
    {
        super(mgr, tableId);
        this.sources = sources;
        this.title = title;
        this.missingValues = new HashMap<>(missingVals);

        KnownLengthRecordSet rs = null;
        try
        {
            List<Table> tables = Utility.mapListEx(sources, getManager()::getSingleTableOrThrow);
            LinkedHashMap<ColumnId, DataType> prevColumns = getColumnsNameAndType(tables.get(0));
            int totalLength = tables.get(0).getData().getLength();
            List<Integer> ends = new ArrayList<>();
            ends.add(totalLength);

            // Remove all from prevColumns that have an omit instruction:
            for (Iterator<Entry<ColumnId, DataType>> iterator = prevColumns.entrySet().iterator(); iterator.hasNext(); )
            {
                Entry<ColumnId, DataType> entry = iterator.next();
                @Nullable Optional<@Value Object> instruction = missingValues.get(entry.getKey());
                if (instruction != null && !instruction.isPresent())
                    iterator.remove();
            }
            // After this point, prevColumns will never have an entry for columns with an omit instruction

            // There are 4 categories of tables:
            // 1. Present in all, type matches in all, instruction is omit or no instruction.
            // 2. Type doesn't match in every instance where column name is present and instruction is not to omit
            // 3. It is present in some, and type matches in all, and it has a default value/instruction
            // 4. It is present in some, and type matches in all whre present, and it doesn't have a default value
            // Numbers 1 and 3 are valid, but 2 and 4 are not.

            for (int i = 1; i < tables.size(); i++)
            {
                LinkedHashMap<ColumnId, DataType> curColumns = getColumnsNameAndType(tables.get(i));
                // Need to check that for every entry in curColumns, the type matches that of
                // prevColumns if present

                Set<ColumnId> matchedPrev = new HashSet<>();
                for (Entry<ColumnId, DataType> curEntry : curColumns.entrySet())
                {
                    @Nullable DataType prev = prevColumns.get(curEntry.getKey());
                    if (prev == null)
                    {
                        // This is fine, as long as there is an instruction for that column
                        @Nullable Optional<@Value Object> onMissing = missingValues.get(curEntry.getKey());
                        if (onMissing == null)
                        {
                            // Case 4
                            throw new UserException("Column " + curEntry.getKey() + " from table " + tables.get(i).getId() + " is not present in all tables and the handling is unspecified");
                        }
                        if (onMissing.isPresent())
                        {
                            // Case 4
                            prevColumns.put(curEntry.getKey(), curEntry.getValue());
                        }
                        // Otherwise if empty optional, leave out column
                    }
                    else
                    {
                        // Was already there; check type matches
                        if (!prev.equals(curEntry.getValue()))
                        {
                            // No need to check for omit instruction: if it was in prevColumns, it's not omitted
                            // Case 2
                            throw new UserException("Type does not match for column " + curEntry.getKey() + " in table " + tables.get(i).getId() + " and previous tables: " + prev + " vs " + curEntry.getValue());
                        }
                        // else type matches, in which case it's fine
                        matchedPrev.add(curEntry.getKey());
                    }
                }
                // Handle all those already present in prev but which did not occur in cur:
                Collection<@KeyFor("prevColumns") ColumnId> notPresentInCur = Sets.difference(prevColumns.keySet(), matchedPrev);
                for (ColumnId id : notPresentInCur)
                {
                    // Since it was in prev, it can't have an omit instruction, make sure it has default value:
                    @Nullable Optional<@Value Object> instruction = missingValues.get(id);
                    if (instruction == null)
                    {
                        // Case 4
                        throw new UserException("Column " + id + " is not present in all tables and the handling is unspecified");
                    }
                    if (!instruction.isPresent())
                        throw new InternalException("Column " + id + " was instructed to omit but is present in prev");
                }

                int length = tables.get(i).getData().getLength();
                totalLength += length;
                ends.add(totalLength);
            }
            rs = new KnownLengthRecordSet(title, Utility.<Entry<ColumnId, DataType>, FunctionInt<RecordSet, Column>>mapList(new ArrayList<>(prevColumns.entrySet()), (Entry<ColumnId, DataType> oldC) -> new FunctionInt<RecordSet, Column>()
            {
                @Override
                @OnThread(Tag.Simulation)
                public Column apply(RecordSet rs) throws InternalException, UserException
                {
                    return new Column(rs)
                    {

                        public @MonotonicNonNull DataTypeValue type;

                        @Override
                        public @OnThread(Tag.Any) ColumnId getName()
                        {
                            return oldC.getKey();
                        }

                        @Override
                        public @OnThread(Tag.Any) DataTypeValue getType() throws InternalException, UserException
                        {
                            if (type == null)
                            {
                                type = DataTypeValue.copySeveral(oldC.getValue(), (concatenatedRow, prog) ->
                                {
                                    for (int srcTableIndex = 0; srcTableIndex < ends.size(); srcTableIndex++)
                                    {
                                        if (concatenatedRow < ends.get(srcTableIndex))
                                        {
                                            int start = srcTableIndex == 0 ? 0 : ends.get(srcTableIndex - 1);
                                            // First one with end beyond our target must be right one:
                                            return new Pair<DataTypeValue, Integer>(tables.get(srcTableIndex).getData().getColumn(oldC.getKey()).getType(), concatenatedRow - start);
                                        }
                                    }
                                    throw new InternalException("Attempting to access beyond end of concatenated tables: index" + (concatenatedRow + 1) + " but only length " + ends.get(ends.size() - 1));
                                });
                            }
                            return type;
                        }
                    };
                }
            }), totalLength);
        }
        catch (UserException e)
        {
            String msg = e.getLocalizedMessage();
            if (msg != null)
                this.error = msg;
        }
        this.recordSet = rs;
    }

    @Override
    public @OnThread(Tag.FXPlatform) String getTransformationLabel()
    {
        return "concat";
    }

    @Override
    public @OnThread(Tag.FXPlatform) List<TableId> getSources()
    {
        return sources;
    }

    @Override
    public @OnThread(Tag.FXPlatform) TransformationEditor edit()
    {
        throw new RuntimeException("TODO");
    }

    @Override
    protected @OnThread(Tag.Any) String getTransformationName()
    {
        return "concatenate";
    }

    @Override
    protected @OnThread(Tag.FXPlatform) List<String> saveDetail(@Nullable File destination)
    {
        return Collections.emptyList(); // TODO
    }

    @Override
    public @OnThread(Tag.Any) RecordSet getData() throws UserException, InternalException
    {
        if (recordSet == null)
            throw new UserException(error == null ? "Unknown error" : error);
        return recordSet;
    }

    private static LinkedHashMap<ColumnId, DataType> getColumnsNameAndType(Table t) throws InternalException, UserException
    {
        List<Column> columns = new ArrayList<>(t.getData().getColumns());
        Collections.sort(columns, Comparator.comparing(c -> c.getName()));
        LinkedHashMap<ColumnId, DataType> r = new LinkedHashMap<>();
        for (Column c : columns)
        {
            r.put(c.getName(), c.getType());
        }
        return r;
    }
}
