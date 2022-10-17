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
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.Column;
import xyz.columnal.data.NumericColumnStorage;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.SingleSourceTransformation;
import xyz.columnal.data.Table;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.Transformation;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.error.expressions.ExpressionErrorException;
import xyz.columnal.error.expressions.ExpressionErrorException.EditableExpression;
import xyz.columnal.grammar.Versions.ExpressionVersion;
import xyz.columnal.id.TableAndColumnRenames;
import xyz.columnal.id.TableId;
import xyz.columnal.transformations.expression.BooleanLiteral;
import xyz.columnal.transformations.expression.BracketedStatus;
import xyz.columnal.transformations.expression.ErrorAndTypeRecorderStorer;
import xyz.columnal.transformations.expression.EvaluateState;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.Expression.ColumnLookup;
import xyz.columnal.transformations.expression.Expression.SaveDestination;
import xyz.columnal.transformations.expression.ExpressionUtil;
import xyz.columnal.transformations.expression.IdentExpression;
import xyz.columnal.transformations.expression.TypeState;
import xyz.columnal.transformations.expression.visitor.ExpressionVisitorStream;
import xyz.columnal.transformations.function.FunctionList;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.function.simulation.SimulationFunction;
import xyz.columnal.utility.Utility;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;

/**
 * Created by neil on 23/11/2016.
 */
@OnThread(Tag.Simulation)
public class Filter extends VisitableTransformation implements SingleSourceTransformation
{
    private static final String PREFIX = "KEEPIF";
    public static final String NAME = "filter";
    private final TableId srcTableId;
    private final @Nullable Table src;
    // Not actually a column by itself, but holds a list of integers so reasonable to re-use:
    // Each item is a source index in the original list
    private final NumericColumnStorage indexMap;
    // Maps original row indexes to errors:
    private final HashMap<Integer, @Localized String> errorsDuringFilter = new HashMap<>();
    private final @Nullable RecordSet recordSet;
    @OnThread(Tag.Any)
    private String error;
    private int nextIndexToExamine = 0;
    @OnThread(Tag.Any)
    private final Expression filterExpression;
    private @MonotonicNonNull DataType type;
    private boolean typeChecked = false;

    public Filter(TableManager mgr, InitialLoadDetails initialLoadDetails, TableId srcTableId, Expression filterExpression) throws InternalException
    {
        super(mgr, initialLoadDetails);
        this.srcTableId = srcTableId;
        this.src = mgr.getSingleTableOrNull(srcTableId);
        this.indexMap = new NumericColumnStorage(false);
        this.filterExpression = filterExpression;
        this.error = "Unknown error";

        @Nullable RecordSet theRecordSet = null;
        try
        {
            if (src != null)
            {
                List<SimulationFunction<RecordSet, Column>> columns = new ArrayList<>();
                RecordSet data = src.getData();
                ColumnLookup columnLookup = new MultipleTableLookup(getId(), mgr, src.getId(), null);
                for (Column c : data.getColumns())
                {
                    columns.add(rs -> new Column(rs, c.getName())
                    {
                        @Override
                        @SuppressWarnings({"nullness", "initialization"})
                        public @OnThread(Tag.Any) DataTypeValue getType() throws InternalException, UserException
                        {
                            return addManualEditSet(getName(), c.getType().copyReorder(i ->
                            {
                                fillIndexMapTo(i, columnLookup, data);
                                @Nullable @Localized String error = errorsDuringFilter.get(i);
                                if (error != null)
                                    throw new UserException(error);
                                return DataTypeUtility.value(indexMap.getInt(i));
                            }));
                        }
                        
                        @Override
                        public @OnThread(Tag.Any) AlteredState getAlteredState()
                        {
                            return AlteredState.FILTERED_OR_REORDERED;
                        }
                    });
                }

                theRecordSet = new RecordSet(columns)
                {
                    @Override
                    public boolean indexValid(int index) throws UserException, InternalException
                    {
                        if (index < indexMap.filled())
                            return true;

                        Utility.later(Filter.this).fillIndexMapTo(index, columnLookup, data);
                        return index < indexMap.filled();
                    }
                };
            }
        }
        catch (UserException e)
        {
            String msg = e.getLocalizedMessage();
            if (msg != null)
                this.error = msg;
        }
        this.recordSet = theRecordSet;
    }

