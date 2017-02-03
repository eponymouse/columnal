package records.transformations;

import annotation.qual.Value;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;

/**
 * Created by neil on 18/01/2017.
 */
@OnThread(Tag.Simulation)
public class Concatenate extends Transformation
{
    @OnThread(Tag.Any)
    private final List<TableId> sources;

    // If there is a column which is not in every source table, it should appear in this map
    // If it's mapped to Optional.empty then it should be omitted in the concatenated version.
    // If it's mapped to Optional.of then it should be given that value in the concatenated version
    // for any source table which lacks the column.
    private final Map<Pair<TableId, ColumnId>, Optional<@Value Object>> missingValues;

    @OnThread(Tag.Any)
    private @Nullable String error = null;
    @OnThread(Tag.Any)
    private final @Nullable RecordSet recordSet;
    private final String title;

    @SuppressWarnings("initialization")
    public Concatenate(TableManager mgr, @Nullable TableId tableId, String title, List<TableId> sources, Map<Pair<TableId, ColumnId>, Optional<@Value Object>> missingValues) throws InternalException
    {
        super(mgr, tableId);
        this.sources = sources;
        this.title = title;
        this.missingValues = new HashMap<>(missingValues);

        KnownLengthRecordSet rs = null;
        try
        {
            List<Table> tables = Utility.mapListEx(sources, getManager()::getSingleTableOrThrow);
            List<Pair<ColumnId, DataType>> prevColumns = getColumnsNameAndType(tables.get(0));
            int totalLength = tables.get(0).getData().getLength();
            List<Integer> ends = new ArrayList<>();
            ends.add(totalLength);
            for (int i = 1; i < tables.size(); i++)
            {
                List<Pair<ColumnId, DataType>> curColumns = getColumnsNameAndType(tables.get(i));
                if (!prevColumns.equals(curColumns))
                {
                    throw new UserException("Columns in concatenated source tables do not completely match"); // TODO provide more info
                }
                int length = tables.get(i).getData().getLength();
                totalLength += length;
                ends.add(totalLength);

            }
            rs = new KnownLengthRecordSet(title, Utility.<Pair<ColumnId, DataType>, FunctionInt<RecordSet, Column>>mapList(prevColumns, (Pair<ColumnId, DataType> oldC) -> new FunctionInt<RecordSet, Column>()
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
                            return oldC.getFirst();
                        }

                        @Override
                        public @OnThread(Tag.Any) DataTypeValue getType() throws InternalException, UserException
                        {
                            if (type == null)
                            {
                                type = DataTypeValue.copySeveral(oldC.getSecond(), (concatenatedRow, prog) ->
                                {
                                    for (int srcTableIndex = 0; srcTableIndex < ends.size(); srcTableIndex++)
                                    {
                                        if (concatenatedRow < ends.get(srcTableIndex))
                                        {
                                            int start = srcTableIndex == 0 ? 0 : ends.get(srcTableIndex - 1);
                                            // First one with end beyond our target must be right one:
                                            return new Pair<DataTypeValue, Integer>(tables.get(srcTableIndex).getData().getColumn(oldC.getFirst()).getType(), concatenatedRow - start);
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

    private static List<Pair<ColumnId, DataType>> getColumnsNameAndType(Table t) throws InternalException, UserException
    {
        List<Column> columns = new ArrayList<>(t.getData().getColumns());
        Collections.sort(columns, Comparator.comparing(c -> c.getName()));
        List<Pair<ColumnId, DataType>> r = new ArrayList<>();
        for (Column c : columns)
        {
            r.add(new Pair<ColumnId, DataType>(c.getName(), c.getType()));
        }
        return r;
    }
}
