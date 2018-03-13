package records.transformations;

import com.google.common.collect.ImmutableList;
import javafx.scene.layout.Pane;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.ColumnId;
import records.data.RecordSet;
import records.data.TableAndColumnRenames;
import records.data.Table;
import records.data.TableId;
import records.data.TableManager;
import records.data.Transformation;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.TransformationLexer;
import records.grammar.TransformationParser;
import records.grammar.TransformationParser.HideColumnsContext;
import records.gui.SingleSourceControl;
import records.gui.View;
import records.loadsave.OutputBuilder;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExFunction;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.SimulationSupplier;
import utility.Utility;
import utility.gui.GUI;
import utility.gui.TranslationUtility;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Keeps all data as-is, but hides a given set of columns from the resulting
 * data set.
 */
@OnThread(Tag.Simulation)
public class HideColumns extends TransformationEditable
{
    public static final String NAME = "drop.columns";
    @OnThread(Tag.Any)
    private final TableId srcTableId;
    private final @Nullable Table src;
    private final @Nullable RecordSet result;
    @OnThread(Tag.Any)
    private final ImmutableList<ColumnId> hideIds;
    private final List<Column> shownColumns = new ArrayList<>();
    @OnThread(Tag.Any)
    private String error;

    public HideColumns(TableManager mgr, InitialLoadDetails initialLoadDetails, TableId srcTableId, ImmutableList<ColumnId> toHide) throws InternalException
    {
        super(mgr, initialLoadDetails);
        this.srcTableId = srcTableId;
        this.src = mgr.getSingleTableOrNull(srcTableId);
        this.error = "Unknown error with table \"" + getId() + "\"";
        this.hideIds = toHide;
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

            theResult = new <Column>RecordSet(Utility.mapList(shownColumns, c -> (ExFunction<RecordSet, Column>)(rs -> new Column(rs, c.getName())
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
            })))
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
    @OnThread(Tag.Any)
    public List<TableId> getSources()
    {
        return Collections.singletonList(srcTableId);
    }

    @Override
    public @OnThread(Tag.FXPlatform) TransformationEditor edit(View view)
    {
        return new Editor(view, getManager(), srcTableId, hideIds);
    }

    @Override
    protected @OnThread(Tag.Any) String getTransformationName()
    {
        return NAME;
    }

    @Override
    protected @OnThread(Tag.Any) List<String> saveDetail(@Nullable File destination, TableAndColumnRenames renames)
    {
        OutputBuilder b = new OutputBuilder();
        for (ColumnId c : hideIds)
            b.kw("HIDE").id(renames.columnId(getId(), c)).nl();
        return b.toLines();
    }

    @Override
    public @OnThread(Tag.Any) RecordSet getData() throws UserException, InternalException
    {
        if (result == null)
            throw new UserException(error);
        return result;
    }

    private static class Editor extends TransformationEditor
    {
        private final SingleSourceControl srcControl;
        private final HideColumnsPanel columnsPanel;

        @OnThread(Tag.FXPlatform)
        @SuppressWarnings("initialization")
        public Editor(View view, TableManager mgr, @Nullable TableId srcTableId, ImmutableList<ColumnId> toHide)
        {
            this.srcControl = new SingleSourceControl(view, mgr, srcTableId);
            this.columnsPanel = new HideColumnsPanel(mgr, srcControl.tableIdProperty(), toHide);
        }

        @Override
        public TransformationInfo getInfo()
        {
            return new Info();
        }

        @Override
        public @Localized String getDisplayTitle()
        {
            return TranslationUtility.getString("transformEditor.hide.title");
        }

        @Override
        public Pair<@LocalizableKey String, @LocalizableKey String> getDescriptionKeys()
        {
            return new Pair<>("hide.description.short", "hide.description.rest");
        }

        @Override
        public Pane getParameterDisplay(FXPlatformConsumer<Exception> reportError)
        {
            return GUI.vbox("hide-columns-content", srcControl, columnsPanel.getNode());
        }

        @Override
        public SimulationSupplier<Transformation> getTransformation(TableManager mgr, InitialLoadDetails initialLoadDetails)
        {
            SimulationSupplier<TableId> srcId = srcControl.getTableIdSupplier();
            ImmutableList<ColumnId> hiddenCols = columnsPanel.getHiddenColumns();
            return () -> new HideColumns(mgr, initialLoadDetails, srcId.get(), hiddenCols);
        }

        @Override
        public @Nullable TableId getSourceId()
        {
            return srcControl.getTableIdOrNull();
        }
    }

    public static class Info extends SingleSourceTransformationInfo
    {
        public Info()
        {
            super(NAME, "Drop columns", "preview-hide.png", "hide.explanation.short", Arrays.asList("collapse"));
        }

        @Override
        protected @OnThread(Tag.Simulation) Transformation loadSingle(TableManager mgr, InitialLoadDetails initialLoadDetails, TableId srcTableId, String detail) throws InternalException, UserException
        {
            HideColumnsContext loaded = Utility.parseAsOne(detail, TransformationLexer::new, TransformationParser::new, TransformationParser::hideColumns);

            return new HideColumns(mgr, initialLoadDetails, srcTableId, loaded.hideColumn().stream().map(hc -> new ColumnId(hc.column.getText())).collect(ImmutableList.toImmutableList()));
        }

        @Override
        public @OnThread(Tag.FXPlatform) TransformationEditor editNew(View view, TableManager mgr, @Nullable TableId srcTableId, @Nullable Table src)
        {
            return new Editor(view, mgr, srcTableId, ImmutableList.of());
        }
    }

    @Override
    public boolean transformationEquals(Transformation o)
    {
        HideColumns that = (HideColumns) o;

        if (!srcTableId.equals(that.srcTableId)) return false;
        return hideIds.equals(that.hideIds);
    }

    @Override
    public int transformationHashCode()
    {
        int result = srcTableId.hashCode();
        result = 31 * result + hideIds.hashCode();
        return result;
    }

    @Override
    @OnThread(Tag.Any)
    public Table.MessageWhenEmpty getDisplayMessageWhenEmpty()
    {
        return new MessageWhenEmpty("table.hideColumns.noColumns", "table.transformation.noRows");
    }
}