    private void fillIndexMapTo(int index, ColumnLookup data, RecordSet recordSet) throws UserException, InternalException
    {
        if (type == null)
        {
            if (!typeChecked)
            {
                ErrorAndTypeRecorderStorer typeRecorder = new ErrorAndTypeRecorderStorer();
                // Must set it before, in case it throws:
                typeChecked = true;
                @SuppressWarnings("recorded")
                @Nullable TypeExp checked = filterExpression.checkExpression(data, makeTypeState(getManager().getTypeManager()), typeRecorder);
                @Nullable DataType typeFinal = null;
                if (checked != null)
                    typeFinal = typeRecorder.recordLeftError(getManager().getTypeManager(), FunctionList.getFunctionLookup(getManager().getUnitManager()), filterExpression, checked.toConcreteType(getManager().getTypeManager()));
                
                if (typeFinal == null)
                    throw new ExpressionErrorException(typeRecorder.getAllErrors().findFirst().orElse(StyledString.s("Unknown type error")), new EditableExpression(filterExpression, srcTableId, data, () -> makeTypeState(getManager().getTypeManager()), DataType.BOOLEAN)
                    {
                        @Override
                        @OnThread(Tag.Simulation)
                        public Table replaceExpression(Expression changed) throws InternalException
                        {
                            return new Filter(getManager(), getDetailsForCopy(getId()), Filter.this.srcTableId, changed);
                        }
                    });
                
                type = typeFinal;
            }
            if (type == null)
                return;
        }
        ensureBoolean(type);

        int start = indexMap.filled();
        while (indexMap.filled() <= index && recordSet.indexValid(nextIndexToExamine))
        {
            boolean keep;
            try
            {
                keep = Utility.cast(filterExpression.calculateValue(new EvaluateState(getManager().getTypeManager(), OptionalInt.of(nextIndexToExamine))).value, Boolean.class);
            }
            catch (UserException e)
            {
                // The row has an error, keep it but also record error:
                errorsDuringFilter.put(nextIndexToExamine, e.getLocalizedMessage());
                keep = true;
            }
            if (keep)
                indexMap.add(nextIndexToExamine);
            nextIndexToExamine += 1;

            //if (prog != null)
                //prog.progressUpdate((double)(indexMap.filled() - start) / (double)(index - start));
        }
    }
    
    // Given a row in this table, gets the index of the row in the source table that it came from.  Null if invalid or not yet available
    @SuppressWarnings("units")
    @OnThread(Tag.Simulation)
    public @Nullable @TableDataRowIndex Integer getSourceRowFor(@TableDataRowIndex int rowInThisTable) throws InternalException, UserException
    {
        if (rowInThisTable >=0 && rowInThisTable < indexMap.filled())
        {
            return indexMap.getInt(rowInThisTable);
        }
        return null;
    }

    @OnThread(Tag.Any)
    public static TypeState makeTypeState(TypeManager typeManager) throws InternalException
    {
        return TypeState.withRowNumber(typeManager, FunctionList.getFunctionLookup(typeManager.getUnitManager()));
    }

    @Override
    @OnThread(Tag.Any)
    public Stream<TableId> getPrimarySources()
    {
        return Stream.of(srcTableId);
    }

    @Override
    @OnThread(Tag.Any)
    public Stream<TableId> getSourcesFromExpressions()
    {
        return ExpressionUtil.tablesFromExpression(filterExpression);
    }

    @Override
    protected @OnThread(Tag.Any) String getTransformationName()
    {
        return NAME;
    }

