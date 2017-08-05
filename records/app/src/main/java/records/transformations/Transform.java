package records.transformations;

import com.google.common.collect.ImmutableList;
import javafx.beans.binding.BooleanExpression;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import records.data.Column;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.Table;
import records.data.TableId;
import records.data.TableManager;
import records.data.Transformation;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.TransformationLexer;
import records.grammar.TransformationParser;
import records.grammar.TransformationParser.TransformContext;
import records.grammar.TransformationParser.TransformItemContext;
import records.gui.ColumnNameTextField;
import records.gui.SingleSourceControl;
import records.gui.View;
import records.gui.expressioneditor.ExpressionEditor;
import records.loadsave.OutputBuilder;
import records.transformations.expression.EvaluateState;
import records.transformations.expression.Expression;
import records.transformations.expression.NumericLiteral;
import records.transformations.expression.TypeState;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExFunction;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.SimulationSupplier;
import utility.Utility;
import utility.gui.FXUtility;

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
public class Transform extends TransformationEditable
{
    @OnThread(Tag.Any)
    private final ImmutableList<Pair<ColumnId, Expression>> newColumns;
    private final TableId srcTableId;
    private final @Nullable Table src;
    private final @Nullable RecordSet recordSet;
    @OnThread(Tag.Any)
    private String error = "";

