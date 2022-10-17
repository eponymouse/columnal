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

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.Column;
import xyz.columnal.data.ColumnUtility;
import xyz.columnal.data.KnownLengthRecordSet;
import xyz.columnal.data.RecordSet;
import xyz.columnal.data.SingleSourceTransformation;
import xyz.columnal.data.Table;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.Transformation;
import xyz.columnal.id.ColumnId;
import xyz.columnal.id.TableAndColumnRenames;
import xyz.columnal.id.TableId;
import xyz.columnal.log.Log;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.error.expressions.ExpressionErrorException;
import xyz.columnal.error.expressions.ExpressionErrorException.EditableExpression;
import xyz.columnal.grammar.TransformationLexer;
import xyz.columnal.grammar.TransformationParser;
import xyz.columnal.grammar.TransformationParser.CheckContext;
import xyz.columnal.grammar.TransformationParser.CheckTypeContext;
import xyz.columnal.grammar.Versions.ExpressionVersion;
import xyz.columnal.transformations.expression.BooleanLiteral;
import xyz.columnal.transformations.expression.BracketedStatus;
import xyz.columnal.transformations.expression.ErrorAndTypeRecorderStorer;
import xyz.columnal.transformations.expression.EvaluateState;
import xyz.columnal.transformations.expression.EvaluationException;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.Expression.ColumnLookup;
import xyz.columnal.transformations.expression.Expression.SaveDestination;
import xyz.columnal.transformations.expression.Expression.ValueResult;
import xyz.columnal.transformations.expression.ExpressionUtil;
import xyz.columnal.transformations.expression.IdentExpression;
import xyz.columnal.transformations.expression.TypeState;
import xyz.columnal.transformations.expression.explanation.Explanation;
import xyz.columnal.transformations.function.FunctionList;
import xyz.columnal.typeExp.TypeExp;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.function.simulation.SimulationFunction;
import xyz.columnal.utility.TranslationUtility;
import xyz.columnal.utility.Utility;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.stream.Stream;

@OnThread(Tag.Simulation)
public class Check extends VisitableTransformation implements SingleSourceTransformation
{
    public static final String NAME = "check";
    private static final String PREFIX = "CHECK";

    public static enum CheckType
    {
        ALL_ROWS, ANY_ROW, NO_ROWS, STANDALONE;
        
        // For display:
        @Override
        public String toString()
        {
            switch (this)
            {
                case STANDALONE:
                    return TranslationUtility.getString("edit.check.standalone");
                case ALL_ROWS:
                    return TranslationUtility.getString("edit.check.allrows");
                case ANY_ROW:
                    return TranslationUtility.getString("edit.check.anyrow");
                case NO_ROWS:
                    return TranslationUtility.getString("edit.check.norows");
            }
            return "";
        }
    }

    private final TableId srcTableId;
    private final @Nullable RecordSet recordSet;
    private final String error;
    @OnThread(Tag.Any)
    private final CheckType checkType;
    @OnThread(Tag.Any)
    private final Expression checkExpression;
    private @MonotonicNonNull DataType type;
    private @MonotonicNonNull Explanation explanation;
    
    public Check(TableManager mgr, InitialLoadDetails initialLoadDetails, TableId srcTableId, CheckType checkType, Expression checkExpression) throws InternalException
    {
        super(mgr, initialLoadDetails);
        this.srcTableId = srcTableId;
        this.checkType = checkType;
        this.checkExpression = checkExpression;
        RecordSet theRecordSet = null;
        String theError = "Unknown error";
        try
        {
            ColumnId result = new ColumnId("result");
            theRecordSet = new KnownLengthRecordSet(
                    ImmutableList.<SimulationFunction<RecordSet, Column>>of(rs -> ColumnUtility.makeCalculatedColumn(DataType.BOOLEAN, rs, result, n -> Utility.later(this).getResult(), t -> addManualEditSet(result, t)))
                    , 1
            );
        }
        catch (UserException e)
        {
            theError = e.getLocalizedMessage();
        }
        this.recordSet = theRecordSet;
        this.error = theError;
    }

