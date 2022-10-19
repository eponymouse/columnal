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

package xyz.columnal.gui.table.app;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import xyz.columnal.data.ColumnUtility;
import xyz.columnal.log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.Column;
import xyz.columnal.id.ColumnId;
import xyz.columnal.data.EditableColumn;
import xyz.columnal.data.EditableRecordSet;
import xyz.columnal.data.ImmediateDataSource;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.Table;
import xyz.columnal.id.TableAndColumnRenames;
import xyz.columnal.data.TableManager.TableMaker;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.DataTypeUtility.ComparableValue;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.InvalidImmediateValueException;
import xyz.columnal.error.UserException;
import xyz.columnal.gui.AggregateSplitByPane;
import xyz.columnal.gui.EditAggregateSplitByDialog;
import xyz.columnal.gui.EditColumnExpressionDialog;
import xyz.columnal.gui.EditImmediateColumnDialog;
import xyz.columnal.gui.EditImmediateColumnDialog.InitialFocus;
import xyz.columnal.gui.View;
import xyz.columnal.transformations.Aggregate;
import xyz.columnal.transformations.Calculate;
import xyz.columnal.data.RenameOnEdit;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.MultipleTableLookup;
import xyz.columnal.transformations.expression.IdentExpression;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.function.simulation.SimulationFunction;
import xyz.columnal.utility.TranslationUtility;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Workers;
import xyz.columnal.utility.Workers.Priority;
import xyz.columnal.utility.gui.FXUtility;