    public Transform(TableManager mgr, @Nullable TableId thisTableId, TableId srcTableId, ImmutableList<Pair<ColumnId, Expression>> toCalculate) throws InternalException
    {
        super(mgr, thisTableId);
        this.srcTableId = srcTableId;
        this.src = mgr.getSingleTableOrNull(srcTableId);
        this.error = "Unknown error with table \"" + thisTableId + "\"";
        this.newColumns = toCalculate;
        if (this.src == null)
        {
            this.recordSet = null;
            error = "Could not find source table: \"" + srcTableId + "\"";
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
                    });
                }
            }

            for (Pair<ColumnId, Expression> newCol : toCalculate)
            {
                @Nullable DataType type = newCol.getSecond().check(srcRecordSet, new TypeState(mgr.getUnitManager(), mgr.getTypeManager()), (e, s, q) ->
                {
                    error = s;
                });
                if (type == null)
                    throw new UserException(error); // A bit redundant, but control flow will pan out right
                DataType typeFinal = type;
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
            String msg = e.getLocalizedMessage();
            if (msg != null)
                this.error = msg;
        }

        recordSet = theResult;
    }


    @Override
    public @OnThread(Tag.FXPlatform) String getTransformationLabel()
    {
        return "transform";
    }

    @Override
    @OnThread(Tag.Any)
    public List<TableId> getSources()
    {
        return Collections.singletonList(srcTableId);
    }

    @Override
    public @OnThread(Tag.FXPlatform) TransformationEditor edit(View view)
    {
        return new Editor(view, getManager(), this.getId(), this.srcTableId, this.newColumns);
    }

    @Override
    protected @OnThread(Tag.Any) String getTransformationName()
    {
        return "calculate";
    }

    @Override
    protected @OnThread(Tag.Any) List<String> saveDetail(@Nullable File destination)
    {
        return newColumns.stream().map(entry -> {
            OutputBuilder b = new OutputBuilder();
            b.kw("CALCULATE").id(entry.getFirst());
            b.kw("@EXPRESSION");
            b.raw(entry.getSecond().save(true));
            return b.toString();
        }).collect(Collectors.<String>toList());
    }

    @Override
    public @OnThread(Tag.Any) RecordSet getData() throws UserException, InternalException
    {
        if (recordSet == null)
            throw new UserException(error == null ? "Unknown error" : error);
        return recordSet;
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
            super("Calculate columns", Arrays.asList("transform"));
        }

        @Override
        public @OnThread(Tag.Simulation) Transformation load(TableManager mgr, TableId tableId, List<TableId> source, String detail) throws InternalException, UserException
        {
            ImmutableList.Builder<Pair<ColumnId, Expression>> columns = ImmutableList.builder();

            TransformContext transform = Utility.parseAsOne(detail, TransformationLexer::new, TransformationParser::new, p -> p.transform());
            for (TransformItemContext transformItemContext : transform.transformItem())
            {
                columns.add(new Pair<>(new ColumnId(transformItemContext.column.getText()), Expression.parse(null, transformItemContext.expression().EXPRESSION().getText(), mgr.getTypeManager())));
            }

            return new Transform(mgr, tableId, source.get(0), columns.build());
        }

        @Override
        public @OnThread(Tag.FXPlatform) TransformationEditor editNew(View view, TableManager mgr, @Nullable TableId srcTableId, @Nullable Table src)
        {
            return new Editor(view, mgr, null, srcTableId, Collections.singletonList(new Pair<>(new ColumnId(""), new NumericLiteral(0, null))));
        }
    }

    @OnThread(Tag.FXPlatform)
    private static class Editor extends TransformationEditor
    {
        private final @Nullable TableId ourId;
        private final SingleSourceControl srcControl;
        private final List<Pair<ObjectExpression<@Nullable ColumnId>, ObjectExpression<Expression>>> newColumns = new ArrayList<>();
        private final List<Pair<ColumnNameTextField, ExpressionEditor>> newColumnEdit = new ArrayList<>();
        private SimpleBooleanProperty allColNamesValid = new SimpleBooleanProperty(false);

        @OnThread(Tag.FXPlatform)
        public Editor(View view, TableManager mgr, @Nullable TableId id, @Nullable TableId srcId, List<Pair<ColumnId, Expression>> newColumns)
        {
            ourId = id;
            this.srcControl = new SingleSourceControl(view, mgr, srcId);
            for (Pair<ColumnId, Expression> newColumn : newColumns)
            {
                SimpleObjectProperty<Expression> wrapper = new SimpleObjectProperty<>(newColumn.getSecond());
                ColumnNameTextField columnNameTextField = new ColumnNameTextField(newColumn.getFirst());
                FXUtility.addChangeListenerPlatform(columnNameTextField.valueProperty(), v -> {
                    validateColumnNames();
                });
                this.newColumns.add(new Pair<>(columnNameTextField.valueProperty(), wrapper));
                newColumnEdit.add(new Pair<>(columnNameTextField, makeExpressionEditor(mgr, srcControl, wrapper)));
            }
            validateColumnNames();
        }

        @RequiresNonNull({"allColNamesValid", "newColumns"})
        private void validateColumnNames(@UnknownInitialization Editor this)
        {
            allColNamesValid.set(newColumns.stream().allMatch((Pair<ObjectExpression<@Nullable ColumnId>, ObjectExpression<Expression>> p) -> p.getFirst().get() != null));
        }

        private static ExpressionEditor makeExpressionEditor(TableManager mgr, SingleSourceControl srcControl, SimpleObjectProperty<Expression> container)
        {
            return new ExpressionEditor(container.getValue(), srcControl.getTableOrNull(), new ReadOnlyObjectWrapper<@Nullable DataType>(null), mgr, e -> {
                container.set(e);
            });
        }

        @Override
        public String getDisplayTitle()
        {
            return "Calculate columns";
        }

        @Override
        public Pane getParameterDisplay(FXPlatformConsumer<Exception> reportError)
        {
            // TODO allow adding/removing columns
            GridPane gridPane = new GridPane();
            for (int i = 0; i < newColumnEdit.size(); i++)
            {
                gridPane.add(newColumnEdit.get(i).getFirst().getNode(), 0, i);
                gridPane.add(newColumnEdit.get(i).getSecond().getContainer(), 1, i);
            }
            return gridPane;
        }

        @Override
        public BooleanExpression canPressOk()
        {
            return allColNamesValid;
        }

        @Override
        public SimulationSupplier<Transformation> getTransformation(TableManager mgr)
        {
            SimulationSupplier<TableId> srcId = srcControl.getTableIdSupplier();
            // They were only allowed to press OK if all columns were non-null:
            @SuppressWarnings("nullness")
            ImmutableList<Pair<ColumnId, Expression>> cols = newColumns.stream().
                    map((Pair<ObjectExpression<@Nullable ColumnId>, ObjectExpression<Expression>> p) -> p.map(ObjectExpression::get, ObjectExpression::get)).collect(ImmutableList.toImmutableList());
            return () -> new Transform(mgr, ourId, srcId.get(), cols);
        }

        @Override
        public @Nullable TableId getSourceId()
        {
            return srcControl.getTableIdOrNull();
        }
    }
}
