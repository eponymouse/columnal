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

package xyz.columnal.importers;

import annotation.qual.ImmediateValue;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.nullness.qual.KeyForBottom;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.units.qual.UnitsBottom;
import xyz.columnal.data.EditableColumn;
import xyz.columnal.data.EditableRecordSet;
import xyz.columnal.data.MemoryBooleanColumn;
import xyz.columnal.data.MemoryNumericColumn;
import xyz.columnal.data.MemoryStringColumn;
import xyz.columnal.data.MemoryTaggedColumn;
import xyz.columnal.data.MemoryTemporalColumn;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.columntype.BlankColumnType;
import xyz.columnal.data.columntype.BoolColumnType;
import xyz.columnal.data.columntype.CleanDateColumnType;
import xyz.columnal.data.columntype.ColumnType;
import xyz.columnal.data.columntype.NumericColumnType;
import xyz.columnal.data.columntype.OrBlankColumnType;
import xyz.columnal.data.columntype.TextColumnType;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.simulation.SimulationFunction;
import xyz.columnal.utility.TaggedValue;
import xyz.columnal.utility.Utility;

import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

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
                        return Utility.parseNumberOpt(inner.removePrefixAndSuffix(item)).map(new Function<@ImmediateValue Number, Either<String, TaggedValue>>()
                        {
                            @Override
                            public Either<String, TaggedValue> apply(@ImmediateValue Number n)
                            {
                                return Either.<String, TaggedValue>right(new TaggedValue(1, n, mgr.getMaybeType()));
                            }
                        }).orElse(Either.left(item));
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