    @OnThread(Tag.Simulation)
    public @Value Boolean getResult() throws InternalException, UserException
    {
        if (type == null)
        {
            ErrorAndTypeRecorderStorer errors = new ErrorAndTypeRecorderStorer();
            ColumnLookup lookup = getColumnLookup();
            @SuppressWarnings("recorded")
            @Nullable TypeExp checked = checkExpression.checkExpression(lookup, makeTypeState(getManager().getTypeManager(), checkType), errors);
            @Nullable DataType typeFinal = null;
            if (checked != null)
                typeFinal = errors.recordLeftError(getManager().getTypeManager(), FunctionList.getFunctionLookup(getManager().getUnitManager()), checkExpression, checked.toConcreteType(getManager().getTypeManager()));

            if (typeFinal == null)
                throw new ExpressionErrorException(errors.getAllErrors().findFirst().orElse(StyledString.s("Unknown type error")), new EditableExpression(checkExpression, null, lookup, () -> makeTypeState(getManager().getTypeManager(), checkType), DataType.BOOLEAN)
                {
                    @Override
                    @OnThread(Tag.Simulation)
                    public Table replaceExpression(Expression changed) throws InternalException
                    {
                        return new Check(getManager(), getDetailsForCopy(getId()), Check.this.srcTableId, checkType, changed);
                    }
                });

            type = typeFinal;
        }
        
        ensureBoolean(type);
        
        try
        {
            if (checkType == CheckType.STANDALONE)
            {
                ValueResult r = checkExpression.calculateValue(new EvaluateState(getManager().getTypeManager(), OptionalInt.empty(), true));
                explanation = r.makeExplanation(null);
                return Utility.cast(r.value, Boolean.class);
            }
            else
            {
                Table srcTable = getManager().getSingleTableOrNull(srcTableId);
                if (srcTable != null)
                {
                    int length = srcTable.getData().getLength();
                    for (int row = 0; row < length; row++)
                    {
                        ValueResult r = checkExpression.calculateValue(new EvaluateState(getManager().getTypeManager(), OptionalInt.of(row), true));
                        boolean thisRow = Utility.cast(r.value, Boolean.class);
                        if (thisRow && checkType == CheckType.NO_ROWS)
                        {
                            explanation = r.makeExplanation(null);
                            return DataTypeUtility.value(false);
                        }
                        else if (!thisRow && checkType == CheckType.ALL_ROWS)
                        {
                            explanation = r.makeExplanation(null);
                            return DataTypeUtility.value(false);
                        }
                        else if (thisRow && checkType == CheckType.ANY_ROW)
                        {
                            explanation = r.makeExplanation(null);
                            return DataTypeUtility.value(true);
                        }
                    }
                    if (checkType == CheckType.ANY_ROW)
                        return DataTypeUtility.value(false);
                    else
                        return DataTypeUtility.value(true);
                }

                throw new UserException("Cannot find table: " + srcTableId);
            }
        }
        catch (EvaluationException e)
        {
            explanation = e.makeExplanation();
            throw e;
        }
    }

    @OnThread(Tag.Any)
    public ColumnLookup getColumnLookup()
    {
        return getColumnLookup(getManager(), srcTableId, getId(), checkType);
    }

