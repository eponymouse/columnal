package records.transformations;

import javafx.beans.binding.BooleanExpression;
import javafx.beans.binding.StringExpression;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
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
import records.data.datatype.DataType.NumberDisplayInfo;
import records.error.FunctionInt;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.BasicLexer;
import records.grammar.SortParser;
import records.grammar.SortParser.OrderByContext;
import records.grammar.SortParser.SortContext;
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
import java.util.Optional;

/**
 * A transformation which preserves all data from the original table
 * but sorts it.
 *
 * Error behaviour:
 *   - Errors in every place if the sort-by columns can't be found.
 */
@OnThread(Tag.Simulation)
public class Sort extends Transformation
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
        @Nullable NumericColumnStorage theSortMap = null;
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

            theSortMap = new NumericColumnStorage(NumberDisplayInfo.DEFAULT);

            List<FunctionInt<RecordSet, Column>> columns = new ArrayList<>();

            RecordSet srcRecordSet = src.getData();
            this.stillToOrder = new int[srcRecordSet.getLength() + 1];
            for (int i = 0; i < stillToOrder.length - 1; i++)
                stillToOrder[i] = i + 1;
            stillToOrder[stillToOrder.length - 1] = -1;
            for (Column c : srcRecordSet.getColumns())
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
                            fillSortMapTo(i, prog);
                            return sortMap.get(i).intValue();
                        });
                    }
                });
            }

            theResult = new RecordSet("Sorted", columns)
            {
                @Override
                public boolean indexValid(int index) throws UserException
                {
                    return srcRecordSet.indexValid(index);
                }

                @Override
                public int getLength() throws UserException
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
        this.sortMap = theSortMap;
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
            @Nullable List<List<Object>> lowest = null;
            int pointerToLowestIndex = -1;
            int prevSrc = 0;
            if (stillToOrder == null)
                throw new InternalException("Trying to re-sort an already sorted list");
            for (int src = stillToOrder[prevSrc]; src != -1; src = stillToOrder[src])
            {
                // src is in stillToOrder terms, which is one more than original indexes
                List<List<Object>> cur = getItem(src - 1);
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
    private List<List<Object>> getItem(int srcIndex) throws UserException, InternalException
    {
        if (sortBy == null)
            throw new UserException(sortByError);
        List<List<Object>> r = new ArrayList<>();
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
    public @OnThread(Tag.FXPlatform) List<TableId> getSources()
    {
        return Collections.singletonList(srcTableId);
    }

    @Override
    public @OnThread(Tag.FXPlatform) TransformationEditor edit()
    {
        return new Editor(getId(), srcTableId, src, originalSortBy);
    }

    @OnThread(Tag.FXPlatform)
    public static class Info extends TransformationInfo
    {
        @OnThread(Tag.Any)
        public Info()
        {
            super(NAME, Collections.emptyList());
        }

        @Override
        @OnThread(Tag.Simulation)
        public Transformation load(TableManager mgr, TableId tableId, String detail) throws InternalException, UserException
        {
            SortContext loaded = Utility.parseAsOne(detail, BasicLexer::new, SortParser::new, SortParser::sort);

            return new Sort(mgr, tableId, new TableId(loaded.srcTableId.getText()), Utility.<OrderByContext, ColumnId>mapList(loaded.orderBy(), o -> new ColumnId(o.column.getText())));
        }

        @Override
        public @OnThread(Tag.FXPlatform) TransformationEditor editNew(TableId srcTableId, @Nullable Table src)
        {
            return new Editor(null, srcTableId, src, Collections.emptyList());
        }
    }

    private static class Editor extends TransformationEditor
    {
        private final @Nullable TableId thisTableId;
        private final TableId srcTableId;
        private final @Nullable Table src;
        private final ObservableList<Optional<ColumnId>> sortBy;
        private final BooleanProperty ready = new SimpleBooleanProperty(false);

        @OnThread(Tag.FXPlatform)
        private Editor(@Nullable TableId thisTableId, TableId srcTableId, @Nullable Table src, List<ColumnId> sortBy)
        {
            this.thisTableId = thisTableId;
            this.srcTableId = srcTableId;
            this.src = src;
            this.sortBy = FXCollections.observableArrayList();
            // TODO handle case that src table is missing
            for (ColumnId c : sortBy)
            {
                this.sortBy.add(Optional.of(c));
            }
            this.sortBy.add(Optional.empty());
        }

        @Override
        public @OnThread(Tag.FX) StringExpression displayTitle()
        {
            return new ReadOnlyStringWrapper("Sort");
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

        @Override
        public @OnThread(Tag.FXPlatform) Pane getParameterDisplay(FXPlatformConsumer<Exception> reportError)
        {
            HBox colsAndSort = new HBox();
            ListView<ColumnId> columnListView = getColumnListView(src, srcTableId);
            columnListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            colsAndSort.getChildren().add(columnListView);

            VBox buttons = new VBox();
            Button button = new Button(">>");
            button.setOnAction(e ->
            {
                for (ColumnId column : columnListView.getSelectionModel().getSelectedItems())
                {
                    if (!sortBy.contains(Optional.of(column)))
                        sortBy.add(sortBy.size() - 1, Optional.of(column));
                }
            });
            buttons.getChildren().add(button);
            colsAndSort.getChildren().add(buttons);

            ListView<Optional<ColumnId>> sortByView = Utility.readOnlyListView(sortBy, c -> !c.isPresent() ? "Original order" : c.get() + ", then if equal, by");
            colsAndSort.getChildren().add(sortByView);
            return colsAndSort;
        }

        @Override
        public @OnThread(Tag.FXPlatform) BooleanExpression canPressOk()
        {
            return ready;
        }

        @Override
        public @OnThread(Tag.FXPlatform) SimulationSupplier<Transformation> getTransformation(TableManager mgr)
        {
            return () -> {
                if (src == null)
                    throw new InternalException("Null source for transformation");
                // Ignore the special empty column put in for GUI:
                List<ColumnId> presentSortBy = new ArrayList<>();
                for (Optional<ColumnId> c : sortBy)
                    if (c.isPresent())
                        presentSortBy.add(c.get());
                return new Sort(mgr, null, srcTableId, presentSortBy);
            };
        }
    }

    @Override
    protected @OnThread(Tag.FXPlatform) List<String> saveDetail(@Nullable File destination)
    {
        OutputBuilder b = new OutputBuilder();
        b.kw("SORT").id(srcTableId).nl();
        for (ColumnId c : originalSortBy)
            b.kw("ASCENDING").id(c).nl();
        return b.toLines();
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        Sort sort = (Sort) o;

        if (!srcTableId.equals(sort.srcTableId)) return false;
        return originalSortBy.equals(sort.originalSortBy);

    }

    @Override
    public int hashCode()
    {
        int result = super.hashCode();
        result = 31 * result + srcTableId.hashCode();
        result = 31 * result + originalSortBy.hashCode();
        return result;
    }
}
