package records.importers;

import annotation.qual.Value;
import annotation.units.TableDataColIndex;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import log.Log;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.nullness.qual.KeyForBottom;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.units.qual.UnitsBottom;
import records.data.ColumnId;
import records.data.EditableColumn;
import records.data.EditableRecordSet;
import records.data.MemoryBooleanColumn;
import records.data.MemoryNumericColumn;
import records.data.MemoryStringColumn;
import records.data.MemoryTaggedColumn;
import records.data.MemoryTemporalColumn;
import records.data.RecordSet;
import records.data.TableManager;
import records.data.columntype.BlankColumnType;
import records.data.columntype.BoolColumnType;
import records.data.columntype.CleanDateColumnType;
import records.data.columntype.ColumnType;
import records.data.columntype.NumericColumnType;
import records.data.columntype.OrBlankColumnType;
import records.data.columntype.TextColumnType;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.gui.stable.ReadOnlyStringColumnHandler;
import records.gui.stable.ColumnDetails;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.ExFunction;
import utility.FXPlatformConsumer;
import utility.SimulationFunction;
import utility.TaggedValue;
import utility.Utility;

import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ImporterUtility
{
    /**
     * Pads each row with extra blanks so that all rows have the same length.
     * Removes any rows which contain only blanks.s
     * Modifies list (and inner lists) in-place.
     */
    public static void rectangulariseAndRemoveBlankRows(List<ArrayList<String>> vals)
    {
        int maxRowLength = vals.stream().mapToInt(l -> l.size()).max().orElse(0);
        for (Iterator<ArrayList<String>> iterator = vals.iterator(); iterator.hasNext(); )
        {
            List<String> row = iterator.next();
            if (row.stream().allMatch(String::isEmpty))
            {
                iterator.remove();
            }
            else
            {
                while (row.size() < maxRowLength)
                    row.add("");
            }
        }
    }

    @OnThread(Tag.Simulation)
    public static EditableRecordSet makeEditableRecordSet(TypeManager mgr, List<? extends List<String>> vals, ImmutableList<ColumnInfo> columnTypes) throws InternalException, UserException
    {
        @SuppressWarnings({"keyfor", "units"})
        @KeyForBottom @UnitsBottom List<SimulationFunction<RecordSet, EditableColumn>> columns = new ArrayList<>();
        for (int i = 0; i < columnTypes.size(); i++)
        {
            ColumnInfo columnInfo = columnTypes.get(i);
            int iFinal = i;
            List<String> slice = Utility.sliceSkipBlankRows(vals, 0, iFinal);
            ColumnType columnType = columnInfo.type;
            if (columnType instanceof NumericColumnType)
            {
                //TODO remove prefix
                // TODO treat maybe blank as a tagged type
                columns.add(rs ->
                {
                    NumericColumnType numericColumnType = (NumericColumnType) columnType;
                    return new MemoryNumericColumn(rs, columnInfo.title, new NumberInfo(numericColumnType.unit), slice.stream().map(numericColumnType::removePrefixAndSuffix));
                });
            }
            else if (columnType instanceof TextColumnType || columnType instanceof BlankColumnType)
            {
                columns.add(rs -> new MemoryStringColumn(rs, columnInfo.title, Utility.mapList(slice, x -> Either.<String, String>right(x)), ""));
            }
            else if (columnType instanceof CleanDateColumnType)
            {
                columns.add(rs -> {
                    DateTimeInfo dateTimeInfo = ((CleanDateColumnType) columnType).getDateTimeInfo();
                    return new MemoryTemporalColumn(rs, columnInfo.title, dateTimeInfo, Utility.<String, Either<String, TemporalAccessor>>mapListInt(slice, s -> ((CleanDateColumnType) columnType).parse(s)), dateTimeInfo.getDefaultValue());
                });
            }
            else if (columnType instanceof BoolColumnType)
            {
                BoolColumnType bool = (BoolColumnType) columnType;
                columns.add(rs -> new MemoryBooleanColumn(rs, columnInfo.title, Utility.mapList(slice, bool::parse), false));
            }
            else if (columnType instanceof OrBlankColumnType && ((OrBlankColumnType)columnType).getInner() instanceof NumericColumnType)
            {
                OrBlankColumnType or = (OrBlankColumnType) columnType;
                NumericColumnType inner = (NumericColumnType) or.getInner();
                DataType numberType = DataType.number(new NumberInfo(inner.unit));
                @Nullable DataType type = mgr.getMaybeType().instantiate(
                    ImmutableList.of(Either.<Unit, DataType>right(numberType)), mgr
                );
                @NonNull DataType typeFinal = type;
                columns.add(rs -> new MemoryTaggedColumn(rs, columnInfo.title, DataTypeUtility.getTaggedTypeName(typeFinal), ImmutableList.of(Either.<Unit, DataType>right(numberType)), DataTypeUtility.getTagTypes(typeFinal), Utility.mapListEx(slice, (String item) -> {
                    if (item.isEmpty() || item.trim().equals(or.getBlankString()))
                        return Either.<String, TaggedValue>right(new TaggedValue(0, null, mgr.getMaybeType()));
                    else
                        return Utility.parseNumberOpt(inner.removePrefixAndSuffix(item)).map(n -> Either.<String, TaggedValue>right(new TaggedValue(1, DataTypeUtility.value(n), mgr.getMaybeType()))).orElse(Either.left(item));
                }), new TaggedValue(0, null, mgr.getMaybeType())));
            }
            else
            {
                throw new InternalException("Unhandled column type: " + columnType.getClass());
            }
            // If it's blank, should we add any column?
            // Maybe if it has title?                }
        }

        @Initialized int len = vals.size() - (int)vals.stream().filter(r -> r.stream().allMatch(String::isEmpty)).count();

        return new EditableRecordSet(columns, () -> len);
    }
}
