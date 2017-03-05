package records.transformations;

import javafx.beans.binding.BooleanExpression;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.scene.layout.Pane;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.Table;
import records.data.TableId;
import records.data.TableManager;
import records.data.Transformation;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeValue;
import records.error.FunctionInt;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.TransformationLexer;
import records.grammar.TransformationParser;
import records.grammar.TransformationParser.TransformContext;
import records.grammar.TransformationParser.TransformItemContext;
import records.gui.SingleSourceControl;
import records.gui.View;
import records.loadsave.OutputBuilder;
import records.transformations.expression.EvaluateState;
import records.transformations.expression.Expression;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.SimulationSupplier;
import utility.Utility;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
    private final Map<ColumnId, Expression> newColumns;
    private final TableId srcTableId;
    private final @Nullable Table src;
    private final @Nullable RecordSet recordSet;
    @OnThread(Tag.Any)
    private String error = "";

    public Transform(TableManager mgr, @Nullable TableId thisTableId, TableId srcTableId, Map<ColumnId, Expression> toCalculate) throws InternalException
    {
        super(mgr, thisTableId);
        this.srcTableId = srcTableId;
        this.src = mgr.getSingleTableOrNull(srcTableId);
        this.error = "Unknown error with table \"" + thisTableId + "\"";
        this.newColumns = new HashMap<>(toCalculate);
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
            List<FunctionInt<RecordSet, Column>> columns = new ArrayList<>();
            for (Column c : srcRecordSet.getColumns())
            {
                if (!newColumns.containsKey(c.getName()))
                {
                    columns.add(rs -> new Column(rs)
                    {
                        @Override
                        public @OnThread(Tag.Any) ColumnId getName()
                        {
                            return c.getName();
                        }

                        @Override
                        public @OnThread(Tag.Any) DataTypeValue getType() throws InternalException, UserException
                        {
                            return c.getType();
                        }
                    });
                }
            }

            for (Entry<ColumnId, Expression> newCol : toCalculate.entrySet())
            {
                @Nullable DataType type = newCol.getValue().check(srcRecordSet, mgr.getTypeState(), (e, s, q) ->
                {
                    error = s;
                });
                if (type == null)
                    throw new UserException(error); // A bit redundant, but control flow will pan out right
                DataType typeFinal = type;
                columns.add(rs -> typeFinal.makeCalculatedColumn(rs, newCol.getKey(), index -> newCol.getValue().getValue(index, new EvaluateState())));
            }

            theResult = new RecordSet("Transformed", columns)
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
    public @OnThread(Tag.FXPlatform) List<TableId> getSources()
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
        return "transform";
    }

    @Override
    protected @OnThread(Tag.FXPlatform) List<String> saveDetail(@Nullable File destination)
    {
        return newColumns.entrySet().stream().map(entry -> {
            OutputBuilder b = new OutputBuilder();
            b.kw("CALCULATE").id(entry.getKey());
            b.kw("@EXPRESSION");
            b.raw(entry.getValue().save(true));
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

    public static class Info extends TransformationInfo
    {
        public Info()
        {
            super("transform", Arrays.asList("calculate"));
        }

        @Override
        public @OnThread(Tag.Simulation) Transformation load(TableManager mgr, TableId tableId, List<TableId> source, String detail) throws InternalException, UserException
        {
            Map<ColumnId, Expression> columns = new HashMap<>();

            TransformContext transform = Utility.parseAsOne(detail, TransformationLexer::new, TransformationParser::new, p -> p.transform());
            for (TransformItemContext transformItemContext : transform.transformItem())
            {
                columns.put(new ColumnId(transformItemContext.column.getText()), Expression.parse(null, transformItemContext.expression().EXPRESSION().getText(), mgr.getTypeManager()));
            }

            return new Transform(mgr, tableId, source.get(0), columns);
        }

        @Override
        public @OnThread(Tag.FXPlatform) TransformationEditor editNew(View view, TableManager mgr, @Nullable TableId srcTableId, @Nullable Table src)
        {
            return new Editor(view, mgr, null, srcTableId, Collections.emptyMap());
        }
    }
    private static class Editor extends TransformationEditor
    {
        private final @Nullable TableId ourId;
        private final SingleSourceControl srcControl;
        private final Map<ColumnId, Expression> newColumns = new HashMap<>();

        @OnThread(Tag.FXPlatform)
        public Editor(View view, TableManager mgr, @Nullable TableId id, @Nullable TableId srcId, Map<ColumnId, Expression> newColumns)
        {
            ourId = id;
            this.srcControl = new SingleSourceControl(view, mgr, srcId);
            this.newColumns.putAll(newColumns);
        }

        @Override
        public String getDisplayTitle()
        {
            return "Transform";
        }

        @Override
        public Pane getParameterDisplay(FXPlatformConsumer<Exception> reportError)
        {
            return new Pane(); // TODO
        }

        @Override
        public BooleanExpression canPressOk()
        {
            return new ReadOnlyBooleanWrapper(true);
        }

        @Override
        public SimulationSupplier<Transformation> getTransformation(TableManager mgr)
        {
            SimulationSupplier<TableId> srcId = srcControl.getTableIdSupplier();
            return () -> new Transform(mgr, ourId, srcId.get(), newColumns);
        }

        @Override
        public @Nullable TableId getSourceId()
        {
            return srcControl.getTableIdOrNull();
        }
    }
}
