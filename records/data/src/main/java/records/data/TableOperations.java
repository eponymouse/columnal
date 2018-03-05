package records.data;

import annotation.qual.Value;
import annotation.units.TableDataRowIndex;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.function.Function;

/**
 * Created by neil on 29/05/2017.
 */
public class TableOperations
{
    public final @Nullable RenameTable renameTable;
    // TODO have a sum type here for append which allows for a custom GUI operation
    // so that if it's e.g. a linked table, you can click add and get a prompt about converting.
    public final @Nullable AddColumn addColumn;
    public final Function<ColumnId, @Nullable RenameColumn> renameColumn;
    public final Function<ColumnId, @Nullable DeleteColumn> deleteColumn;
    public final @Nullable AppendRows appendRows;
    // Row index to insert at, count
    public final @Nullable InsertRows insertRows;
    // Row index to delete at (incl), count
    public final @Nullable DeleteRows deleteRows;

    @OnThread(Tag.Any)
    public TableOperations(@Nullable RenameTable renameTable, @Nullable AddColumn addColumn, Function<ColumnId, @Nullable RenameColumn> renameColumn, Function<ColumnId, @Nullable DeleteColumn> deleteColumn, @Nullable AppendRows appendRows, @Nullable InsertRows insertRows, @Nullable DeleteRows deleteRows)
    {
        this.renameTable = renameTable;
        this.addColumn = addColumn;
        this.renameColumn = renameColumn;
        this.deleteColumn = deleteColumn;
        this.appendRows = appendRows;
        this.insertRows = insertRows;
        this.deleteRows = deleteRows;
    }

    // Add column at end
    @FunctionalInterface
    public static interface AddColumn
    {
        // If before is null, add at end.
        @OnThread(Tag.Simulation)
        public void addColumn(@Nullable ColumnId before, @Nullable ColumnId newColumnName, DataType newColumnType, @Value Object defaultValue);
    }

    // Rename column (only available if this is source of the column)
    @FunctionalInterface
    public static interface RenameColumn
    {
        // Don't need old column name because that will have already been passed:
        @OnThread(Tag.Simulation)
        public void renameColumn(ColumnId newColumnName);
    }

    // Delete column (only available if this is source of the column)
    @FunctionalInterface
    public static interface DeleteColumn
    {
        @OnThread(Tag.Simulation)
        public void deleteColumn(ColumnId columnId);
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
        public void insertRows(@TableDataRowIndex int beforeRowIndex, int count);

        // TODO
        //@OnThread(Tag.Simulation)
        //public void pasteRows(int rowIndex, List<Map<ColumnId, String>> content);
    }

    public static interface DeleteRows
    {
        @OnThread(Tag.Simulation)
        public void deleteRows(@TableDataRowIndex int rowIndex, int count);
    }
    
    public static interface RenameTable
    {
        @OnThread(Tag.Simulation)
        public void renameTable(TableId newName);
    }
}
