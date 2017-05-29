package records.data;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.List;
import java.util.Map;

/**
 * Created by neil on 29/05/2017.
 */
public class TableOperations
{
    public final @Nullable AppendRows appendRows;
    // Row index to insert at, count
    public final @Nullable InsertRows insertRows;
    // Row index to delete at (incl), count
    public final @Nullable DeleteRows deleteRows;

    @OnThread(Tag.Any)
    public TableOperations(@Nullable AppendRows appendRows, @Nullable InsertRows insertRows, @Nullable DeleteRows deleteRows)
    {
        this.appendRows = appendRows;
        this.insertRows = insertRows;
        this.deleteRows = deleteRows;
    }

    // Add rows at end
    @FunctionalInterface
    public static interface AppendRows
    {
        @OnThread(Tag.Simulation)
        public void appendRows(int count);
    }

    // Add rows in middle
    public static interface InsertRows
    {
        @OnThread(Tag.Simulation)
        public void insertRows(int rowIndex, int count);

        @OnThread(Tag.Simulation)
        public void pasteRows(int rowIndex, List<Map<ColumnId, String>> content);
    }

    public static interface DeleteRows
    {
        @OnThread(Tag.Simulation)
        public void deleteRows(int rowIndex, int count);
    }
}
