package records.transformations;

import annotation.qual.Value;
import javafx.beans.binding.BooleanExpression;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
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
import records.data.datatype.NumberInfo;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.error.FunctionInt;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.TransformationLexer;
import records.grammar.TransformationParser;
import records.grammar.TransformationParser.OrderByContext;
import records.grammar.TransformationParser.SortContext;
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
import utility.gui.FXUtility;
import utility.gui.GUI;
import utility.gui.TranslationUtility;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A transformation which preserves all data from the original table
 * but sorts it.
 *
 * Error behaviour:
 *   - Errors in every place if the sort-by columns can't be found.
 */
@OnThread(Tag.Simulation)
public class Sort extends TransformationEditable
{

    public static final String NAME = "sort";

    private static enum Direction { ASCENDING, DESCENDING; }

    @OnThread(Tag.Any)
    private String sortByError;
    @OnThread(Tag.Any)
    private final TableId srcTableId;
    private final @Nullable Table src;
    private final @Nullable RecordSet result;
    // Not actually a column by itself, but holds a list of integers so reasonable to re-use:
    private final @Nullable NumericColumnStorage sortMap;

    // This works like a linked list, but flattened into an integer array.
    // The first item is the head, then others point onwards.  (Irritatingly, stillToOrder[n]
    // corresponds to originalIndex=n-1, due to having the head.
    // To begin with, each item points to the next:
    // 0 : 1 [HEAD: originalIndex = 0]
    // 1 : 2 [next item after originalIndex=0 is originalIndex=1]
    // 2 : 3 [next item after originalIndex=1 is originalIndex=2]
    // 3 : 4, etc
    // Then, when we use an item from the list, e.g. item 2 from original list, we update pointers:
    // 0 : 1
    // 1 : 2
    // 2 : 4 [next item after originalIndex=1 is originalIndex=3]
    // 3 : 4 [now stillToOrder], etc
    // Then if we use item 1, we update again:
    // 0 : 1
    // 1 : 4 [next item after originalIndex=0 is originalIndex=3]
    // 2 : 4
    // 3 : 4 [now stillToOrder], etc
    private int @Nullable [] stillToOrder;
    @OnThread(Tag.Any)
    private final @NonNull List<ColumnId> originalSortBy;
    private final @Nullable List<Column> sortBy;

