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

package xyz.columnal.transformations;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import javafx.beans.binding.ObjectExpression;
import javafx.scene.Scene;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.data.Column;
import xyz.columnal.data.Column.AlteredState;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.Table;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.id.ColumnId;
import xyz.columnal.id.TableId;
import xyz.columnal.log.Log;
import xyz.columnal.styled.StyledCSS;
import xyz.columnal.styled.StyledString;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.Expression.ColumnLookup;
import xyz.columnal.transformations.expression.IdentExpression;
import xyz.columnal.transformations.expression.QuickFix;
import xyz.columnal.transformations.expression.QuickFix.QuickFixAction;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.function.simulation.SimulationConsumer;

import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Lookup for filter and calculate.  Available columns are:
 * - per-row columns (with null table id) from
 * the source table (equivalent to  our table if the column
 * is unaltered)
 * - @table columns from the source table (equivalent to our table if the column
 * is unaltered)
 * -  @table columns from all tables that
 * are behind this in the dependency tree,
 * with non-null table id.
 */
public class MultipleTableLookup implements ColumnLookup
{
    // Only null during testing
    private final @Nullable TableId us;
    private final TableManager tableManager;
    private final @Nullable Table srcTable;
    private final @Nullable CalculationEditor editing;

    public static interface CalculationEditor
    {
        public ColumnId getCurrentlyEditingColumn();

        // Gives back an action which will make a new Calculate depending on the current Calculate,
        // with the currently editing expression moved there.
        @OnThread(Tag.FXPlatform)
        public SimulationConsumer<Pair<@Nullable ColumnId, Expression>> moveExpressionToNewCalculation();
    }

    public MultipleTableLookup(@Nullable TableId us, TableManager tableManager, @Nullable TableId srcTableId, @Nullable CalculationEditor editing)
    {
        this.us = us;
        this.tableManager = tableManager;
        this.srcTable = srcTableId == null ? null : tableManager.getSingleTableOrNull(srcTableId);
        this.editing = editing;
    }

    @Override
    public @Nullable QuickFix<Expression> getFixForIdent(@Nullable @ExpressionIdentifier String namespace, ImmutableList<@ExpressionIdentifier String> idents, @Recorded Expression target)
    {
        if (editing == null)
            return null;

        final ImmutableList<ColumnId> columnsFromSrc;
        final ImmutableList<ColumnId> columnsInUs;

        try
        {
            if (us != null)
            {
                Table ourTable = tableManager.getSingleTableOrNull(us);
                if (ourTable != null)
                    columnsInUs = ourTable.getData().getColumnIds();
                else
                    columnsInUs = ImmutableList.of();
            }
            else
                columnsInUs = ImmutableList.of();

            columnsFromSrc = srcTable == null ? ImmutableList.of() : srcTable.getData().getColumnIds();

            if ((namespace == null || namespace.equals("column")) && idents.size() == 1 && columnsInUs.contains(new ColumnId(idents.get(0))) && !columnsFromSrc.contains(new ColumnId(idents.get(0))))
            {
                return new QuickFix<>(StyledString.s("Make a new calculation that can use this table's " + idents.get(0)), ImmutableList.of(), target, new QuickFixAction()
                {
                    @Override
                    public @OnThread(Tag.FXPlatform)
                    @Nullable SimulationConsumer<Pair<@Nullable ColumnId, Expression>> doAction(TypeManager typeManager, ObjectExpression<Scene> editorSceneProperty)
                    {
                        return editing.moveExpressionToNewCalculation();
                    }
                });
            }
        }
        catch (InternalException | UserException e)
        {
            if (e instanceof InternalException)
                Log.log(e);
        }
        return null;
    }

    @Override
    public Stream<ClickedReference> getPossibleColumnReferences(TableId tableId, ColumnId columnId)
    {
        // Handle us and source table specially:
        if (us != null && tableId.equals(us))
        {
            // Can't refer to an edited column
            if (editing != null && columnId.equals(editing.getCurrentlyEditingColumn()))
                return Stream.empty();

            Table usTable = tableManager.getSingleTableOrNull(us);
            if (usTable == null)
                return Stream.empty();
            try
            {
                Column column = usTable.getData().getColumnOrNull(columnId);
                if (column == null || column.getAlteredState() == AlteredState.OVERWRITTEN)
                    return Stream.empty();
                return Stream.of(new ClickedReference(tableId, columnId)
                {
                    @Override
                    public Expression getExpression()
                    {
                        return IdentExpression.column(getColumnId());
                    }
                });
            }
            catch (InternalException | UserException e)
            {
                if (e instanceof InternalException)
                    Log.log(e);
                return Stream.empty();
            }
        }
        if (srcTable != null && tableId.equals(srcTable.getId()))
        {
            try
            {
                Column column = srcTable.getData().getColumnOrNull(columnId);
                if (column == null)
                    return Stream.empty();
                return Stream.of(new ClickedReference(tableId, columnId)
                {
                    @Override
                    public Expression getExpression()
                    {
                        return IdentExpression.column(getColumnId());
                    }
                });
            }
            catch (InternalException | UserException e)
            {
                if (e instanceof InternalException)
                    Log.log(e);
                return Stream.empty();
            }
        }
        // For everything else fall back to usual:
        return getAvailableColumnReferences().filter(c -> tableId.equals(c.getFirst()) && columnId.equals(c.getSecond())).map(c -> new ClickedReference(tableId, columnId)
        {
            @Override
            public Expression getExpression()
            {
                return IdentExpression.column(c.getFirst(), c.getSecond());
            }
        });
    }

