package records.gui.table;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.PolyNull;
import records.data.Column;
import records.data.ColumnId;
import records.data.EditableColumn;
import records.data.EditableRecordSet;
import records.data.ImmediateDataSource;
import records.data.RecordSet;
import records.data.TableAndColumnRenames;
import records.data.TableManager.TableMaker;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.error.InternalException;
import records.error.InvalidImmediateValueException;
import records.error.UserException;
import records.gui.EditAggregateSourceDialog;
import records.gui.EditColumnExpressionDialog;
import records.gui.EditImmediateColumnDialog;
import records.gui.View;
import records.transformations.Calculate;
import records.transformations.SummaryStatistics;
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

import java.util.ArrayList;
import java.util.List;

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
        
        new EditColumnExpressionDialog(parent, parent.getManager().getSingleTableOrNull(calc.getSrcTableId()), columnId, expression, new MultipleTableLookup(calc.getId(), parent.getManager(), calc.getSrcTableId()), null).showAndWait().ifPresent(newDetails -> {
            ImmutableMap<ColumnId, Expression> newColumns = Utility.appendToMap(calc.getCalculatedColumns(), newDetails.getFirst(), newDetails.getSecond());
            Workers.onWorkerThread("Editing column", Priority.SAVE, () -> {
                FXUtility.alertOnError_("Error saving column", () ->
                    parent.getManager().edit(calc.getId(), () -> new Calculate(parent.getManager(), calc.getDetailsForCopy(), calc.getSrcTableId(), newColumns), null)
                );
            });
        });
    }

    @OnThread(Tag.FXPlatform)
    static void editAggregateSplitBy(View parent, SummaryStatistics aggregate)
    {
        new EditAggregateSourceDialog(parent, null, parent.getManager().getSingleTableOrNull(aggregate.getSrcTableId()), aggregate.getSplitBy()).showAndWait().ifPresent(splitBy -> Workers.onWorkerThread("Edit aggregate", Priority.SAVE, () -> {
            FXUtility.alertOnError_("Error editing aggregate", () -> parent.getManager().edit(aggregate.getId(), () -> new SummaryStatistics(parent.getManager(), aggregate.getDetailsForCopy(), aggregate.getSrcTableId(), aggregate.getColumnExpressions(), splitBy), null));
        }));
    }

    @OnThread(Tag.FXPlatform)
    static void editColumn_Agg(View parent, SummaryStatistics agg, ColumnId columnId) throws InternalException, UserException
    {
        // Start with the existing value.
        ImmutableList<Pair<ColumnId, Expression>> oldColumns = agg.getColumnExpressions();
        Expression expression = Utility.pairListToMap(oldColumns).get(columnId);
        if (expression != null)
        {
            new EditColumnExpressionDialog(parent, parent.getManager().getSingleTableOrNull(agg.getSrcTableId()), columnId, expression, agg.getColumnLookup(), null).showAndWait().ifPresent(newDetails -> {
                ImmutableList.Builder<Pair<ColumnId, Expression>> newColumns = ImmutableList.builderWithExpectedSize(oldColumns.size() + 1);
                boolean added = false;
                for (Pair<ColumnId, Expression> oldColumn : oldColumns)
                {
                    if (oldColumn.getFirst().equals(columnId) && !added)
                    {
                        newColumns.add(newDetails);
                    }
                    else
                    {
                        newColumns.add(oldColumn);
                    }
                }
                Workers.onWorkerThread("Editing column", Priority.SAVE, () -> {
                    FXUtility.alertOnError_("Error saving column", () ->
                        parent.getManager().edit(agg.getId(), () -> new SummaryStatistics(parent.getManager(), agg.getDetailsForCopy(), agg.getSrcTableId(), newColumns.build(), agg.getSplitBy()), null)
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
                    @Nullable TableMaker makeReplacement = null;
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
                    parent.getManager().edit(data.getId(), makeReplacement, new TableAndColumnRenames(ImmutableMap.of(data.getId(), new Pair<>(data.getId(), ImmutableMap.of(columnId, columnDetails.columnId)))));
                });
            });
        });
    }
}
