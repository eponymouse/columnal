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

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.UnknownIfValue;
import annotation.qual.Value;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.application.Platform;
import xyz.columnal.id.ColumnId;
import xyz.columnal.log.ErrorHandler;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.DataTypeValue.DataTypeVisitorGet;
import xyz.columnal.data.datatype.DataTypeValue.GetValue;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.InvalidImmediateValueException;
import xyz.columnal.error.UserException;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.function.simulation.SimulationFunction;
import xyz.columnal.utility.function.simulation.SimulationFunctionInt;
import xyz.columnal.utility.function.simulation.SimulationRunnable;
import xyz.columnal.utility.function.simulation.SimulationSupplier;
import xyz.columnal.utility.TaggedValue;
import xyz.columnal.utility.TranslationUtility;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Utility.ListEx;
import xyz.columnal.utility.Utility.ListExList;
import xyz.columnal.utility.Utility.Record;
import xyz.columnal.utility.Utility.RecordMap;

import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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
    public <C extends EditableColumn> EditableRecordSet(List<SimulationFunction<RecordSet, C>> columns, SimulationSupplier<Integer> loadLength) throws InternalException, UserException
    {
        super(columns);
        // Can't fail given the type we require above:
        this.editableColumns = Utility.mapList(this.getColumns(), c -> (EditableColumn)c);
        this.curLength = loadLength.get();
    }

    public EditableRecordSet(RecordSet copyFrom) throws InternalException, UserException
    {
        this(Utility.<Column, SimulationFunction<RecordSet, EditableColumn>>mapList(copyFrom.getColumns(), EditableRecordSet::copyColumn), copyFrom::getLength);
    }

    public static SimulationFunction<RecordSet, EditableColumn> copyColumn(@NonNull Column original)
    {
        @Nullable @Value Object defaultValue = (original instanceof EditableColumn) ? ((EditableColumn)original).getDefaultValue() : null;

        return rs -> original.getType().applyGet(new DataTypeVisitorGet<EditableColumn>()
        {
            private <T extends @NonNull Object> List<Either<String, @UnknownIfValue T>> getAll(GetValue<@Value T> g) throws InternalException, UserException
            {
                List<Either<String, @UnknownIfValue T>> r = new ArrayList<>();
                for (int i = 0; original.indexValid(i); i++)
                {
                    try
                    {
                        @SuppressWarnings("valuetype")
                        T t = g.get(i);
                        r.add(Either.right(t));
                    }
                    catch (InvalidImmediateValueException e)
                    {
                        r.add(Either.left(e.getInvalid()));
                    }
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
            public EditableColumn tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tagTypes, GetValue<@Value TaggedValue> g) throws InternalException, UserException
            {
                List<Either<String, TaggedValue>> r = new ArrayList<>();
                for (int i = 0; original.indexValid(i); i++)
                {
                    try
                    {
                        r.add(Either.right(g.get(i)));
                    }
                    catch (InvalidImmediateValueException e)
                    {
                        r.add(Either.left(e.getInvalid()));
                    }
                }
                return new MemoryTaggedColumn(rs, original.getName(), typeName, typeVars, tagTypes, r, Utility.cast(Utility.replaceNull(defaultValue, DataTypeUtility.makeDefaultTaggedValue(tagTypes)), TaggedValue.class));
            }

            @Override
            public EditableColumn record(ImmutableMap<@ExpressionIdentifier String, DataType> types, GetValue<@Value Record> g) throws InternalException, UserException
            {
                List<Either<String, @Value Record>> r = new ArrayList<>();
                for (int index = 0; original.indexValid(index); index++)
                {
                    try
                    {
                        r.add(Either.<String, @Value Record>right(Utility.<Record>cast(g.get(index), Record.class)));
                    }
                    catch (InvalidImmediateValueException e)
                    {
                        r.add(Either.left(e.getInvalid()));
                    }
                }
                Map<@ExpressionIdentifier String, @Value Object> recordOfDefaults = new HashMap<>();
                for (Entry<@ExpressionIdentifier String, DataType> entry : types.entrySet())
                {
                    recordOfDefaults.put(entry.getKey(), DataTypeUtility.makeDefaultValue(entry.getValue()));
                }
                return new MemoryRecordColumn(rs, original.getName(), types, r, Utility.cast(Utility.replaceNull(defaultValue, DataTypeUtility.value(new RecordMap(recordOfDefaults))), (Class<@Value Record>) Record.class));
            }

            @Override
            public EditableColumn array(DataType inner, GetValue<@Value ListEx> g) throws InternalException, UserException
            {
                List<Either<String, ListEx>> r = new ArrayList<>();
                for (int index = 0; original.indexValid(index); index++)
                {
                    try
                    {
                        @NonNull @Value ListEx details = g.get(index);
                        ImmutableList.Builder<@Value Object> array = ImmutableList.builderWithExpectedSize(details.size());
                        for (int indexInArray = 0; indexInArray < details.size(); indexInArray++)
                        {
                            // Need to look for indexInArray, not index, to get full list:
                            array.add(details.get(indexInArray));
                        }
                        r.add(Either.right(DataTypeUtility.<@Value Object>value(array.build())));
                    }
                    catch (InvalidImmediateValueException e)
                    {
                        r.add(Either.left(e.getInvalid()));
                    }
                }
                return new MemoryArrayColumn(rs, original.getName(), inner, r, Utility.cast(Utility.replaceNull(defaultValue, new <@Value Object>ListExList(Collections.<@Value Object>emptyList())), ListEx.class));
            }
        });
    }

    public static EditableRecordSet newRecordSetSingleColumn(ColumnId name, DataType type, @Value Object defaultValue) throws InternalException, UserException
    {
        return new EditableRecordSet(Collections.<SimulationFunction<RecordSet, EditableColumn>>singletonList(ColumnUtility.makeImmediateColumn(type, name, defaultValue)::apply), () -> 0);
    }

    @Override
    public boolean indexValid(int index) throws UserException, InternalException
    {
        return index < curLength;
    }

    @Override
    @SuppressWarnings("units")
    public @TableDataRowIndex int getLength() throws UserException, InternalException
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
            ErrorHandler.getErrorHandler().showError(TranslationUtility.getString("error.inserting.rows"), e);
            for (SimulationRunnable revertOne : revert)
            {
                try
                {
                    revertOne.run();
                }
                catch (InternalException e2)
                {
                    ErrorHandler.getErrorHandler().showError(TranslationUtility.getString("error.reversing.row.insertion"), e2);
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
            ErrorHandler.getErrorHandler().showError(TranslationUtility.getString("error.removing.rows"), e);
            for (SimulationRunnable revertOne : revert)
            {
                try
                {
                    revertOne.run();
                }
                catch (InternalException e2)
                {
                    ErrorHandler.getErrorHandler().showError(TranslationUtility.getString("error.reversing.row.removal"), e2);
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

    public void addColumn(@Nullable ColumnId addBefore, SimulationFunctionInt<RecordSet, ? extends EditableColumn> makeNewColumn) throws InternalException, UserException
    {
        EditableColumn col = makeNewColumn.apply(this);
        col.insertRows(0, getLength());
        int targetIndex = addBefore == null ? columns.size() : Utility.findFirstIndex(columns, c -> c.getName().equals(addBefore)).orElse(columns.size());
        columns.add(targetIndex, col);
        editableColumns.add(targetIndex, col);
        if (columns.stream().map(Column::getName).distinct().count() != columns.size())
        {
            throw new UserException("Duplicate column names found");
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
