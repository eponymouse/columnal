package records.transformations;

import javafx.beans.binding.BooleanExpression;
import javafx.beans.binding.StringExpression;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.layout.Pane;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.ColumnId;
import records.data.KnownLengthRecordSet;
import records.data.RecordSet;
import records.data.Table;
import records.data.TableId;
import records.data.TableManager;
import records.data.Transformation;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import records.loadsave.OutputBuilder;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.SimulationSupplier;
import utility.Utility;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Keeps all data as-is, but hides a given set of columns from the resulting
 * data set.
 */
@OnThread(Tag.Simulation)
public class HideColumns extends Transformation
{
    @OnThread(Tag.Any)
    private final TableId srcTableId;
    private final @Nullable Table src;
    private final @Nullable RecordSet result;
    @OnThread(Tag.Any)
    private final List<ColumnId> hideIds;
    private final List<Column> shownColumns = new ArrayList<>();
    @OnThread(Tag.Any)
    private String error;

    public HideColumns(TableManager mgr, @Nullable TableId thisTableId, TableId srcTableId, List<ColumnId> toHide) throws InternalException
    {
        super(mgr, thisTableId);
        this.srcTableId = srcTableId;
        this.src = mgr.getSingleTableOrNull(srcTableId);
        this.error = "Unknown error with table \"" + thisTableId + "\"";
        this.hideIds = new ArrayList<>(toHide);
        if (this.src == null)
        {
            this.result = null;
            error = "Could not find source table: \"" + srcTableId + "\"";
            return;
        }


        @Nullable RecordSet theResult = null;
        try
        {
            ArrayList<ColumnId> stillToHide = new ArrayList<>(toHide);
            RecordSet srcRecordSet = this.src.getData();
            for (Column c : srcRecordSet.getColumns())
            {
                if (!stillToHide.remove(c.getName()))
                {
                    shownColumns.add(c);
                }
            }

            if (!stillToHide.isEmpty())
                throw new UserException("Source table does not contain columns " + Utility.listToString(stillToHide));

            if (shownColumns.isEmpty())
                throw new UserException("Cannot hide all columns");

            theResult = new RecordSet("Hidden", Utility.mapList(shownColumns, c -> rs -> new Column(rs)
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
            }))
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

        result = theResult;
    }

    @Override
    public @OnThread(Tag.FXPlatform) String getTransformationLabel()
    {
        return "Hide";
    }

    @Override
    public @OnThread(Tag.FXPlatform) List<TableId> getSources()
    {
        return Collections.emptyList(); //TODO
    }

    @Override
    public @OnThread(Tag.FXPlatform) TransformationEditor edit()
    {
        return new Editor();
    }

    @Override
    protected @OnThread(Tag.Any) String getTransformationName()
    {
        return "hide";
    }

    @Override
    protected @OnThread(Tag.FXPlatform) List<String> saveDetail(@Nullable File destination)
    {
        OutputBuilder b = new OutputBuilder();
        for (ColumnId c : hideIds)
            b.kw("HIDE").id(c).nl();
        return b.toLines();
    }

    @Override
    public @OnThread(Tag.Any) RecordSet getData() throws UserException, InternalException
    {
        if (result == null)
            throw new UserException(error);
        return result;
    }

    private class Editor extends TransformationEditor
    {
        @Override
        public @OnThread(Tag.FX) StringExpression displayTitle()
        {
            return new ReadOnlyStringWrapper("Hide");
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
            throw new RuntimeException(); // TODO
        }

        @Override
        public @Nullable Table getSource()
        {
            return null;
        }

        @Override
        public TableId getSourceId()
        {
            throw new RuntimeException(); // TODO
        }
    }
}
