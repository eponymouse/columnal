package records.transformations;

import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.*;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.errors.ExpressionErrorException;
import records.errors.ExpressionErrorException.EditableExpression;
import records.grammar.Versions.ExpressionVersion;
import records.transformations.expression.BooleanLiteral;
import records.transformations.expression.BracketedStatus;
import records.transformations.expression.ErrorAndTypeRecorderStorer;
import records.transformations.expression.EvaluateState;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.ColumnLookup;
import records.transformations.expression.Expression.MultipleTableLookup;
import records.transformations.expression.Expression.SaveDestination;
import records.transformations.expression.ExpressionUtil;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
                            return new Filter(getManager(), getDetailsForCopy(), Filter.this.srcTableId, changed);
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
        return new Filter(getManager(), getDetailsForCopy(), newSrcTableId, filterExpression);
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
    public <T> T visit(TransformationVisitor<T> visitor)
    {
        return visitor.filter(this);
    }
}
