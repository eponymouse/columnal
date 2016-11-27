package records.transformations;

import javafx.beans.binding.BooleanExpression;
import javafx.beans.binding.StringExpression;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.Column.ProgressListener;
import records.data.ColumnId;
import records.data.NumericColumnStorage;
import records.data.RecordSet;
import records.data.Table;
import records.data.TableId;
import records.data.TableManager;
import records.data.Transformation;
import records.data.datatype.DataType;
import records.error.FunctionInt;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.expression.Expression;
import records.transformations.expression.NumericLiteral;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.SimulationSupplier;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 23/11/2016.
 */
@OnThread(Tag.Simulation)
public class Filter extends Transformation
{
    private final TableId srcTableId;
    private final @Nullable Table src;
    // Not actually a column by itself, but holds a list of integers so reasonable to re-use:
    // Each item is a source index in the original list
    private final NumericColumnStorage indexMap;
    private final @Nullable RecordSet recordSet;
    @OnThread(Tag.Any)
    private String error;
    private int nextIndexToExamine = 0;
    @OnThread(Tag.Any)
    private final Expression filterExpression;

    @SuppressWarnings("initialization")
    public Filter(TableManager mgr, @Nullable TableId tableId, TableId srcTableId, Expression filterExpression) throws InternalException
    {
        super(mgr, tableId);
        this.srcTableId = srcTableId;
        this.src = mgr.getSingleTableOrNull(srcTableId);
        this.indexMap = new NumericColumnStorage();
        this.filterExpression = filterExpression;
        this.error = "Unknown error";

        @Nullable RecordSet theRecordSet = null;
        try
        {
            if (src != null)
            {
                List<FunctionInt<RecordSet, Column>> columns = new ArrayList<>();
                RecordSet data = src.getData();
                for (Column c : data.getColumns())
                {
                    columns.add(rs -> new Column(rs)
                    {
                        @Override
                        public @OnThread(Tag.Any) ColumnId getName()
                        {
                            return c.getName();
                        }

                        @Override
                        @SuppressWarnings({"nullness", "initialization"})
                        public @OnThread(Tag.Any) DataType getType() throws InternalException, UserException
                        {
                            return c.getType().copyReorder((i, prog) ->
                            {
                                fillIndexMapTo(i, data, prog);
                                return indexMap.get(i).intValue();
                            });
                        }
                    });
                }

                theRecordSet = new RecordSet("Filtered", columns)
                {
                    @Override
                    public boolean indexValid(int index) throws UserException, InternalException
                    {
                        if (index < indexMap.filled())
                            return true;

                        fillIndexMapTo(index, data, null);
                        return false;
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

    private void fillIndexMapTo(int index, RecordSet data, @Nullable ProgressListener prog) throws UserException, InternalException
    {
        int start = indexMap.filled();
        while (indexMap.filled() <= index && data.indexValid(nextIndexToExamine))
        {
            boolean keep = filterExpression.getBoolean(data, nextIndexToExamine, prog);
            if (keep)
                indexMap.add(nextIndexToExamine);
            nextIndexToExamine += 1;

            if (prog != null)
                prog.progressUpdate((double)(indexMap.filled() - start) / (double)(index - start));
        }
    }

    @Override
    public @OnThread(Tag.FXPlatform) String getTransformationLabel()
    {
        return "Filter";
    }

    @Override
    public @OnThread(Tag.FXPlatform) List<TableId> getSources()
    {
        return Collections.singletonList(srcTableId);
    }

    @Override
    public @OnThread(Tag.FXPlatform) TransformationEditor edit()
    {
        return new Editor(getId(), srcTableId, src);
    }

    @Override
    protected @OnThread(Tag.Any) String getTransformationName()
    {
        return "filter";
    }

    @Override
    protected @OnThread(Tag.FXPlatform) List<String> saveDetail(@Nullable File destination)
    {
        return Collections.singletonList(filterExpression.save());
    }

    @Override
    public @OnThread(Tag.Any) RecordSet getData() throws UserException
    {
        if (recordSet == null)
            throw new UserException(error);
        return recordSet;
    }

    private static class Editor extends TransformationEditor
    {
        private final @Nullable TableId thisTableId;
        private final TableId srcTableId;
        private final @Nullable Table src;

        public Editor(@Nullable TableId thisTableId, TableId srcTableId, @Nullable Table src)
        {
            this.thisTableId = thisTableId;
            this.srcTableId = srcTableId;
            this.src = src;
        }

        @Override
        public @OnThread(Tag.FX) StringExpression displayTitle()
        {
            return new ReadOnlyStringWrapper("Filter");
        }

        @Override
        public Pane getParameterDisplay(FXPlatformConsumer<Exception> reportError)
        {
            return new BorderPane(new Label("TODO"));
        }

        @Override
        public BooleanExpression canPressOk()
        {
            return new ReadOnlyBooleanWrapper(true);
        }

        @Override
        public SimulationSupplier<Transformation> getTransformation(TableManager mgr)
        {
            return () -> new Filter(mgr, thisTableId, srcTableId, new NumericLiteral(0));
        }

        @Override
        public @Nullable Table getSource()
        {
            return src;
        }

        @Override
        public TableId getSourceId()
        {
            return srcTableId;
        }
    }

    public static class Info extends SingleSourceTransformationInfo
    {
        public Info()
        {
            super("filter", Arrays.asList("remove", "delete"));
        }

        @Override
        public @OnThread(Tag.Simulation) Transformation loadSingle(TableManager mgr, TableId tableId, TableId srcTableId, String detail) throws InternalException, UserException
        {
            return new Filter(mgr, tableId, srcTableId, Expression.parse(detail));
        }

        @Override
        public @OnThread(Tag.FXPlatform) TransformationEditor editNew(TableId srcTableId, @Nullable Table src)
        {
            return new Editor(null, srcTableId, src);
        }
    }
}