    @Override
    public Stream<TableId> getAvailableTableReferences()
    {
        return tableManager.getAllTablesAvailableTo(us, false).stream().map(t -> t.getId());
    }

    public Stream<Pair<@Nullable TableId, ColumnId>> getAvailableColumnReferences()
    {
        return tableManager.getAllTablesAvailableTo(us, false).stream().<Pair<@Nullable TableId, ColumnId>>flatMap(new Function<Table, Stream<Pair<@Nullable TableId, ColumnId>>>()
        {
            @Override
            public Stream<Pair<@Nullable TableId, ColumnId>> apply(Table t)
            {
                try
                {
                    boolean isUsOrSrc = Objects.equals(us, t.getId()) || (srcTable != null && Objects.equals(t.getId(), srcTable.getId()));
                    return t.getData().getColumns().stream().<Pair<@Nullable TableId, ColumnId>>map(c -> new Pair<@Nullable TableId, ColumnId>(isUsOrSrc ? null : t.getId(), c.getName()));
                }
                catch (UserException e)
                {
                    return Stream.<Pair<@Nullable TableId, ColumnId>>empty();
                }
                catch (InternalException e)
                {
                    Log.log(e);
                    return Stream.<Pair<@Nullable TableId, ColumnId>>empty();
                }
            }
        }).distinct();
    }

    @Override
    public @Nullable FoundTable getTable(@Nullable TableId tableName) throws UserException, InternalException
    {
        if (tableName == null && srcTable != null)
            tableName = srcTable.getId();
        ImmutableList<Table> available = tableManager.getAllTablesAvailableTo(us, false);
        TableId tableNameFinal = tableName;
        Table t = available.stream().filter(table -> table.getId().equals(tableNameFinal)).findFirst().orElse(null);
        if (t == null)
            return null;

        return new FoundTableActual(t);
    }

    @Override
    public @Nullable FoundColumn getColumn(@Recorded Expression expression, @Nullable TableId tableId, ColumnId columnId)
    {
        try
        {
            @Nullable Pair<TableId, RecordSet> rs = null;
            if (tableId == null)
            {
                if (srcTable != null)
                    rs = new Pair<>(srcTable.getId(), srcTable.getData());
            }
            else
            {
                Table table = tableManager.getSingleTableOrNull(tableId);
                if (table != null)
                    rs = new Pair<>(table.getId(), table.getData());
            }

            if (rs != null)
            {
                Column column = rs.getSecond().getColumnOrNull(columnId);
                if (column == null)
                    return null;
                DataTypeValue columnType = column.getType();
                return new FoundColumn(rs.getFirst(), srcTable != null && srcTable.getId().equals(rs.getFirst()), columnType, checkRedefined(expression, tableId, columnId));
            }
        }
        catch (InternalException | UserException e)
        {
            Log.log(e);
        }
        return null;
    }

    // If column is redefined in this table, issue a warning
    private @Nullable Pair<StyledString, @Nullable QuickFix<Expression>> checkRedefined(@Recorded Expression expression, @Nullable TableId tableId, ColumnId columnId)
    {
        if (tableId == null && us != null)
        {
            try
            {
                Table ourTable = tableManager.getSingleTableOrNull(us);
                if (ourTable == null)
                    return null;
                RecordSet rs = ourTable.getData();
                Column c = rs.getColumnOrNull(columnId);
                if (c != null && editing != null && !Objects.equals(columnId, editing.getCurrentlyEditingColumn()) && c.getAlteredState() == AlteredState.OVERWRITTEN
                )
                {
                    return new Pair<>(StyledString.concat(StyledString.s("Note: column "), StyledString.styled(c.getName().getRaw(), new StyledCSS("column-reference")), StyledString.s(" is re-calculated in this table, but this reference will use the value from the source table.")), getFixForIdent("column", ImmutableList.of(columnId.getRaw()), expression));
                }
            }
            catch (InternalException | UserException e)
            {
                if (e instanceof InternalException)
                    Log.log(e);
            }
        }
        return null;
    }
}
