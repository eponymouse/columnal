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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
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
import utility.gui.FXUtility;
import utility.gui.FXUtility.DragHandler;
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

            theResult = new RecordSet(Utility.mapList(shownColumns, c -> rs -> new Column(rs)
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
    @OnThread(Tag.Any)
    public List<TableId> getSources()
    {
        return Collections.singletonList(srcTableId);
    }

    @Override
    public @OnThread(Tag.FXPlatform) TransformationEditor edit(View view)
    {
        return new Editor(view, getManager(), getId(), srcTableId, src, hideIds);
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
        private final @Nullable TableId tableId;
        private final SingleSourceControl srcControl;
        private final ObservableList<ColumnId> columnsToHide;
        private final ListView<ColumnId> srcColumnList;

        private final ObservableList<DeletableListCell> selectedCells;
        private boolean hoverOverSelection = false;

        @OnThread(Tag.FXPlatform)
        @SuppressWarnings("initialization")
        public Editor(View view, TableManager mgr, @Nullable TableId tableId, @Nullable TableId srcTableId, @Nullable Table src, List<ColumnId> toHide)
        {
            this.tableId = tableId;
            columnsToHide = FXCollections.observableArrayList(toHide);
            this.srcControl = new SingleSourceControl(view, mgr, srcTableId);
            this.srcColumnList = getColumnListView(mgr, srcControl.tableIdProperty(), col -> {
                addAllItems(Collections.singletonList(col));
            });
            srcColumnList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            selectedCells = FXCollections.observableArrayList();
            srcColumnList.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ENTER)
                {
                    addAllItems(srcColumnList.getSelectionModel().getSelectedItems());
                }
            });
            FXUtility.enableDragFrom(srcColumnList, "ColumnId", TransferMode.COPY);
        }

        @Override
        public String getDisplayTitle()
        {
            return "Hide";
        }

        @Override
        public @Localized String getDescription()
        {
            return TranslationUtility.getString("hide.description");
        }

        @Override
        public Pane getParameterDisplay(FXPlatformConsumer<Exception> reportError)
        {
            Button add = new Button(">>");
            add.setMinWidth(Region.USE_PREF_SIZE);
            VBox addWrapper = new VBox(add);
            addWrapper.getStyleClass().add("add-wrapper");

            ListView<ColumnId> hiddenColumns = new ListView<>(columnsToHide);
            hiddenColumns.setCellFactory(lv -> new DeletableListCell(lv));
            hiddenColumns.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            hiddenColumns.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE)
                {
                    deleteSelection(hiddenColumns);
                }
            });

            FXUtility.enableDragTo(hiddenColumns, Collections.singletonMap(FXUtility.getTextDataFormat("ColumnId"), new DragHandler()
            {
                @Override
                public @OnThread(Tag.FXPlatform) void dragMoved(Point2D pointInScene)
                {

                }

                @Override
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

            HBox.setHgrow(srcColumnList, Priority.ALWAYS);
            HBox.setHgrow(hiddenColumns, Priority.ALWAYS);
            HBox hBox = new HBox(srcColumnList, addWrapper, hiddenColumns);
            hBox.getStyleClass().add("hide-columns-lists");
            return new VBox(srcControl, hBox);
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
        public SimulationSupplier<Transformation> getTransformation(TableManager mgr)
        {
            SimulationSupplier<TableId> srcId = srcControl.getTableIdSupplier();
            return () -> new HideColumns(mgr, tableId, srcId.get(), columnsToHide);
        }

        @Override
        public @Nullable TableId getSourceId()
        {
            return srcControl.getTableIdOrNull();
        }

        @OnThread(Tag.FXPlatform)
        public void deleteSelection(ListView<ColumnId> listView)
        {
            ArrayList<DeletableListCell> cols = new ArrayList<>(selectedCells);
            List<ColumnId> selectedItems = new ArrayList<>(listView.getSelectionModel().getSelectedItems());
            listView.getSelectionModel().clearSelection();
            DeletableListCell.animateOutToRight(cols, () -> columnsToHide.removeAll(selectedItems));
        }

        private class DeletableListCell extends SlidableListCell<ColumnId>
        {
            private final SmallDeleteButton button;
            private final Label label;

            @SuppressWarnings("initialization")
            public DeletableListCell(ListView<ColumnId> listView)
            {
                getStyleClass().add("deletable-list-cell");
                button = new SmallDeleteButton();
                button.setOnAction(() -> {
                    if (isSelected())
                    {
                        // Delete all in selection
                        deleteSelection(listView);
                    }
                    else
                    {
                        // Just delete this one
                        animateOutToRight(Collections.singletonList(this), () -> columnsToHide.remove(getItem()));
                    }
                });
                button.setOnHover(entered -> {
                    if (isSelected())
                    {
                        hoverOverSelection = entered;
                        // Set hover state on all (including us):
                        for (DeletableListCell selectedCell : selectedCells)
                        {
                            selectedCell.updateHoverState(hoverOverSelection);
                        }

                    }
                    // If not selected, nothing to do
                });
                label = new Label("");
                BorderPane.setAlignment(label, Pos.CENTER_LEFT);
                BorderPane borderPane = new BorderPane(label, null, button, null, null);
                borderPane.getStyleClass().add("deletable-list-cell-content");
                setGraphic(borderPane);
            }

            private void updateHoverState(boolean hovering)
            {
                pseudoClassStateChanged(PseudoClass.getPseudoClass("my_hover_sel"), hovering);
            }

            @Override
            public void updateSelected(boolean selected)
            {
                if (selected)
                {
                    selectedCells.add(this);
                    updateHoverState(hoverOverSelection);
                }
                else
                {
                    selectedCells.remove(this);
                    updateHoverState(false);
                }
                super.updateSelected(selected);
            }

            @Override
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            protected void updateItem(ColumnId item, boolean empty)
            {
                if (empty)
                {
                    label.setText("");
                    button.setVisible(false);
                }
                else
                {
                    label.setText(item.toString());
                    button.setVisible(true);
                }

                super.updateItem(item, empty);
            }
        }
    }

    public static class Info extends SingleSourceTransformationInfo
    {
        public Info()
        {
            super("hide", Arrays.asList("collapse"));
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
            return new Editor(view, mgr, null, srcTableId, src, Collections.emptyList());
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
