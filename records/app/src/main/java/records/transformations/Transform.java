package records.transformations;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import javafx.beans.binding.ObjectExpression;
import javafx.scene.layout.Pane;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableAndColumnRenames;
import records.data.Table;
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
import records.gui.SingleSourceControl;
import records.gui.View;
import records.gui.expressioneditor.ConsecutiveBase.BracketedStatus;
import records.loadsave.OutputBuilder;
import records.transformations.expression.ErrorAndTypeRecorder;
import records.transformations.expression.EvaluateState;
import records.transformations.expression.Expression;
import records.transformations.expression.NumericLiteral;
import records.transformations.expression.TypeState;
import records.types.TypeExp;
import styled.StyledShowable;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExFunction;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.SimulationSupplier;
import utility.Utility;
import utility.gui.TranslationUtility;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A transformation on a single table which calculates a new set of columns
 * (adding to/replacing the existing columns depending on name)
 * by evaluating an expression for each.
 */
@OnThread(Tag.Simulation)
public class Transform extends Transformation
{
    @OnThread(Tag.Any)
    private final ImmutableList<Pair<ColumnId, Expression>> newColumns;
    private final TableId srcTableId;
    private final @Nullable Table src;
    private final @Nullable RecordSet recordSet;
    @OnThread(Tag.Any)
    private StyledString error = StyledString.s("");

    public Transform(TableManager mgr, InitialLoadDetails initialLoadDetails, TableId srcTableId, ImmutableList<Pair<ColumnId, Expression>> toCalculate) throws InternalException
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
            List<ExFunction<RecordSet, Column>> columns = new ArrayList<>();
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
                    public <EXPRESSION extends StyledShowable> void recordQuickFixes(EXPRESSION src, List<QuickFix<EXPRESSION>> quickFixes)
                    {
                        
                    }

                    @SuppressWarnings("recorded")
                    @Override
                    public @Recorded @NonNull TypeExp recordTypeNN(Expression expression, @NonNull TypeExp typeExp)
                    {
                        return typeExp;
                    }
                };
                @Nullable TypeExp type = newCol.getSecond().check(srcRecordSet, new TypeState(mgr.getUnitManager(), mgr.getTypeManager()), errorAndTypeRecorder);
                
                DataType concrete = type == null ? null : errorAndTypeRecorder.recordLeftError(newCol.getSecond(), type.toConcreteType(mgr.getTypeManager()));
                if (type == null || concrete == null)
                    throw new UserException(error); // A bit redundant, but control flow will pan out right
                @NonNull DataType typeFinal = concrete;
                columns.add(rs -> typeFinal.makeCalculatedColumn(rs, newCol.getFirst(), index -> newCol.getSecond().getValue(index, new EvaluateState())));
            }

            theResult = new RecordSet(columns)
            {
                @Override
                public boolean indexValid(int index) throws UserException, InternalException
                {
                    return srcRecordSet.indexValid(index);
                }

                @Override
                public int getLength() throws UserException, InternalException
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
    public List<TableId> getSources()
    {
        return Collections.singletonList(srcTableId);
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
        return new TableOperations(getManager().getRenameTableOperation(this), null, c -> null, deleteId -> newColumns.stream().anyMatch(p -> p.getFirst().equals(deleteId)) ? this::deleteColumn : null, null, null, null);
    }

    private void deleteColumn(ColumnId columnId)
    {
        //TODO
    }

    @Override
    public boolean transformationEquals(Transformation o)
    {
        Transform transform = (Transform) o;

        if (!newColumns.equals(transform.newColumns)) return false;
        return srcTableId.equals(transform.srcTableId);
    }

    @Override
    public int transformationHashCode()
    {
        int result = newColumns.hashCode();
        result = 31 * result + srcTableId.hashCode();
        return result;
    }

    public List<Pair<ColumnId, Expression>> getCalculatedColumns()
    {
        return newColumns;
    }

    public static class Info extends TransformationInfo
    {
        public Info()
        {
            super("calculate", "Calculate", "preview-calculate.png", "calculate.explanation.short", Arrays.asList("transform"));
        }

        @Override
        public @OnThread(Tag.Simulation) Transformation load(TableManager mgr, InitialLoadDetails initialLoadDetails, List<TableId> source, String detail) throws InternalException, UserException
        {
            ImmutableList.Builder<Pair<ColumnId, Expression>> columns = ImmutableList.builder();

            TransformContext transform = Utility.parseAsOne(detail, TransformationLexer::new, TransformationParser::new, p -> p.transform());
            for (TransformItemContext transformItemContext : transform.transformItem())
            {
                columns.add(new Pair<>(new ColumnId(transformItemContext.column.getText()), Expression.parse(null, transformItemContext.expression().EXPRESSION().getText(), mgr.getTypeManager())));
            }

            return new Transform(mgr, initialLoadDetails, source.get(0), columns.build());
        }

        @Override
        public @OnThread(Tag.FXPlatform) TransformationEditor editNew(View view, TableManager mgr, @Nullable TableId srcTableId, @Nullable Table src)
        {
            return new Editor(view, mgr, srcTableId, Collections.singletonList(new Pair<ColumnId, Expression>(new ColumnId(""), new NumericLiteral(0, null))));
        }
    }

    @OnThread(Tag.FXPlatform)
    private static class Editor extends TransformationEditor
    {
        private final SingleSourceControl srcControl;
        private final ColumnExpressionList columnEditors;

        @OnThread(Tag.FXPlatform)
        public Editor(View view, TableManager mgr, @Nullable TableId srcId, List<Pair<ColumnId, Expression>> newColumns)
        {
            this.srcControl = new SingleSourceControl(view, mgr, srcId);
            this.columnEditors = new ColumnExpressionList(mgr, srcControl, true, newColumns);
        }


        @Override
        public Pair<@LocalizableKey String, @LocalizableKey String> getDescriptionKeys()
        {
            return new Pair<>("calculate.description.short", "calculate.description.rest");
        }

        @Override
        public TransformationInfo getInfo()
        {
            return new Info();
        }

        @Override
        public @Localized String getDisplayTitle()
        {
            return TranslationUtility.getString("transformEditor.calculate.title");
        }

        @Override
        public Pane getParameterDisplay(FXPlatformConsumer<Exception> reportError)
        {
            return columnEditors.getNode();
        }
        
        @Override
        public @Nullable SimulationSupplier<Transformation> getTransformation(TableManager mgr, InitialLoadDetails initialLoadDetails)
        {
            SimulationSupplier<TableId> srcId = srcControl.getTableIdSupplier();
            ImmutableList.Builder<Pair<ColumnId, Expression>> cols = ImmutableList.builder();
            for (Pair<ObjectExpression<@Nullable ColumnId>, ObjectExpression<Expression>> col : columnEditors.getColumns())
            {
                @Nullable ColumnId columnId = col.getFirst().getValue();
                if (columnId == null)
                    return null;
                cols.add(new Pair<>(columnId, col.getSecond().getValue()));
            }
            return () -> new Transform(mgr, initialLoadDetails, srcId.get(), cols.build());
        }

        @Override
        public @Nullable TableId getSourceId()
        {
            return srcControl.getTableIdOrNull();
        }
    }
}
