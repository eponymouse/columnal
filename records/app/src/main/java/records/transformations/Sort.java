package records.transformations;

import annotation.qual.Value;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.CellPosition;
import records.data.Column;
import records.data.Column.ProgressListener;
import records.data.ColumnId;
import records.data.NumericColumnStorage;
import records.data.RecordSet;
import records.data.TableAndColumnRenames;
import records.data.Table;
import records.data.TableId;
import records.data.TableManager;
import records.data.Transformation;
import records.data.datatype.NumberInfo;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.TransformationLexer;
import records.grammar.TransformationParser;
import records.grammar.TransformationParser.OrderByContext;
import records.grammar.TransformationParser.SortContext;
import records.gui.SingleSourceControl;
import records.gui.View;
import records.loadsave.OutputBuilder;
import styled.StyledShowable;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExFunction;
import utility.FXPlatformConsumer;
import utility.FXPlatformSupplier;
import utility.Pair;
import utility.SimulationSupplier;
import utility.Utility;
import utility.gui.FXUtility;
import utility.gui.FXUtility.DragHandler;
import utility.gui.GUI;
import utility.gui.TranslationUtility;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    public static enum Direction implements StyledShowable
    {
        ASCENDING, DESCENDING;

        // This is for preview in description, so we just give an arrow:
        @Override
        public StyledString toStyledString()
        {
            return StyledString.s(this == ASCENDING ? "\u2191" : "\u2193");
        }
    }

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
    private final @NonNull ImmutableList<Pair<ColumnId, Direction>> originalSortBy;
    private final @Nullable ImmutableList<Pair<Column, Direction>> sortBy;

    public Sort(TableManager mgr, InitialLoadDetails initialLoadDetails, TableId srcTableId, ImmutableList<Pair<ColumnId, Direction>> sortBy) throws InternalException
    {
        super(mgr, initialLoadDetails);
        this.srcTableId = srcTableId;
        this.src = mgr.getSingleTableOrNull(srcTableId);
        this.originalSortBy = sortBy;
        this.sortByError = "Unknown error with table \"" + getId() + "\"";
        if (this.src == null)
        {
            this.result = null;
            this.sortBy = null;
            this.sortMap = null;
            sortByError = "Could not find source table: \"" + srcTableId + "\"";
            return;
        }
        @Nullable RecordSet theResult = null;
        @Nullable List<Pair<Column, Direction>> theSortBy = null;
        try
        {
            List<Pair<Column, Direction>> sortByColumns = new ArrayList<>();
            for (Pair<ColumnId, Direction> c : originalSortBy)
            {
                @Nullable Column column = this.src.getData().getColumn(c.getFirst());
                if (column == null)
                {
                    sortByColumns = null;
                    this.sortByError = "Could not find source column to sort by: \"" + c + "\"";
                    break;
                }
                sortByColumns.add(new Pair<>(column, c.getSecond()));
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

                    @Override
                    public boolean isAltered()
                    {
                        return true;
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
                public @TableDataRowIndex int getLength() throws UserException, InternalException
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
        this.sortBy = theSortBy != null ? ImmutableList.copyOf(theSortBy) : null;
    }

    private void fillSortMapTo(int target, @Nullable ProgressListener prog) throws InternalException, UserException
    {
        if (sortMap == null)
            throw new InternalException("Trying to fill null sort map; error in initialisation carried forward.");
        int destStart = sortMap.filled();
        Direction[] justDirections = originalSortBy.stream().map(p -> p.getSecond()).toArray(Direction[]::new);
        for (int dest = destStart; dest <= target; dest++)
        {
            int lowestIndex = 0;
            @Value Object @Nullable [] lowest = null;
            int pointerToLowestIndex = -1;
            int prevSrc = 0;
            if (stillToOrder == null)
                throw new InternalException("Trying to re-sort an already sorted list");
            // stillToOrder shouldn't possibly become null during this loop, but need to satisfy checker:
            for (int src = stillToOrder[prevSrc]; src != -1; src = stillToOrder == null ? -1 : stillToOrder[src])
            {
                // src is in stillToOrder terms, which is one more than original indexes
                @Value Object[] cur = getItem(src - 1);
                if (lowest == null || compareFixedSet(cur, lowest, justDirections) < 0)
                {
                    lowest = cur;
                    lowestIndex = src;
                    pointerToLowestIndex = prevSrc;
                }
                prevSrc = src;
            }
            if (lowest != null && stillToOrder != null)
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

    // All arrays must be same length.
    // Returns -1 if a is before b, given the directions.
    // e.g. compareFixedSet({0}, {1}, DESCENDING) will return positive number, because 0 is after 1 when descending
    private static int compareFixedSet(@Value Object[] a, @Value Object[] b, Direction[] justDirections) throws UserException, InternalException
    {
        for (int i = 0; i < a.length; i++)
        {
            int cmp = Utility.compareValues(a[i], b[i]);
            if (justDirections[i] == Direction.DESCENDING)
                cmp = -cmp;
            if (cmp != 0)
                return cmp;
        }
        return 0;
    }

    @Pure
    private @Value Object[] getItem(int srcIndex) throws UserException, InternalException
    {
        if (sortBy == null)
            throw new UserException(sortByError);
        @Value Object[] r = new @Value Object[sortBy.size()];
        for (int i = 0; i < sortBy.size(); i++)
        {
            Pair<Column, Direction> c = sortBy.get(i);
            r[i] = c.getFirst().getType().getCollapsed(srcIndex);
        }
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
    protected @OnThread(Tag.Any) String getTransformationName()
    {
        return NAME;
    }

    @OnThread(Tag.Any)
    public TableId getSource()
    {
        return srcTableId;
    }
    
    @Override
    @OnThread(Tag.Any)
    public Stream<TableId> getPrimarySources()
    {
        return Stream.of(srcTableId);
    }

    @Override
    protected @OnThread(Tag.Any) Stream<TableId> getSourcesFromExpressions()
    {
        return Stream.empty();
    }

    @OnThread(Tag.FXPlatform)
    public static class Info extends SingleSourceTransformationInfo
    {
        @OnThread(Tag.Any)
        public Info()
        {
            super(NAME, "Sort rows", "preview-sort.png", "sort.explanation.short",Collections.emptyList());
        }

        @Override
        @OnThread(Tag.Simulation)
        public Transformation loadSingle(TableManager mgr, InitialLoadDetails initialLoadDetails, TableId srcTableId, String detail) throws InternalException, UserException
        {
            SortContext loaded = Utility.parseAsOne(detail, TransformationLexer::new, TransformationParser::new, TransformationParser::sort);

            return new Sort(mgr, initialLoadDetails, srcTableId, Utility.<OrderByContext, Pair<ColumnId, Direction>>mapListI(loaded.orderBy(), o -> new Pair<>(new ColumnId(o.column.getText()), o.orderKW().getText().equals("DESCENDING") ? Direction.DESCENDING : Direction.ASCENDING)));
        }

        @Override
        @OnThread(Tag.Simulation)
        public Transformation makeWithSource(View view, TableManager mgr, CellPosition destination, Table srcTable) throws InternalException
        {
            return new Sort(mgr, new InitialLoadDetails(null, destination, new Pair<>(Display.ALL, ImmutableList.of())), srcTable.getId(), ImmutableList.of());
        }
    }

    @Override
    protected @OnThread(Tag.Any) List<String> saveDetail(@Nullable File destination, TableAndColumnRenames renames)
    {
        OutputBuilder b = new OutputBuilder();
        for (Pair<ColumnId, Direction> c : originalSortBy)
            b.kw(c.getSecond().toString()).id(renames.columnId(srcTableId, c.getFirst())).nl();
        return b.toLines();
    }

    @OnThread(Tag.Any)
    public ImmutableList<Pair<ColumnId, Direction>> getSortBy()
    {
        return originalSortBy;
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
