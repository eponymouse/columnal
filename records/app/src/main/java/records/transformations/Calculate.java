package records.transformations;

import annotation.recorded.qual.Recorded;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.*;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.TransformationLexer;
import records.grammar.TransformationParser;
import records.grammar.TransformationParser.TransformContext;
import records.grammar.TransformationParser.TransformItemContext;
import records.gui.View;
import records.transformations.expression.BracketedStatus;
import records.loadsave.OutputBuilder;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.ErrorAndTypeRecorderStorer;
import records.transformations.expression.EvaluateState;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.ColumnLookup;
import records.transformations.expression.Expression.MultipleTableLookup;
import records.transformations.expression.QuickFix;
import records.transformations.expression.TypeState;
import records.transformations.function.FunctionList;
import records.typeExp.TypeExp;
import styled.StyledShowable;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.SimulationFunction;
import utility.Utility;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A transformation on a single table which calculates a new set of columns
 * (adding to/replacing the existing columns depending on name)
 * by evaluating an expression for each.
 */
@OnThread(Tag.Simulation)
public class Calculate extends Transformation
{
    // If any columns overlap the source table's columns, they are shown in that position.
    // If they are new, they are shown at the end, in the order provided by this list
    // (Note that Guava's ImmutableMap respects insertion order for iteration, which
    // we rely on here).
    @OnThread(Tag.Any)
    private final ImmutableMap<ColumnId, Expression> newColumns;
    private final TableId srcTableId;
    private final @Nullable Table src;
    private final @Nullable RecordSet recordSet;
    @OnThread(Tag.Any)
    private StyledString error = StyledString.s("");

    public Calculate(TableManager mgr, InitialLoadDetails initialLoadDetails, TableId srcTableId, ImmutableMap<ColumnId, Expression> toCalculate) throws InternalException
    {
        super(mgr, initialLoadDetails);
        this.srcTableId = srcTableId;
        this.src = mgr.getSingleTableOrNull(srcTableId);
        this.error = StyledString.s("Unknown error with table \"" + getId() + "\"");
        this.newColumns = toCalculate;
        if (this.src == null)
        {
            this.recordSet = null;
            error = StyledString.s("Could not find source table: \"" + srcTableId + "\"");
            return;
        }


        @Nullable RecordSet theResult = null;
        try
        {
            RecordSet srcRecordSet = this.src.getData();
            ColumnLookup columnLookup = new MultipleTableLookup(getId(), mgr, src.getId());
            List<SimulationFunction<RecordSet, Column>> columns = new ArrayList<>();
            HashMap<ColumnId, Expression> stillToAdd = new HashMap<>(newColumns);
            for (Column c : srcRecordSet.getColumns())
            {
                // If the old column is not overwritten by one of the same name, include it:
                Expression overwrite = stillToAdd.remove(c.getName());
                
                if (overwrite == null)
                {
                    columns.add(rs -> new Column(rs, c.getName())
                    {
                        @Override
                        public @OnThread(Tag.Any) DataTypeValue getType() throws InternalException, UserException
                        {
                            return c.getType();
                        }

                        @Override
                        public boolean isAltered()
                        {
                            return false;
                        }
                    });
                }
                else
                {
                    columns.add(makeCalcColumn(mgr, columnLookup, c.getName(), overwrite));
                }
            }

            for (Entry<ColumnId, Expression> newCol : stillToAdd.entrySet())
            {
                columns.add(makeCalcColumn(mgr, columnLookup, newCol.getKey(), newCol.getValue()));
            }

            theResult = new RecordSet(columns)
            {
                @Override
                public boolean indexValid(int index) throws UserException, InternalException
                {
                    return srcRecordSet.indexValid(index);
                }

                @Override
                public @TableDataRowIndex int getLength() throws UserException, InternalException
                {
                    return srcRecordSet.getLength();
                }
            };
        }
        catch (UserException e)
        {
            this.error = e.getStyledMessage();
        }

        recordSet = theResult;
    }

