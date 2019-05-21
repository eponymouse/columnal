package records.gui.table;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.ColumnId;
import records.data.EditableColumn;
import records.data.EditableRecordSet;
import records.data.ImmediateDataSource;
import records.data.RecordSet;
import records.data.Table;
import records.data.TableAndColumnRenames;
import records.data.TableManager.TableMaker;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeUtility.ComparableValue;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.InvalidImmediateValueException;
import records.error.UserException;
import records.gui.AggregateSplitByPane;
import records.gui.EditAggregateSplitByDialog;
import records.gui.EditColumnExpressionDialog;
import records.gui.EditImmediateColumnDialog;
import records.gui.View;
import records.transformations.Aggregate;
import records.transformations.Calculate;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.MultipleTableLookup;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.SimulationFunction;
import utility.Utility;
import utility.Workers;
import utility.Workers.Priority;
import utility.gui.FXUtility;

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
            expression = new ColumnReference(columnId, ColumnReferenceType.CORRESPONDING_ROW); 
        // expression may still be null
        
        EditColumnExpressionDialog.withoutSidePane(parent, parent.getManager().getSingleTableOrNull(calc.getSrcTableId()), columnId, expression, ed -> new MultipleTableLookup(calc.getId(), parent.getManager(), calc.getSrcTableId(), ed), () -> Calculate.makeTypeState(parent.getManager()), null).showAndWait().ifPresent(newDetails -> {
            ImmutableMap<ColumnId, Expression> newColumns = Utility.appendToMap(calc.getCalculatedColumns(), newDetails.columnId, newDetails.expression);
            Workers.onWorkerThread("Editing column", Priority.SAVE, () -> {
                FXUtility.alertOnError_("Error saving column", () ->
                    parent.getManager().edit(calc.getId(), () -> new Calculate(parent.getManager(), calc.getDetailsForCopy(), calc.getSrcTableId(), newColumns), null)
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
                    FXUtility.alertOnError_("Error editing aggregate", () -> parent.getManager().edit(aggregate.getId(), () -> new Aggregate(parent.getManager(), aggregate.getDetailsForCopy(), aggregate.getSrcTableId(), aggregate.getColumnExpressions(), splitBy), null));
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
                    stringItems.add(DataTypeUtility.valueToString(columnType.getType(), value.getValue(), null));
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
                    FXUtility.alertOnError_("Error saving column", () ->
                        parent.getManager().edit(agg.getId(), () -> new Aggregate(parent.getManager(), agg.getDetailsForCopy(), agg.getSrcTableId(), newColumns.build(), newDetails.extra), null)
                    );
                });
            });
        }
    }

    @OnThread(Tag.FXPlatform)
    static void editColumn_IDS(View parent, ImmediateDataSource data, ColumnId columnId, @Nullable DataType type)
    {
        new EditImmediateColumnDialog(parent, parent.getManager(),columnId, type, false).showAndWait().ifPresent(columnDetails -> {
            Workers.onWorkerThread("Editing column", Priority.SAVE, () -> {
                FXUtility.alertOnError_("Error saving column", () -> {
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
                                        stringVersion = DataTypeUtility.valueToString(c.getType().getType(), c.getType().getCollapsed(i), null);
                                    }
                                    catch (InvalidImmediateValueException e)
                                    {
                                        stringVersion = e.getInvalid();
                                    }
                                    // Now turn String back into the new type:
                                    newValues.add(columnDetails.dataType.loadSingleItem(stringVersion));
                                }
                                
                                return columnDetails.dataType.makeImmediateColumn(c.getName(), newValues.build(), columnDetails.defaultValue);
                            }
                            else
                                return EditableRecordSet.copyColumn(c);
                        });
                        EditableRecordSet newRecordSet = new <EditableColumn>EditableRecordSet(columns, () -> length);
                        makeReplacement = () -> new ImmediateDataSource(parent.getManager(), data.getDetailsForCopy(), newRecordSet);
                    }
                    parent.getManager().<ImmediateDataSource>edit(data.getId(), makeReplacement, new TableAndColumnRenames(ImmutableMap.of(data.getId(), new Pair<>(data.getId(), ImmutableMap.of(columnId, columnDetails.columnId)))));
                });
            });
        });
    }
}