    @Override
    protected @OnThread(Tag.Any) List<String> saveDetail(@Nullable File destination, TableAndColumnRenames renames)
    {
        renames.useColumnsFromTo(srcTableId, getId());
        
        return Collections.singletonList(PREFIX + " " + filterExpression.save(SaveDestination.TO_FILE, BracketedStatus.DONT_NEED_BRACKETS, renames.withDefaultTableId(srcTableId)));
    }

    @Override
    public @OnThread(Tag.Any) RecordSet getData() throws UserException
    {
        if (recordSet == null)
            throw new UserException(error);
        return recordSet;
    }

    @OnThread(Tag.Any)
    public Expression getFilterExpression()
    {
        return filterExpression;
    }

    @OnThread(Tag.Any)
    public TableId getSrcTableId()
    {
        return srcTableId;
    }

    @Override
    public @OnThread(Tag.Simulation) Transformation withNewSource(TableId newSrcTableId) throws InternalException
    {
        return new Filter(getManager(), getDetailsForCopy(getId()), newSrcTableId, filterExpression);
    }

    public static class Info extends SingleSourceTransformationInfo
    {
        public Info()
        {
            super(NAME, "transform.filter", "preview-filter.png", "filter.explanation.short", Arrays.asList("remove", "delete"));
        }

        @Override
        public @OnThread(Tag.Simulation) Transformation loadSingle(TableManager mgr, InitialLoadDetails initialLoadDetails, TableId srcTableId, String detail, ExpressionVersion expressionVersion) throws InternalException, UserException
        {
            return new Filter(mgr, initialLoadDetails, srcTableId, ExpressionUtil.parse(PREFIX, detail, expressionVersion, mgr.getTypeManager(), FunctionList.getFunctionLookup(mgr.getUnitManager())));
        }

        @Override
        @OnThread(Tag.Simulation)
        public Transformation makeWithSource(TableManager mgr, CellPosition destination, Table srcTable) throws InternalException
        {
            return new Filter(mgr, new InitialLoadDetails(null, null, destination, new Pair<>(Display.ALL, ImmutableList.of())), srcTable.getId(), new BooleanLiteral(true));
        }
    }

    @Override
    public boolean transformationEquals(Transformation o)
    {
        Filter filter = (Filter) o;

        if (!srcTableId.equals(filter.srcTableId)) return false;
        return filterExpression.equals(filter.filterExpression);
    }

    @Override
    public int transformationHashCode()
    {
        int result = srcTableId.hashCode();
        result = 31 * result + filterExpression.hashCode();
        return result;
    }

    @Override
    @OnThread(Tag.Any)
    public <T> T visit(TransformationVisitor<T> visitor)
    {
        return visitor.filter(this);
    }

    @Override
    public TableId getSuggestedName()
    {
        return suggestedName(filterExpression);
    }

    public static TableId suggestedName(Expression filterExpression)
    {
        return new TableId(IdentifierUtility.spaceSeparated("Filt", guessFirstColumnReference(filterExpression).orElse("custom")));
    }

    @SuppressWarnings("recorded")
    public static Optional<@ExpressionIdentifier String> guessFirstColumnReference(Expression expression)
    {
        return expression.visit(new ExpressionVisitorStream<@ExpressionIdentifier String>() {
            @Override
            public Stream<@ExpressionIdentifier String> ident(@Recorded IdentExpression self, @Nullable @ExpressionIdentifier String namespace, ImmutableList<@ExpressionIdentifier String> idents, boolean isVariable)
            {
                // Bit of a hacky guess, but we'll assume any non-variable single ident is a column:
                if (idents.size() == 1 && !isVariable && (namespace == null || namespace.equals(IdentExpression.NAMESPACE_COLUMN)))
                    return Stream.<@ExpressionIdentifier String>of(idents.get(0));
                return super.ident(self, namespace, idents, isVariable);
            }
        }).findFirst();
    }
}