    private SimulationFunction<RecordSet, Column> makeCalcColumn(@UnknownInitialization(Object.class) Calculate this,
                                                                 TableManager mgr, ColumnLookup columnLookup, ColumnId columnId, Expression expression) throws InternalException
    {
        try
        {
            ErrorAndTypeRecorderStorer errorAndTypeRecorder = new ErrorAndTypeRecorderStorer();
            @Nullable TypeExp type = expression.checkExpression(columnLookup, new TypeState(mgr.getUnitManager(), mgr.getTypeManager()), errorAndTypeRecorder);

            DataType concrete = type == null ? null : errorAndTypeRecorder.recordLeftError(mgr.getTypeManager(), FunctionList.getFunctionLookup(mgr.getUnitManager()), expression, type.toConcreteType(mgr.getTypeManager()));
            if (type == null || concrete == null)
                throw new UserException(StyledString.concat(StyledString.s("Error in " + columnId.getRaw() + " expression: "), error == null ? StyledString.s("") : error)); // A bit redundant, but control flow will pan out right
            @NonNull DataType typeFinal = concrete;
            return rs -> typeFinal.makeCalculatedColumn(rs, columnId, index -> expression.calculateValue(new EvaluateState(mgr.getTypeManager(), OptionalInt.of(index), errorAndTypeRecorder)).value);
        }
        catch (UserException e)
        {
            return rs -> new ErrorColumn(rs, mgr.getTypeManager(), columnId, e.getStyledMessage());
        }
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
        return TransformationUtil.tablesFromExpressions(newColumns.values().stream());
    }

    @Override
    protected @OnThread(Tag.Any) String getTransformationName()
    {
        return "calculate";
    }

    @Override
    protected @OnThread(Tag.Any) List<String> saveDetail(@Nullable File destination, TableAndColumnRenames renames)
    {
        return newColumns.entrySet().stream().map(entry -> {
            OutputBuilder b = new OutputBuilder();
            b.kw("CALCULATE").id(renames.columnId(getId(), entry.getKey()).getSecond());
            b.kw("@EXPRESSION");
            b.raw(entry.getValue().save(true, BracketedStatus.MISC, renames.withDefaultTableId(srcTableId)));
            return b.toString();
        }).collect(Collectors.<String>toList());
    }

    @Override
    public @OnThread(Tag.Any) RecordSet getData() throws UserException, InternalException
    {
        if (recordSet == null)
            throw new UserException(error == null ? StyledString.s("Unknown error") : error);
        return recordSet;
    }

    @Override
    public @OnThread(Tag.Any) TableOperations getOperations()
    {
        // Renames and deletes are valid, if they refer to
        // columns derived from us.
        // TODO allow renames backwards through dependencies
        return new TableOperations(getManager().getRenameTableOperation(this), deleteId -> newColumns.containsKey(deleteId) ? this::deleteColumn : null, null, null, null);
    }

    @OnThread(Tag.Any)
    public TableId getSource()
    {
        return srcTableId;
    }

    private void deleteColumn(ColumnId columnId)
    {
        //TODO
    }

    @Override
    public boolean transformationEquals(Transformation o)
    {
        Calculate calculate = (Calculate) o;

        if (!newColumns.equals(calculate.newColumns)) return false;
        return srcTableId.equals(calculate.srcTableId);
    }

    @Override
    public int transformationHashCode()
    {
        int result = newColumns.hashCode();
        result = 31 * result + srcTableId.hashCode();
        return result;
    }

    @OnThread(Tag.Any)
    public ImmutableMap<ColumnId, Expression> getCalculatedColumns()
    {
        return newColumns;
    }

    public static class Info extends SingleSourceTransformationInfo
    {
        public Info()
        {
            super("calculate", "transform.calculate", "preview-calculate.png", "calculate.explanation.short", Arrays.asList("transform"));
        }

        @Override
        public @OnThread(Tag.Simulation) Transformation loadSingle(TableManager mgr, InitialLoadDetails initialLoadDetails, TableId srcTableId, String detail) throws InternalException, UserException
        {
            ImmutableMap.Builder<ColumnId, Expression> columns = ImmutableMap.builder();

            TransformContext transform = Utility.parseAsOne(detail, TransformationLexer::new, TransformationParser::new, p -> p.transform());
            for (TransformItemContext transformItemContext : transform.transformItem())
            {
                columns.put(new ColumnId(transformItemContext.column.getText()), Expression.parse(null, transformItemContext.expression().EXPRESSION().getText(), mgr.getTypeManager(), FunctionList.getFunctionLookup(mgr.getUnitManager())));
            }

            return new Calculate(mgr, initialLoadDetails, srcTableId, columns.build());
        }
        
        @Override
        protected @OnThread(Tag.Simulation) Transformation makeWithSource(View view, TableManager mgr, CellPosition destination, Table srcTable) throws InternalException
        {
            return new Calculate(view.getManager(), new InitialLoadDetails(null, destination, null), srcTable.getId(), ImmutableMap.of());
        }
    }
}
