package records.importers;

import annotation.qual.Value;
import annotation.units.TableDataColIndex;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.nullness.qual.KeyForBottom;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.units.qual.UnitsBottom;
import org.checkerframework.checker.nullness.qual.NonNull;
import records.data.ColumnId;
import records.data.EditableColumn;
import records.data.EditableRecordSet;
import records.data.MemoryNumericColumn;
import records.data.MemoryStringColumn;
import records.data.MemoryTaggedColumn;
import records.data.MemoryTemporalColumn;
import records.data.RecordSet;
import records.data.TableManager;
import records.data.columntype.BlankColumnType;
import records.data.columntype.CleanDateColumnType;
import records.data.columntype.ColumnType;
import records.data.columntype.NumericColumnType;
import records.data.columntype.OrBlankColumnType;
import records.data.columntype.TextColumnType;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.NumberInfo;
import records.error.InternalException;
import records.error.UserException;
import records.gui.stable.ReadOnlyStringColumnHandler;
import records.gui.stable.ColumnDetails;
import records.importers.gui.ImportChoicesDialog.SourceInfo;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExFunction;
import utility.FXPlatformConsumer;
import utility.TaggedValue;
import utility.Utility;

import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;

public class ImporterUtility
{
    //package-visible
    static SourceInfo makeSourceInfo(List<List<String>> vals)
    {
        ImmutableList.Builder<ColumnDetails> columnHandlers = ImmutableList.builder();
        if (!vals.isEmpty())
        {
            int widest = vals.stream().mapToInt(l -> l.size()).max().orElse(0);
            for (int columnIndex = 0; columnIndex < widest; columnIndex++)
            {
                @SuppressWarnings("units")
                @TableDataColIndex int columnIndexFinal = columnIndex;
                columnHandlers.add(new ColumnDetails(new ColumnId("Column " + (columnIndex + 1)), DataType.TEXT, null, new ReadOnlyStringColumnHandler(columnIndexFinal)
                {
                    @Override
                    @OnThread(Tag.FXPlatform)
                    public void fetchValueForRow(int rowIndex, FXPlatformConsumer<String> withValue)
                    {
                        String s;
                        try
                        {
                            s = vals.get(rowIndex).get(columnIndexFinal);
                        }
                        catch (IndexOutOfBoundsException e)
                        {
                            s = "<Missing>";
                        }
                        withValue.consume(s);
                    }

                    @OnThread(Tag.Simulation)
                    @Override
                    public @Value Object getValue(int rowIndex) throws InternalException, UserException
                    {
                        return vals.get(rowIndex).get(columnIndexFinal);
                    }
                }));
            }
        }
        @SuppressWarnings("units")
        @TableDataRowIndex int valSize = vals.size();
        return new SourceInfo(columnHandlers.build(), valSize);
    }

    // Pads each row with extra blanks so that all rows have the same length
    // Modifies list (and inner lists) in-place.
    public static void rectangularise(List<List<String>> vals)
    {
        int maxRowLength = vals.stream().mapToInt(l -> l.size()).max().orElse(0);
        for (List<String> row : vals)
        {
            while (row.size() < maxRowLength)
                row.add("");
        }
    }

    @NonNull
    @OnThread(Tag.Simulation)
    public static EditableRecordSet makeEditableRecordSet(TableManager mgr, List<List<String>> vals, Format format) throws InternalException, UserException
    {
        @SuppressWarnings({"keyfor", "units"})
        @KeyForBottom @UnitsBottom List<ExFunction<RecordSet, EditableColumn>> columns = new ArrayList<>();
        for (int i = 0; i < format.columnTypes.size(); i++)
        {
            ColumnInfo columnInfo = format.columnTypes.get(i);
            int iFinal = i;
            List<String> slice = Utility.sliceSkipBlankRows(vals, format.headerRows, iFinal);
            ColumnType columnType = columnInfo.type;
            if (columnType instanceof NumericColumnType)
            {
                //TODO remove prefix
                // TODO treat maybe blank as a tagged type
                columns.add(rs ->
                {
                    NumericColumnType numericColumnType = (NumericColumnType) columnType;
                    return new MemoryNumericColumn(rs, columnInfo.title, new NumberInfo(numericColumnType.unit), slice.stream().map(numericColumnType::removePrefix));
                });
            }
            else if (columnType instanceof TextColumnType)
            {
                columns.add(rs -> new MemoryStringColumn(rs, columnInfo.title, slice, ""));
            }
            else if (columnType instanceof CleanDateColumnType)
            {
                columns.add(rs -> new MemoryTemporalColumn(rs, columnInfo.title, ((CleanDateColumnType) columnType).getDateTimeInfo(), Utility.<String, TemporalAccessor>mapListInt(slice, s -> ((CleanDateColumnType) columnType).parse(s)), DateTimeInfo.DEFAULT_VALUE));
            }
            else if (columnType instanceof OrBlankColumnType && ((OrBlankColumnType)columnType).getInner() instanceof NumericColumnType)
            {
                OrBlankColumnType or = (OrBlankColumnType) columnType;
                NumericColumnType inner = (NumericColumnType) or.getInner();
                DataType numberType = DataType.number(new NumberInfo(inner.unit));
                @Nullable DataType type = mgr.getTypeManager().getMaybeType().instantiate(
                    ImmutableList.of(numberType)
                );
                @NonNull DataType typeFinal = type;
                columns.add(rs -> new MemoryTaggedColumn(rs, columnInfo.title, typeFinal.getTaggedTypeName(), ImmutableList.of(numberType), typeFinal.getTagTypes(), Utility.mapListEx(slice, item -> {
                    if (item.isEmpty() || item.trim().equals(or.getBlankString()))
                        return new TaggedValue(0, null);
                    else
                        return new TaggedValue(1, DataTypeUtility.value(Utility.parseNumber(inner.removePrefix(item))));
                }), new TaggedValue(0, null)));
            }
            else if (!(columnType instanceof BlankColumnType))
            {
                throw new InternalException("Unhandled column type: " + columnType.getClass());
            }
            // If it's blank, should we add any column?
            // Maybe if it has title?                }
        }

        @Initialized int len = vals.size() - format.headerRows - (int)vals.stream().skip(format.headerRows).filter(r -> r.stream().allMatch(String::isEmpty)).count();

        return new EditableRecordSet(columns, () -> len);
    }
}
