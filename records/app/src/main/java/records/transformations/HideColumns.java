package records.transformations;

import javafx.beans.binding.BooleanExpression;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.Column;
import records.data.ColumnId;
import records.data.RecordSet;
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
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.SimulationSupplier;
import utility.Utility;
import utility.gui.DeletableListView;
import utility.gui.FXUtility;
import utility.gui.FXUtility.DragHandler;
import utility.gui.GUI;
import utility.gui.SlidableListCell;
import utility.gui.SmallDeleteButton;
import utility.gui.TranslationUtility;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Keeps all data as-is, but hides a given set of columns from the resulting
 * data set.
 */
@OnThread(Tag.Simulation)
public class HideColumns extends TransformationEditable
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

            theResult = new RecordSet(Utility.mapList(shownColumns, c -> rs -> new Column(rs, c.getName())
            {
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
    @OnThread(Tag.Any)
    public List<TableId> getSources()
    {
        return Collections.singletonList(srcTableId);
    }

    @Override
    public @OnThread(Tag.FXPlatform) TransformationEditor edit(View view)
    {
        return new Editor(view, getManager(), srcTableId, src, hideIds);
    }

    @Override
    protected @OnThread(Tag.Any) String getTransformationName()
    {
        return "hide";
    }

    @Override
    protected @OnThread(Tag.Any) List<String> saveDetail(@Nullable File destination)
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

    private static class Editor extends TransformationEditor
    {
        private final SingleSourceControl srcControl;
        private final ObservableList<ColumnId> columnsToHide;
        private final ListView<ColumnId> srcColumnList;


        @OnThread(Tag.FXPlatform)
        @SuppressWarnings("initialization")
        public Editor(View view, TableManager mgr, @Nullable TableId srcTableId, @Nullable Table src, List<ColumnId> toHide)
        {
            columnsToHide = FXCollections.observableArrayList(toHide);
            this.srcControl = new SingleSourceControl(view, mgr, srcTableId);
            this.srcColumnList = getColumnListView(mgr, srcControl.tableIdProperty(), col -> {
                addAllItems(Collections.singletonList(col));
            });
            srcColumnList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            srcColumnList.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ENTER)
                {
                    addAllItems(srcColumnList.getSelectionModel().getSelectedItems());
                }
            });
            FXUtility.enableDragFrom(srcColumnList, "ColumnId", TransferMode.COPY);
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
            Button add = new Button(">>");
            add.setMinWidth(Region.USE_PREF_SIZE);
            VBox addWrapper = new VBox(add);
            addWrapper.getStyleClass().add("add-column-wrapper");

            DeletableListView<ColumnId> hiddenColumns = new DeletableListView<>(columnsToHide);

            FXUtility.enableDragTo(hiddenColumns, Collections.singletonMap(FXUtility.getTextDataFormat("ColumnId"), new DragHandler()
            {
                @Override
                @SuppressWarnings("unchecked")
                public @OnThread(Tag.FXPlatform) boolean dragEnded(Dragboard db, Point2D pointInScene)
                {
                    @Nullable Object content = db.getContent(FXUtility.getTextDataFormat("ColumnId"));
                    if (content != null && content instanceof List)
                    {
                        addAllItems((List<ColumnId>) content);
                        return true;
                    }
                    return false;
                }
            }));

            add.setOnAction(e -> {
                ObservableList<ColumnId> selectedItems = srcColumnList.getSelectionModel().getSelectedItems();
                addAllItems(selectedItems);
                //sortHiddenColumns();
            });

            GridPane gridPane = new GridPane();

            GridPane.setHgrow(srcColumnList, Priority.ALWAYS);
            GridPane.setHgrow(hiddenColumns, Priority.ALWAYS);
            gridPane.add(GUI.label("transformEditor.hide.srcColumns"), 0, 0);
            gridPane.add(GUI.label("transformEditor.hide.hiddenColumns"), 2, 0);
            gridPane.add(srcColumnList, 0, 1);
            gridPane.add(addWrapper, 1, 1);
            gridPane.add(hiddenColumns, 2, 1);
            gridPane.getStyleClass().add("hide-columns-lists");
            return GUI.vbox("hide-columns-content", srcControl, gridPane);
        }

        @OnThread(Tag.FXPlatform)
        public void addAllItems(List<ColumnId> items)
        {
            for (ColumnId selected : items)
            {
                if (!columnsToHide.contains(selected))
                {
                    columnsToHide.add(selected);
                }
            }
            ObservableList<ColumnId> srcList = srcColumnList.getItems();
            columnsToHide.sort(Comparator.<ColumnId, Pair<Integer, ColumnId>>comparing(col -> {
                // If it's in the original, we sort by original position
                // Otherwise we put it at the top (which will be -1 in original, which
                // works out neatly) and sort by name.
                int srcIndex = srcList.indexOf(col);
                return new Pair<Integer, ColumnId>(srcIndex, col);
            }, Pair.comparator()));
        }

        @Override
        public BooleanExpression canPressOk()
        {
            return new ReadOnlyBooleanWrapper(true);
        }

        @Override
        public SimulationSupplier<Transformation> getTransformation(TableManager mgr, TableId tableId)
        {
            SimulationSupplier<TableId> srcId = srcControl.getTableIdSupplier();
            return () -> new HideColumns(mgr, tableId, srcId.get(), columnsToHide);
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
            super("drop.columns", "Drop columns", "preview-hide.png", Arrays.asList("collapse"));
        }

        @Override
        protected @OnThread(Tag.Simulation) Transformation loadSingle(TableManager mgr, TableId tableId, TableId srcTableId, String detail) throws InternalException, UserException
        {
            HideColumnsContext loaded = Utility.parseAsOne(detail, TransformationLexer::new, TransformationParser::new, TransformationParser::hideColumns);

            return new HideColumns(mgr, tableId, srcTableId, Utility.mapList(loaded.hideColumn(), hc -> new ColumnId(hc.column.getText())));
        }

        @Override
        public @OnThread(Tag.FXPlatform) TransformationEditor editNew(View view, TableManager mgr, @Nullable TableId srcTableId, @Nullable Table src)
        {
            return new Editor(view, mgr, srcTableId, src, Collections.emptyList());
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
}
