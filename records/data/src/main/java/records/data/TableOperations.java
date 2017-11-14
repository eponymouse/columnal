package records.data;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformRunnable;

import java.util.List;
import java.util.Map;

/**
 * Created by neil on 29/05/2017.
 */
public class TableOperations
{
    // TODO have a sum type here which allows for a custom GUI operation
    // so that if it's e.g. a linked table, you can click add and get a prompt about converting.
    public final @Nullable AppendColumn appendColumn;
    public final @Nullable AppendRows appendRows;
    // Row index to insert at, count
    public final @Nullable InsertRows insertRows;
    // Row index to delete at (incl), count
    public final @Nullable DeleteRows deleteRows;

    @OnThread(Tag.Any)
    public TableOperations(@Nullable AppendColumn appendColumn, @Nullable AppendRows appendRows, @Nullable InsertRows insertRows, @Nullable DeleteRows deleteRows)
    {
        this.appendColumn = appendColumn;
        this.appendRows = appendRows;
        this.insertRows = insertRows;
        this.deleteRows = deleteRows;
    }

    // Add column at end
    @FunctionalInterface
    public static interface AppendColumn
    {
        @OnThread(Tag.Simulation)
        public void appendColumn(String newColumnName, DataType newColumnType, @Value Object defaultValue);
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

        // TODO
        //@OnThread(Tag.Simulation)
        //public void pasteRows(int rowIndex, List<Map<ColumnId, String>> content);
    }

    public static interface DeleteRows
    {
        @OnThread(Tag.Simulation)
        public void deleteRows(int rowIndex, int count);
    }
}