    public Sort(TableManager mgr, @Nullable TableId thisTableId, TableId srcTableId, List<ColumnId> sortBy) throws InternalException
    {
        super(mgr, thisTableId);
        this.srcTableId = srcTableId;
        this.src = mgr.getSingleTableOrNull(srcTableId);
        this.originalSortBy = sortBy;
        this.sortByError = "Unknown error with table \"" + thisTableId + "\"";
        if (this.src == null)
        {
            this.result = null;
            this.sortBy = null;
            this.sortMap = null;
            sortByError = "Could not find source table: \"" + srcTableId + "\"";
            return;
        }
        @Nullable RecordSet theResult = null;
        @Nullable List<Column> theSortBy = null;
        try
        {
            List<Column> sortByColumns = new ArrayList<>();
            for (ColumnId c : originalSortBy)
            {
                @Nullable Column column = this.src.getData().getColumn(c);
                if (column == null)
                {
                    sortByColumns = null;
                    this.sortByError = "Could not find source column to sort by: \"" + c + "\"";
                    break;
                }
                sortByColumns.add(column);
            }
            theSortBy = sortByColumns;

            List<ExFunction<RecordSet, Column>> columns = new ArrayList<>();

            RecordSet srcRecordSet = src.getData();
            this.stillToOrder = new int[srcRecordSet.getLength() + 1];
            for (int i = 0; i < stillToOrder.length - 1; i++)
                stillToOrder[i] = i + 1;
            stillToOrder[stillToOrder.length - 1] = -1;
            for (Column c : srcRecordSet.getColumns())
            {
                columns.add(rs -> new Column(rs, c.getName())
                {
                    @Override
                    @SuppressWarnings({"nullness", "initialization"})
                    public @OnThread(Tag.Any) DataTypeValue getType() throws InternalException, UserException
                    {
                        return c.getType().copyReorder((i, prog) ->
                        {
                            fillSortMapTo(i, prog);
                            return DataTypeUtility.value(sortMap.getInt(i));
                        });
                    }
                });
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
                this.sortByError = msg;
        }
        this.result = theResult;
        this.sortMap = new NumericColumnStorage(NumberInfo.DEFAULT);
        this.sortBy = theSortBy;
    }

    private void fillSortMapTo(int target, @Nullable ProgressListener prog) throws InternalException, UserException
    {
        if (sortMap == null)
            throw new InternalException("Trying to fill null sort map; error in initialisation carried forward.");
        int destStart = sortMap.filled();
        for (int dest = destStart; dest <= target; dest++)
        {
            int lowestIndex = 0;
            @Nullable List<@Value Object> lowest = null;
            int pointerToLowestIndex = -1;
            int prevSrc = 0;
            if (stillToOrder == null)
                throw new InternalException("Trying to re-sort an already sorted list");
            for (int src = stillToOrder[prevSrc]; src != -1; src = stillToOrder[src])
            {
                // src is in stillToOrder terms, which is one more than original indexes
                List<@Value Object> cur = getItem(src - 1);
                if (lowest == null || Utility.compareLists(cur, lowest) < 0)
                {
                    lowest = cur;
                    lowestIndex = src;
                    pointerToLowestIndex = prevSrc;
                }
                prevSrc = src;
            }
            if (lowest != null)
            {
                // Make the pointer behind lowest point to entry after lowest:
                stillToOrder[pointerToLowestIndex] = stillToOrder[lowestIndex];
                // Still to order is empty, so null it to garbage collect the memory:
                if (stillToOrder[0] == -1)
                    stillToOrder = null;
                // lowestIndex is in stillToOrder terms, which is one more than original indexes
                sortMap.add(lowestIndex - 1);
            }
            else
            {
                throw new InternalException("Not enough items available to fill source list");
            }

            if (prog != null)
                prog.progressUpdate((double)(dest - destStart) / (double)(target - dest));
        }
    }

    @Pure
    private List<@Value Object> getItem(int srcIndex) throws UserException, InternalException
    {
        if (sortBy == null)
            throw new UserException(sortByError);
        List<@Value Object> r = new ArrayList<>();
        for (Column c : sortBy)
            r.add(c.getType().getCollapsed(srcIndex));
        return r;
    }

    @Override
    public @OnThread(Tag.Any) RecordSet getData() throws UserException
    {
        if (result == null)
            throw new UserException(sortByError);
        return result;
    }

    @Override
    public @OnThread(Tag.FXPlatform) String getTransformationLabel()
    {
        return "Sort";
    }

    @Override
    protected @OnThread(Tag.Any) String getTransformationName()
    {
        return NAME;
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
        return new Editor(view, getManager(), getId(), srcTableId, originalSortBy);
    }

    @OnThread(Tag.FXPlatform)
    public static class Info extends SingleSourceTransformationInfo
    {
        @OnThread(Tag.Any)
        public Info()
        {
            super(NAME, "Sort", Collections.emptyList());
        }

        @Override
        @OnThread(Tag.Simulation)
        public Transformation loadSingle(TableManager mgr, TableId tableId, TableId srcTableId, String detail) throws InternalException, UserException
        {
            SortContext loaded = Utility.parseAsOne(detail, TransformationLexer::new, TransformationParser::new, TransformationParser::sort);

            return new Sort(mgr, tableId, srcTableId, Utility.<OrderByContext, ColumnId>mapList(loaded.orderBy(), o -> new ColumnId(o.column.getText())));
        }

        @Override
        public TransformationEditor editNew(View view, TableManager mgr, @Nullable TableId srcTableId, @Nullable Table src)
        {
            return new Editor(view, mgr, null, srcTableId, Collections.emptyList());
        }
    }

    private static class Editor extends TransformationEditor
    {
        private final @Nullable TableId thisTableId;
        private final SingleSourceControl srcControl;
        private final ObservableList<Optional<ColumnId>> sortBy;
        private final BooleanProperty ready = new SimpleBooleanProperty(false);
        private final ListView<ColumnId> columnListView;

        @SuppressWarnings("initialization")
        @OnThread(Tag.FXPlatform)
        private Editor(View view, TableManager mgr, @Nullable TableId thisTableId, @Nullable TableId srcTableId, List<ColumnId> sortBy)
        {
            this.thisTableId = thisTableId;
            this.srcControl = new SingleSourceControl(view, mgr, srcTableId);
            this.sortBy = FXCollections.observableArrayList();
            // TODO handle case that src table is missing
            for (ColumnId c : sortBy)
            {
                this.sortBy.add(Optional.of(c));
            }
            this.sortBy.add(Optional.empty());
            columnListView = getColumnListView(mgr, srcControl.tableIdProperty(), c -> addAllItems(Collections.singletonList(c)));
            columnListView.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ENTER)
                {
                    addAllItems(columnListView.getSelectionModel().getSelectedItems());
                }
            });
            FXUtility.enableDragFrom(columnListView, "ColumnId", TransferMode.COPY);
        }

        private void addAllItems(List<ColumnId> items)
        {
            for (ColumnId column : items)
            {
                if (!sortBy.contains(Optional.of(column)))
                    sortBy.add(sortBy.size() - 1, Optional.of(column));
            }
        }

        @Override
        public String getDisplayTitle()
        {
            return "Sort";
        }

        @Override
        public Pair<@LocalizableKey String, @LocalizableKey String> getDescriptionKeys()
        {
            return new Pair<>("sort.description.short", "sort.description.rest");
        }

        @Override
        public @Nullable TableId getSourceId()
        {
            return srcControl.getTableIdOrNull();
        }

        @Override
        @SuppressWarnings({"keyfor", "intern"})
        public @OnThread(Tag.FXPlatform) Pane getParameterDisplay(FXPlatformConsumer<Exception> reportError)
        {
            GridPane colsAndSort = new GridPane();
            colsAndSort.add(GUI.label("transformEditor.sort.srcColumns"), 0, 0);
            colsAndSort.add(GUI.label("transformEditor.sort.sortColumns"), 2, 0);

            // TODO need to handle changes to source table.
            columnListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            colsAndSort.add(columnListView, 0, 1);

            Button button = new Button(">>");
            button.setMinWidth(Region.USE_PREF_SIZE);
            button.setOnAction(e ->
            {
                addAllItems(columnListView.getSelectionModel().getSelectedItems());
            });
            colsAndSort.add(GUI.vbox("add-column-wrapper", button), 1, 1);

            ListView<Optional<ColumnId>> sortByView = FXUtility.readOnlyListView(sortBy, c -> !c.isPresent() ? "Original order" : c.get() + ", then if equal, by");
            colsAndSort.add(sortByView, 2, 1);

            //FXUtility.enableDragFrom();
            //FXUtility.enableDragTo(sortByView, Optional::of, "ColumnId");
            /*
            ObservableList<Pair<String, List<DisplayValue>>> srcHeaderAndData = FXCollections.observableArrayList();
            ObservableList<Pair<String, List<DisplayValue>>> destHeaderAndData = FXCollections.observableArrayList();
            List<Column> allColumns = new ArrayList<>();
            try
            {
                if (srcControl.getTableOrNull() != null)
                    allColumns.addAll(srcControl.getTableOrNull().getData().getColumns());
                updateExample(allColumns, getPresent(sortByView.getItems()), srcHeaderAndData, destHeaderAndData);
                FXUtility.listen(sortByView.getItems(), c -> {
                    try
                    {
                        updateExample(allColumns, getPresent(sortByView.getItems()), srcHeaderAndData, destHeaderAndData);
                    }
                    catch (InternalException e)
                    {
                        Utility.report(e);
                    }
                    catch (UserException e)
                    {
                        e.printStackTrace();
                    }
                });
            }
            catch (InternalException e)
            {
                Utility.report(e);
            }
            catch (UserException e)
            {
                // Ignore; we'll just leave example blank
                e.printStackTrace();
            }
            Node example = createExplanation(srcHeaderAndData, destHeaderAndData, "The table's rows will be sorted by the values in the chosen columns.");
            */
            return new VBox(colsAndSort); //, example);
        }

        @OnThread(Tag.FXPlatform)
        private List<Column> getPresent(ObservableList<Optional<ColumnId>> cols) throws UserException, InternalException
        {
            ArrayList<Column> r = new ArrayList<>();
            for (Optional<ColumnId> col : cols)
            {
                if (col.isPresent() && srcControl.getTableOrNull() != null)
                    r.add(srcControl.getTableOrNull().getData().getColumn(col.get()));
            }
            return r;
        }

        /*
        @OnThread(Tag.FXPlatform)
        private void updateExample(List<Column> allColumns, List<Column> sortBy, ObservableList<Pair<String, List<DisplayValue>>> srcHeaderAndData, ObservableList<Pair<String, List<DisplayValue>>> destHeaderAndData) throws UserException, InternalException
        {
            // We try to take 1 not involved and 2 which are involved.
            // If there aren't enough we first take more involved, then more which aren't, then given up
            List<Column> exampleColumns = new ArrayList<>();
            if (allColumns.size() > 3)
            {
                int involved = Math.min(2, sortBy.size());
                if (allColumns.size() == sortBy.size())
                    involved += 1;

                for (int i = 0; i < involved; i++)
                {
                    exampleColumns.add(sortBy.get(i));
                }

                for (Column c : allColumns)
                {
                    if (!sortBy.contains(c))
                    {
                        exampleColumns.add(c);
                        if (exampleColumns.size() == 3)
                            break;
                    }
                }
            }
            else
                exampleColumns.addAll(allColumns);

            List<DataType> exampleColumnTypes = new ArrayList<>();
            for (Column c : exampleColumns)
                exampleColumnTypes.add(c.getType());

            // Generate four rows of data for each column:
            List<List<@Value Object>> data = new ArrayList<>();
            for (int i = 0; i < 4; i++)
                data.add(new ArrayList<>());
            for (Column c : exampleColumns)
            {
                int index = 0;
                for (List<@Value Object> row : data)
                {
                    row.add(DataTypeUtility.generateExample(c.getType(), (index++ + row.size()) % 4));
                }
            }
            // Sort it to get result:
            Collections.<List<@Value Object>>sort(data, new Comparator<List<@Value Object>>()
            {
                @Override
                @OnThread(value = Tag.Simulation, ignoreParent = true)
                public int compare(List<@Value Object> a, List<@Value Object> b)
                {
                    for (Column c : sortBy)
                    {
                        int i = exampleColumns.indexOf(c);
                        if (i != -1)
                        {
                            try
                            {
                                int cmp = Utility.compareValues(a.get(i), b.get(i));
                                if (cmp != 0)
                                    return cmp;
                            } catch (InternalException e)
                            {
                                //Report this?
                                Utility.report(e);
                            } catch (UserException e)
                            {
                                Utility.log(e);
                            }
                        }
                    }
                    return 0;
                }
            });
            List<List<@Value Object>> unsorted = new ArrayList<>();
            if (data.size() == 4)
            {
                unsorted.add(data.get(2));
                unsorted.add(data.get(1));
                unsorted.add(data.get(3));
                unsorted.add(data.get(0));
            }
            // Then produce unsorted version for source:
            List<Pair<String, List<DisplayValue>>> destCols = new ArrayList<>();
            List<Pair<String, List<DisplayValue>>> srcCols = new ArrayList<>();
            for (int i = 0; i < exampleColumns.size(); i++)
            {
                List<DisplayValue> sortedCol = new ArrayList<>();
                for (List<@Value Object> row : data)
                {
                    sortedCol.add(DataTypeUtility.display(-1, exampleColumnTypes.get(i), row.get(i)));
                }
                List<DisplayValue> unsortedCol = new ArrayList<>();
                for (List<@Value Object> row : unsorted)
                {
                    unsortedCol.add(DataTypeUtility.display(-1, exampleColumnTypes.get(i), row.get(i)));
                }
                destCols.add(new Pair<>(exampleColumns.get(i).getName().toString(), sortedCol));
                srcCols.add(new Pair<>(exampleColumns.get(i).getName().toString(), unsortedCol));
            }
            destHeaderAndData.setAll(destCols);
            srcHeaderAndData.setAll(srcCols);
        }
        */

        @Override
        public @OnThread(Tag.FXPlatform) BooleanExpression canPressOk()
        {
            return ready;
        }

        @Override
        public @OnThread(Tag.FXPlatform) SimulationSupplier<Transformation> getTransformation(TableManager mgr)
        {
            SimulationSupplier<TableId> srcId = srcControl.getTableIdSupplier();
            return () -> {
                // Ignore the special empty column put in for GUI:
                List<ColumnId> presentSortBy = new ArrayList<>();
                for (Optional<ColumnId> c : sortBy)
                    if (c.isPresent())
                        presentSortBy.add(c.get());
                return new Sort(mgr, thisTableId, srcId.get(), presentSortBy);
            };
        }
    }

    @Override
    protected @OnThread(Tag.Any) List<String> saveDetail(@Nullable File destination)
    {
        OutputBuilder b = new OutputBuilder();
        for (ColumnId c : originalSortBy)
            b.kw("ASCENDING").id(c).nl();
        return b.toLines();
    }

    @Override
    public boolean transformationEquals(Transformation o)
    {
        Sort sort = (Sort) o;

        if (!srcTableId.equals(sort.srcTableId)) return false;
        return originalSortBy.equals(sort.originalSortBy);

    }

    @Override
    public int transformationHashCode()
    {
        int result = srcTableId.hashCode();
        result = 31 * result + originalSortBy.hashCode();
        return result;
    }
}