    @OnThread(Tag.Any)
    public static ColumnLookup getColumnLookup(TableManager tableManager, TableId srcTableId, @Nullable TableId us, CheckType checkType)
    {
        return new ColumnLookup()
        {
            @Override
            public @Nullable FoundColumn getColumn(Expression expression, @Nullable TableId tableId, ColumnId columnId)
            {
                try
                {
                    Pair<TableId, Column> column = null;
                    Table srcTable = tableManager.getSingleTableOrNull(srcTableId);
                    if (tableId == null)
                    {
                        if (srcTable != null)
                        {
                            Column col = srcTable.getData().getColumnOrNull(columnId);
                            column = col == null ? null : new Pair<>(srcTable.getId(), col);
                        }
                    }
                    else
                    {
                        Table table = tableManager.getSingleTableOrNull(tableId);
                        if (table != null)
                        {
                            Column col = table.getData().getColumnOrNull(columnId);
                            column = col == null ? null : new Pair<>(table.getId(), col);
                        }
                    }
                    if (column == null)
                    {
                        return null;
                    }
                    else
                    {
                
                        if (checkType == CheckType.STANDALONE)
                            return null;
                        else
                            return new FoundColumn(column.getFirst(), srcTableId.equals(tableId), column.getSecond().getType(), null);
                    }
                }
                catch (InternalException | UserException e)
                {
                    Log.log(e);
                }
                return null;
            }

            @Override
            public Stream<Pair<@Nullable TableId, ColumnId>> getAvailableColumnReferences()
            {
                return tableManager.getAllTables().stream().<Pair<@Nullable TableId, ColumnId>>flatMap(new Function<Table, Stream<Pair<@Nullable TableId, ColumnId>>>()
                {
                    @Override
                    public Stream<Pair<@Nullable TableId, ColumnId>> apply(Table t)
                    {
                        try
                        {
                            Stream.Builder<Pair<@Nullable TableId, ColumnId>> columns = Stream.builder();
                            if (t.getId().equals(srcTableId))
                            {
                                for (Column column : t.getData().getColumns())
                                {
                                    if (checkType != CheckType.STANDALONE)
                                        columns.add(new Pair<>(null, column.getName()));
                                }
                            }
                            return columns.build();
                        }
                        catch (InternalException | UserException e)
                        {
                            Log.log(e);
                            return Stream.<Pair<@Nullable TableId, ColumnId>>of();
                        }
                    }
                });
            }

            @Override
            public Stream<ClickedReference> getPossibleColumnReferences(TableId tableId, ColumnId columnId)
            {
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

            @Override
            public @Nullable FoundTable getTable(@Nullable TableId tableName) throws UserException, InternalException
            {
                Table t;
                if (tableName == null)
                {
                    if (us == null)
                        t = null;
                    else
                        t = tableManager.getSingleTableOrNull(us);
                }
                else
                    t = tableManager.getSingleTableOrNull(tableName);
                return Utility.onNullable(t, FoundTableActual::new);
            }
        };
    }

    @Override
    public @OnThread(Tag.Any) RecordSet getData() throws UserException
    {
        if (recordSet == null)
            throw new UserException(error);
        return recordSet;
    }

    @Override
    protected @OnThread(Tag.Any) Stream<TableId> getPrimarySources()
    {
        return Stream.of(srcTableId);
    }

    @Override
    @OnThread(Tag.Any)
    public Stream<TableId> getSourcesFromExpressions()
    {
        return ExpressionUtil.tablesFromExpression(checkExpression);
    }

    @OnThread(Tag.Any)
    public TableId getSrcTableId()
    {
        return srcTableId;
    }

    @Override
    public @OnThread(Tag.Simulation) Transformation withNewSource(TableId newSrcTableId) throws InternalException
    {
        return new Check(getManager(), getDetailsForCopy(getId()), newSrcTableId, checkType, checkExpression);
    }

    @OnThread(Tag.Any)
    public Expression getCheckExpression()
    {
        return checkExpression;
    }

    @Override
    @OnThread(Tag.Any)
    protected String getTransformationName()
    {
        return "check";
    }

    @Override
    @OnThread(Tag.Any)
    protected List<String> saveDetail(@Nullable File destination, TableAndColumnRenames renames)
    {
        final String checkTypeStr;
        switch (checkType)
        {
            case ALL_ROWS:
                checkTypeStr = "ALLROWS";
                break;
            case ANY_ROW:
                checkTypeStr = "ANYROWS";
                break;
            case NO_ROWS:
                checkTypeStr = "NOROWS";
                break;
            case STANDALONE:
            default: // To satisfy compiler
                checkTypeStr = "STANDALONE";
                break;
        }
        return Collections.singletonList(PREFIX + " " + checkTypeStr + " @EXPRESSION " + checkExpression.save(SaveDestination.TO_FILE, BracketedStatus.DONT_NEED_BRACKETS, renames.withDefaultTableId(srcTableId)));
    }
    
    @OnThread(Tag.Any)
    public CheckType getCheckType()
    {
        return checkType;
    }

    // Only valid after fetching the result.
    public @Nullable Explanation getExplanation()
    {
        return explanation;
    }

    @Override
    protected int transformationHashCode()
    {
        return checkExpression.hashCode();
    }

    @Override
    protected boolean transformationEquals(Transformation obj)
    {
        if (obj instanceof Check)
            return checkExpression.equals(((Check)obj).checkExpression);
        return false;
    }

    @OnThread(Tag.Any)
    public static TypeState makeTypeState(TypeManager typeManager, @Nullable CheckType selectedItem) throws InternalException
    {
        return selectedItem == CheckType.STANDALONE ? new TypeState(typeManager, FunctionList.getFunctionLookup(typeManager.getUnitManager())) : TypeState.withRowNumber(typeManager, FunctionList.getFunctionLookup(typeManager.getUnitManager()));
    }

    public static class Info extends SingleSourceTransformationInfo
    {
        public Info()
        {
            super(NAME, "transform.check", "preview-check.png", "check.explanation.short", ImmutableList.of("remove", "delete"));
        }
        
        @Override
        protected @OnThread(Tag.Simulation) Transformation loadSingle(TableManager mgr, InitialLoadDetails initialLoadDetails, TableId srcTableId, String detail, ExpressionVersion expressionVersion) throws InternalException, UserException
        {
            CheckContext loaded = Utility.parseAsOne(detail, TransformationLexer::new, TransformationParser::new, TransformationParser::check);

            CheckTypeContext checkTypeContext = loaded.checkType();
            CheckType checkType;
            if (checkTypeContext.checkAllRows() != null)
                checkType = CheckType.ALL_ROWS;
            else if (checkTypeContext.checkAnyRows() != null)
                checkType = CheckType.ANY_ROW;
            else if (checkTypeContext.checkNoRows() != null)
                checkType = CheckType.NO_ROWS;
            else
                checkType = CheckType.STANDALONE;
            
            return new Check(mgr, initialLoadDetails, srcTableId, checkType, ExpressionUtil.parse(null, loaded.expression().EXPRESSION().getText(), expressionVersion, mgr.getTypeManager(), FunctionList.getFunctionLookup(mgr.getUnitManager())));
        }

        @Override
        public @OnThread(Tag.Simulation) Transformation makeWithSource(TableManager mgr, CellPosition destination, Table srcTable) throws InternalException
        {
            return new Check(mgr, new InitialLoadDetails(null, null, destination, null), srcTable.getId(), CheckType.STANDALONE, new BooleanLiteral(true));
        }
    }

    @Override
    @OnThread(Tag.Any)
    public <T> T visit(TransformationVisitor<T> visitor)
    {
        return visitor.check(this);
    }

    @Override
    public TableId getSuggestedName()
    {
        return suggestedName(checkExpression);
    }

    public static TableId suggestedName(Expression checkExpression)
    {
        return new TableId(IdentifierUtility.spaceSeparated("Chk", Filter.guessFirstColumnReference(checkExpression).orElse("custom")));
    }

}
