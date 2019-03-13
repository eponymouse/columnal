package records.gui.table;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.ImmediateDataSource;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.error.InternalException;
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
import utility.Pair;
import utility.Utility;
import utility.Workers;
import utility.Workers.Priority;
import utility.gui.FXUtility;

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
        // TODO apply the type and default value.
        new EditImmediateColumnDialog(parent, parent.getManager(),columnId, type, false).showAndWait().ifPresent(columnDetails -> {
            Workers.onWorkerThread("Editing column", Priority.SAVE, () -> {
                FXUtility.alertOnError_("Error saving column", () ->
                    parent.getManager().edit(data.getId(), null, new TableAndColumnRenames(ImmutableMap.of(data.getId(), new Pair<>(data.getId(), ImmutableMap.of(columnId, columnDetails.columnId)))))
                );
            });
        });
    }
}
