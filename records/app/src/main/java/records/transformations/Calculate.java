package records.transformations;

import annotation.recorded.qual.Recorded;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import records.data.Column;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.Table;
import records.data.TableAndColumnRenames;
import records.data.TableId;
import records.data.TableManager;
import records.data.TableOperations;
import records.data.Transformation;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.TransformationLexer;
import records.grammar.TransformationParser;
import records.grammar.TransformationParser.TransformContext;
import records.grammar.TransformationParser.TransformItemContext;
import records.gui.View;
import records.gui.expressioneditor.ConsecutiveBase.BracketedStatus;
import records.loadsave.OutputBuilder;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.EvaluateState;
import records.transformations.expression.Expression;
import records.transformations.expression.Expression.MultipleTableLookup;
import records.transformations.expression.Expression.TableLookup;
import records.transformations.expression.TypeState;
import records.typeExp.TypeExp;
import styled.StyledShowable;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.SimulationFunction;
import utility.Utility;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
    @OnThread(Tag.Any)
    private final ImmutableList<Pair<ColumnId, Expression>> newColumns;
    private final TableId srcTableId;
    private final @Nullable Table src;
    private final @Nullable RecordSet recordSet;
    @OnThread(Tag.Any)
    private StyledString error = StyledString.s("");

    public Calculate(TableManager mgr, InitialLoadDetails initialLoadDetails, TableId srcTableId, ImmutableList<Pair<ColumnId, Expression>> toCalculate) throws InternalException
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
            TableLookup tableLookup = new MultipleTableLookup(mgr, src);
            List<SimulationFunction<RecordSet, Column>> columns = new ArrayList<>();
            for (Column c : srcRecordSet.getColumns())
            {
                // If the old column is not overwritten by one of the same name, include it:
                if (!newColumns.stream().anyMatch(n -> n.getFirst().equals(c.getName())))
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
            }

            for (Pair<ColumnId, Expression> newCol : toCalculate)
            {
                ErrorAndTypeRecorder errorAndTypeRecorder = new ErrorAndTypeRecorder()
                {
                    @Override
                    public <E> void recordError(E src, StyledString s)
                    {
                        error = s;
                    }

                    @Override
                    public <EXPRESSION extends StyledShowable, SEMANTIC_PARENT> void recordQuickFixes(EXPRESSION src, List<QuickFix<EXPRESSION, SEMANTIC_PARENT>> quickFixes)
                    {
                        
                    }

                    @SuppressWarnings("recorded")
                    @Override
                    public @Recorded @NonNull TypeExp recordTypeNN(Expression expression, @NonNull TypeExp typeExp)
                    {
                        return typeExp;
                    }
                };
                @Nullable TypeExp type = newCol.getSecond().check(tableLookup, new TypeState(mgr.getUnitManager(), mgr.getTypeManager()), errorAndTypeRecorder);
                
                DataType concrete = type == null ? null : errorAndTypeRecorder.recordLeftError(mgr.getUnitManager(), newCol.getSecond(), type.toConcreteType(mgr.getTypeManager()));
                if (type == null || concrete == null)
                    throw new UserException(error); // A bit redundant, but control flow will pan out right
                @NonNull DataType typeFinal = concrete;
                columns.add(rs -> typeFinal.makeCalculatedColumn(rs, newCol.getFirst(), index -> newCol.getSecond().getValue(new EvaluateState(mgr.getTypeManager(), OptionalInt.of(index)))));
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
        return TransformationUtil.tablesFromExpressions(newColumns.stream().map(p -> p.getSecond()));
    }

    @Override
    protected @OnThread(Tag.Any) String getTransformationName()
    {
        return "calculate";
    }

    @Override
    protected @OnThread(Tag.Any) List<String> saveDetail(@Nullable File destination, TableAndColumnRenames renames)
    {
        return newColumns.stream().map(entry -> {
            OutputBuilder b = new OutputBuilder();
            b.kw("CALCULATE").id(renames.columnId(getId(), entry.getFirst()));
            b.kw("@EXPRESSION");
            b.raw(entry.getSecond().save(BracketedStatus.MISC, renames.withDefaultTableId(srcTableId)));
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
        return new TableOperations(getManager().getRenameTableOperation(this), deleteId -> newColumns.stream().anyMatch(p -> p.getFirst().equals(deleteId)) ? this::deleteColumn : null, null, null, null);
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
    public List<Pair<ColumnId, Expression>> getCalculatedColumns()
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
            ImmutableList.Builder<Pair<ColumnId, Expression>> columns = ImmutableList.builder();

            TransformContext transform = Utility.parseAsOne(detail, TransformationLexer::new, TransformationParser::new, p -> p.transform());
            for (TransformItemContext transformItemContext : transform.transformItem())
            {
                columns.add(new Pair<>(new ColumnId(transformItemContext.column.getText()), Expression.parse(null, transformItemContext.expression().EXPRESSION().getText(), mgr.getTypeManager())));
            }

            return new Calculate(mgr, initialLoadDetails, srcTableId, columns.build());
        }
        
        @Override
        protected @OnThread(Tag.Simulation) Transformation makeWithSource(View view, TableManager mgr, CellPosition destination, Table srcTable) throws InternalException
        {
            return new Calculate(view.getManager(), new InitialLoadDetails(null, destination, null), srcTable.getId(), ImmutableList.of());
        }
    }
}
