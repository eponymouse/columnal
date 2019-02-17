package records.transformations;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import log.Log;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.*;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import records.errors.ExpressionErrorException;
import records.errors.ExpressionErrorException.EditableExpression;
import records.gui.View;
import records.gui.expressioneditor.ExpressionEditor.ColumnAvailability;
import records.transformations.expression.BracketedStatus;
import records.transformations.expression.BooleanLiteral;
import records.transformations.expression.ColumnReference;
import records.transformations.expression.ColumnReference.ColumnReferenceType;
import records.transformations.expression.ErrorAndTypeRecorderStorer;
import records.transformations.expression.EvaluateState;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.ColumnLookup;
import records.transformations.expression.Expression.MultipleTableLookup;
import records.transformations.expression.TypeState;
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
public class Check extends Transformation
{
    public static final String NAME = "check";
    private static final String PREFIX = "CHECK";
    private final TableId srcTableId;
    private final @Nullable RecordSet recordSet;
    private final String error;
    @OnThread(Tag.Any)
    private final Expression checkExpression;
    private @MonotonicNonNull DataType type;
    
    public Check(TableManager mgr, InitialLoadDetails initialLoadDetails, TableId srcTableId, Expression checkExpression) throws InternalException
    {
        super(mgr, initialLoadDetails);
        this.srcTableId = srcTableId;
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
            @Nullable TypeExp checked = checkExpression.checkExpression(lookup, new TypeState(getManager().getUnitManager(), getManager().getTypeManager()), errors);
            @Nullable DataType typeFinal = null;
            if (checked != null)
                typeFinal = errors.recordLeftError(getManager().getTypeManager(), checkExpression, checked.toConcreteType(getManager().getTypeManager()));

            if (typeFinal == null)
                throw new ExpressionErrorException(errors.getAllErrors().findFirst().orElse(StyledString.s("Unknown type error")), new EditableExpression(checkExpression, null, lookup, DataType.BOOLEAN)
                {
                    @Override
                    @OnThread(Tag.Simulation)
                    public Table replaceExpression(Expression changed) throws InternalException
                    {
                        return new Check(getManager(), getDetailsForCopy(), Check.this.srcTableId, changed);
                    }
                });

            type = typeFinal;
        }
        return checkExpression.getValue(new EvaluateState(getManager().getTypeManager(), OptionalInt.empty())).getFirst();
    }

    @OnThread(Tag.Any)
    public ColumnLookup getColumnLookup()
    {
        return new ColumnLookup()
        {
            @Override
            public @Nullable Pair<TableId, DataTypeValue> getColumn(@Nullable TableId tableId, ColumnId columnId, ColumnReferenceType columnReferenceType)
            {
                try
                {
                    Pair<TableId, Column> column = null;
                    Table srcTable = getManager().getSingleTableOrNull(srcTableId);
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
                        Table table = getManager().getSingleTableOrNull(tableId);
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
                                return new Pair<>(column.getFirst(), column.getSecond().getType());
                            case WHOLE_COLUMN:
                                Column columnFinal = column.getSecond();
                                return new Pair<>(column.getFirst(), DataTypeValue.arrayV(columnFinal.getType(), (i, prog) -> new Pair<>(columnFinal.getLength(), columnFinal.getType())));
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
                return getManager().streamAllTables().flatMap(t -> {
                    try
                    {
                        Stream.Builder<ColumnReference> columns = Stream.builder();
                        if (t.getId().equals(srcTableId))
                        {
                            for (Column column : t.getData().getColumns())
                            {
                                columns.add(new ColumnReference(column.getName(), ColumnReferenceType.WHOLE_COLUMN));
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
    public TableId getSource()
    {
        return srcTableId;
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
        return Collections.singletonList(PREFIX + " " + checkExpression.save(true, BracketedStatus.MISC, renames));
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

    public static class Info extends SingleSourceTransformationInfo
    {
        public Info()
        {
            super(NAME, "transform.check", "preview-check.png", "check.explanation.short", ImmutableList.of("remove", "delete"));
        }
        
        @Override
        protected @OnThread(Tag.Simulation) Transformation loadSingle(TableManager mgr, InitialLoadDetails initialLoadDetails, TableId srcTableId, String detail) throws InternalException, UserException
        {
            return new Check(mgr, initialLoadDetails, srcTableId, Expression.parse(PREFIX, detail, mgr.getTypeManager()));
        }

        @Override
        public @OnThread(Tag.Simulation) Transformation makeWithSource(View view, TableManager mgr, CellPosition destination, Table srcTable) throws InternalException
        {
            return new Check(mgr, new InitialLoadDetails(null, destination, null), srcTable.getId(), new BooleanLiteral(true));
        }
    }
}
