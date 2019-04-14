package records.transformations;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import log.Log;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.*;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.data.datatype.ListExDTV;
import records.data.datatype.TypeManager;
import records.transformations.expression.EvaluateState.TypeLookup;
import records.transformations.expression.Expression.ValueResult;
import records.transformations.expression.explanation.Explanation;
import records.error.InternalException;
import records.error.UserException;
import records.errors.ExpressionErrorException;
import records.errors.ExpressionErrorException.EditableExpression;
import records.grammar.TransformationLexer;
import records.grammar.TransformationParser;
import records.grammar.TransformationParser.CheckContext;
import records.grammar.TransformationParser.CheckTypeContext;
import records.gui.View;
import records.transformations.expression.BracketedStatus;
import records.transformations.expression.BooleanLiteral;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.ErrorAndTypeRecorderStorer;
import records.transformations.expression.EvaluateState;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.ColumnLookup;
import records.transformations.expression.TypeState;
import records.transformations.function.FunctionList;
import records.typeExp.TypeExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.SimulationFunction;
import utility.Utility;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Stream;

@OnThread(Tag.Simulation)
public class Check extends Transformation implements SingleSourceTransformation
{
    public static final String NAME = "check";
    private static final String PREFIX = "CHECK";

    public static enum CheckType
    {
        STANDALONE, ALL_ROWS, ANY_ROW, NO_ROWS;
    }

    private final TableId srcTableId;
    private final @Nullable RecordSet recordSet;
    private final String error;
    @OnThread(Tag.Any)
    private final CheckType checkType;
    @OnThread(Tag.Any)
    private final Expression checkExpression;
    private @MonotonicNonNull Pair<TypeLookup, DataType> type;
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
            theRecordSet = new KnownLengthRecordSet(
                    ImmutableList.<SimulationFunction<RecordSet, Column>>of(rs -> DataType.BOOLEAN.makeCalculatedColumn(rs, new ColumnId("result"), n -> Utility.later(this).getResult()))
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
    private @Value Object getResult() throws InternalException, UserException
    {
        if (type == null)
        {
            ErrorAndTypeRecorderStorer errors = new ErrorAndTypeRecorderStorer();
            ColumnLookup lookup = getColumnLookup();
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
                        return new Check(getManager(), getDetailsForCopy(), Check.this.srcTableId, checkType, changed);
                    }
                });

            type = new Pair<>(errors, typeFinal);
        }
        if (checkType == CheckType.STANDALONE)
        {
            ValueResult r = checkExpression.calculateValue(new EvaluateState(getManager().getTypeManager(), OptionalInt.empty(), true, type.getFirst()));
            explanation = r.makeExplanation(null);
            return r.value;
        }
        else
        {
            Table srcTable = getManager().getSingleTableOrNull(srcTableId);
            if (srcTable != null)
            {
                int length = srcTable.getData().getLength();
                for (int row = 0; row < length; row++)
                {
                    ValueResult r = checkExpression.calculateValue(new EvaluateState(getManager().getTypeManager(), OptionalInt.of(row), true, type.getFirst()));
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

    @OnThread(Tag.Any)
    public ColumnLookup getColumnLookup()
    {
        return getColumnLookup(getManager(), srcTableId, checkType);
    }

    @OnThread(Tag.Any)
    public static ColumnLookup getColumnLookup(TableManager tableManager, TableId srcTableId, CheckType checkType)
    {
        return new ColumnLookup()
        {
            @Override
            public @Nullable Pair<TableId, DataTypeValue> getColumn(@Nullable TableId tableId, ColumnId columnId, ColumnReferenceType columnReferenceType)
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
                        switch (columnReferenceType)
                        {
                            case CORRESPONDING_ROW:
                                if (checkType == CheckType.STANDALONE)
                                    return null;
                                else
                                    return new Pair<>(column.getFirst(), column.getSecond().getType());
                            case WHOLE_COLUMN:
                                Column columnFinal = column.getSecond();
                                return new Pair<>(column.getFirst(), DataTypeValue.array(columnFinal.getType().getType(), (i, prog) -> DataTypeUtility.value(new ListExDTV(columnFinal))));
                            default:
                                throw new InternalException("Unknown reference type: " + columnReferenceType);
                        }
                    }
                }
                catch (InternalException | UserException e)
                {
                    Log.log(e);
                }
                return null;
            }

            @Override
            public Stream<ColumnReference> getAvailableColumnReferences()
            {
                return tableManager.streamAllTables().flatMap(t -> {
                    try
                    {
                        Stream.Builder<ColumnReference> columns = Stream.builder();
                        if (t.getId().equals(srcTableId))
                        {
                            for (Column column : t.getData().getColumns())
                            {
                                columns.add(new ColumnReference(column.getName(), ColumnReferenceType.WHOLE_COLUMN));
                                if (checkType != CheckType.STANDALONE)
                                    columns.add(new ColumnReference(column.getName(), ColumnReferenceType.CORRESPONDING_ROW));
                            }
                        }

                        for (Column column : t.getData().getColumns())
                        {
                            columns.add(new ColumnReference(t.getId(), column.getName(), ColumnReferenceType.WHOLE_COLUMN));
                        }
                        return columns.build();
                    }
                    catch (InternalException | UserException e)
                    {
                        Log.log(e);
                        return Stream.of();
                    }
                });
            }

            @Override
            public Stream<ColumnReference> getPossibleColumnReferences(TableId tableId, ColumnId columnId)
            {
                return getAvailableColumnReferences().filter(c -> tableId.equals(c.getTableId()) && columnId.equals(c.getColumnId()));
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
        return TransformationUtil.tablesFromExpression(checkExpression);
    }

    @OnThread(Tag.Any)
    public TableId getSrcTableId()
    {
        return srcTableId;
    }

    @Override
    public @OnThread(Tag.Simulation) Transformation withNewSource(TableId newSrcTableId) throws InternalException
    {
        return new Check(getManager(), getDetailsForCopy(), newSrcTableId, checkType, checkExpression);
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
        return Collections.singletonList(PREFIX + " " + checkTypeStr + " @EXPRESSION " + checkExpression.save(true, BracketedStatus.MISC, renames));
    }
    
    @OnThread(Tag.Any)
    public CheckType getCheckType()
    {
        return checkType;
    }

    // Only valid after fetching the result.
    public @Nullable Explanation getExplanation() throws InternalException
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
        return selectedItem == CheckType.STANDALONE ? new TypeState(typeManager) : TypeState.withRowNumber(typeManager);
    }

    public static class Info extends SingleSourceTransformationInfo
    {
        public Info()
        {
            super(NAME, "transform.check", "preview-check.png", "check.explanation.short", ImmutableList.of("remove", "delete"));
        }
        
        @Override
        protected @OnThread(Tag.Simulation) Transformation loadSingle(TableManager mgr, InitialLoadDetails initialLoadDetails, TableId srcTableId, String detail) throws InternalException, UserException
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
            
            return new Check(mgr, initialLoadDetails, srcTableId, checkType, Expression.parse(null, loaded.expression().EXPRESSION().getText(), mgr.getTypeManager(), FunctionList.getFunctionLookup(mgr.getUnitManager())));
        }

        @Override
        public @OnThread(Tag.Simulation) Transformation makeWithSource(View view, TableManager mgr, CellPosition destination, Table srcTable) throws InternalException
        {
            return new Check(mgr, new InitialLoadDetails(null, destination, null), srcTable.getId(), CheckType.STANDALONE, new BooleanLiteral(true));
        }
    }
}