import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class TransformationEdits
{
    @OnThread(Tag.FXPlatform)
    static void editColumn_Calc(View parent, Calculate calc, ColumnId columnId) throws InternalException, UserException
    {
        // Start with the existing value.
        Expression expression = calc.getCalculatedColumns().get(columnId);
        // If that doesn't exist, copy the name of the column if appropriate:
        if (expression == null && calc.getData().getColumns().stream().anyMatch(c -> c.getName().equals(columnId)))
            expression = IdentExpression.column(columnId); 
        // expression may still be null
        
        EditColumnExpressionDialog.withoutSidePane(parent, parent.getManager().getSingleTableOrNull(calc.getSrcTableId()), columnId, expression, ed -> new MultipleTableLookup(calc.getId(), parent.getManager(), calc.getSrcTableId(), ed == null ? null : calc.makeEditor(ed)), () -> Calculate.makeTypeState(parent.getManager()), null).showAndWait().ifPresent(newDetails -> {
            ImmutableMap<ColumnId, Expression> newColumns = Utility.appendToMap(calc.getCalculatedColumns(), newDetails.columnId, newDetails.expression, columnId);
            Workers.onWorkerThread("Editing column", Priority.SAVE, () -> {
                FXUtility.alertOnError_(TranslationUtility.getString("error.saving.column"), () ->
                    parent.getManager().edit(calc, id -> new Calculate(parent.getManager(), calc.getDetailsForCopy(id), calc.getSrcTableId(), newColumns), RenameOnEdit.UNNEEDED /* edit column won't affect it */)
                );
            });
        });
    }

    @OnThread(Tag.FXPlatform)
    static void editAggregateSplitBy(View parent, Aggregate aggregate)
    {
        CompletableFuture<Optional<Pair<ColumnId, ImmutableList<String>>>> exampleSplitColumn = new CompletableFuture<>();
        new Thread(() -> {
            @Nullable Pair<ColumnId, ImmutableList<String>> example = null;
            long limit = System.currentTimeMillis() + 500;
            Workers.onWorkerThread("Fetch example split", Priority.FETCH, () -> {
                try
                {
                    exampleSplitColumn.complete(Optional.ofNullable(calculateExampleSplit(limit, parent.getManager().getSingleTableOrNull(aggregate.getSrcTableId()))));
                }
                catch (InternalException e)
                {
                    Log.log(e);
                }
                catch (UserException e)
                {
                    // Just ignore
                }
            });
            try
            {
                example = exampleSplitColumn.get(500, TimeUnit.MILLISECONDS).orElse(null);
            }
            catch (Throwable t)
            {
                // Never mind, just show the dialog without
            }
            final @Nullable Pair<ColumnId, ImmutableList<String>> exampleFinal = example;
            FXUtility.runAfter(() -> {
                new EditAggregateSplitByDialog(parent, null, parent.getManager().getSingleTableOrNull(aggregate.getSrcTableId()), exampleFinal, aggregate.getSplitBy()).showAndWait().ifPresent(splitBy -> Workers.onWorkerThread("Edit aggregate", Priority.SAVE, () -> {
                    FXUtility.alertOnError_(TranslationUtility.getString("error.editing.aggregate"), () -> parent.getManager().edit(aggregate, id -> new Aggregate(parent.getManager(), aggregate.getDetailsForCopy(id), aggregate.getSrcTableId(), aggregate.getColumnExpressions(), splitBy), RenameOnEdit.ifOldAuto(Aggregate.suggestedName(splitBy, aggregate.getColumnExpressions()))));
                }));
            });
        }).start();
    }

    @OnThread(Tag.Simulation)
    private static @Nullable Pair<ColumnId, ImmutableList<String>> calculateExampleSplit(long end, @Nullable Table table) throws InternalException, UserException
    {
        if (table == null)
            return null;
        
        RecordSet data = table.getData();
        // Check first 100 rows:
        int len = Math.min(100, data.getLength());
        int bestGuessSize = len;
        @Nullable Pair<ColumnId, ImmutableList<String>> bestGuess = null;
        for (Column column : data.getColumns())
        {
            // No point taking longer than the time-out
            if (System.currentTimeMillis() > end - 50)
                return bestGuess;
            TreeSet<ComparableValue> values = new TreeSet<>();
            DataTypeValue columnType = column.getType();
            for (int i = 0; i < len; i++)
            {
                try
                {
                    values.add(new ComparableValue(columnType.getCollapsed(i)));
                }
                catch (UserException e)
                {
                    // Never mind, just skip that row
                }
            }
            if (values.size() > 1 && values.size() < bestGuessSize)
            {
                ImmutableList.Builder<String> stringItems = ImmutableList.builder();
                for (ComparableValue value : Utility.iterableStream(values.stream().limit(3)))
                {
                    stringItems.add(DataTypeUtility.valueToString(value.getValue()));
                }
                bestGuess = new Pair<>(column.getName(), stringItems.build());
                bestGuessSize = values.size();
                if (values.size() < len / 4)
                {
                    return bestGuess;
                }
            }        
        }
        return bestGuess;
    }

    @OnThread(Tag.FXPlatform)
    static void editColumn_Agg(View parent, Aggregate agg, ColumnId columnId) throws InternalException, UserException
    {
        // Start with the existing value.
        ImmutableList<Pair<ColumnId, Expression>> oldColumns = agg.getColumnExpressions();
        Expression expression = Utility.pairListToMap(oldColumns).get(columnId);
        if (expression != null)
        {
            AggregateSplitByPane.editColumn(parent, parent.getManager().getSingleTableOrNull(agg.getSrcTableId()), columnId, expression, _ed -> agg.getColumnLookup(), () -> Aggregate.makeTypeState(parent.getManager()), null, agg.getSplitBy()).showAndWait().ifPresent(newDetails -> {
                ImmutableList.Builder<Pair<ColumnId, Expression>> newColumns = ImmutableList.builderWithExpectedSize(oldColumns.size() + 1);
                boolean added = false;
                for (Pair<ColumnId, Expression> oldColumn : oldColumns)
                {
                    if (oldColumn.getFirst().equals(columnId) && !added)
                    {
                        newColumns.add(new Pair<>(newDetails.columnId, newDetails.expression));
                    }
                    else
                    {
                        newColumns.add(oldColumn);
                    }
                }
                Workers.onWorkerThread("Editing column", Priority.SAVE, () -> {
                    FXUtility.alertOnError_(TranslationUtility.getString("error.saving.column"), () -> {
                        ImmutableList<Pair<ColumnId, Expression>> newColumnsBuilt = newColumns.build();
                        parent.getManager().edit(agg, id -> new Aggregate(parent.getManager(), agg.getDetailsForCopy(id), agg.getSrcTableId(), newColumnsBuilt, newDetails.extra), RenameOnEdit.ifOldAuto(Aggregate.suggestedName(newDetails.extra, newColumnsBuilt)));
                    });
                });
            });
        }
    }

    @OnThread(Tag.FXPlatform)
    static void editColumn_IDS(View parent, ImmediateDataSource data, ColumnId columnId, @Nullable DataType type, InitialFocus initialFocus)
    {
        new EditImmediateColumnDialog(parent, parent.getManager(),columnId, type, false, initialFocus).showAndWait().ifPresent(columnDetails -> {
            Workers.onWorkerThread("Editing column", Priority.SAVE, () -> {
                FXUtility.alertOnError_(TranslationUtility.getString("error.saving.column"), () -> {
                    @Nullable TableMaker<ImmediateDataSource> makeReplacement = null;
                    // If column type and default value are unaltered, don't need to re-do table:
                    final Column oldColumn = data.getData().getColumn(columnId);
                    if (!(oldColumn instanceof EditableColumn))
                        throw new InternalException("Non-editable column in original data set");
                    // Important to check type first, because if type has changed,
                    // default values cannot be compared:
                    if (!columnDetails.dataType.equals(oldColumn.getType().getType()) || Utility.compareValues(((EditableColumn)oldColumn).getDefaultValue(), columnDetails.defaultValue) != 0)
                    {
                        // Need to redo whole table to edit the column type/default:
                        int length = data.getData().getLength();
                        
                        List<SimulationFunction<RecordSet, EditableColumn>> columns = Utility.mapListEx(data.getData().getColumns(), c -> {
                            if (c.getName().equals(columnId))
                            {
                                ImmutableList.Builder<Either<String, @Value Object>> newValues = ImmutableList.builderWithExpectedSize(length);
                                for (int i = 0; i < length; i++)
                                {
                                    String stringVersion;
                                    try
                                    {
                                        stringVersion = DataTypeUtility.valueToString(c.getType().getCollapsed(i));
                                    }
                                    catch (InvalidImmediateValueException e)
                                    {
                                        stringVersion = e.getInvalid();
                                    }
                                    // Now turn String back into the new type:
                                    newValues.add(columnDetails.dataType.loadSingleItem(stringVersion.trim()));
                                }
                                
                                return ColumnUtility.makeImmediateColumn(columnDetails.dataType, c.getName(), newValues.build(), columnDetails.defaultValue);
                            }
                            else
                                return EditableRecordSet.copyColumn(c);
                        });
                        EditableRecordSet newRecordSet = new <EditableColumn>EditableRecordSet(columns, () -> length);
                        makeReplacement = () -> new ImmediateDataSource(parent.getManager(), data.getDetailsForCopy(data.getId()), newRecordSet);
                    }
                    parent.getManager().<ImmediateDataSource>editData(data.getId(), makeReplacement, new TableAndColumnRenames(ImmutableMap.of(data.getId(), new Pair<>(data.getId(), ImmutableMap.of(columnId, columnDetails.columnId)))));
                });
            });
        });
    }
}
