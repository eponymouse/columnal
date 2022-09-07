package xyz.columnal.data;

import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.ColumnId;
import xyz.columnal.data.Table;
import xyz.columnal.data.TableAndColumnRenames;
import xyz.columnal.data.TableId;
import xyz.columnal.utility.Pair;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public interface RenameOnEdit
{
    /*
    // Always rename to new name
    RENAME_TO_SUGGESTED,
    // Rename only if old name seems to have been auto-assigned
    IF_OLD_AUTO,
    // No renaming
    UNNEEDED;
    
    // Helper method that gives table id modification based on this enum 
    public TableAndColumnRenames fromTo(Table old, @Nullable Supplier<TableId> rename)
    {
        if (this == RENAME_TO_SUGGESTED)
            return rename == null ? TableAndColumnRenames.EMPTY : new TableAndColumnRenames(ImmutableMap.<TableId, Pair<@Nullable TableId, ImmutableMap<ColumnId, ColumnId>>>of(old.getId(), new Pair<TableId, ImmutableMap<ColumnId, ColumnId>>(rename.get(), ImmutableMap.<ColumnId, ColumnId>of())));
        else if (this == IF_OLD_AUTO)
            return rename == null || !old.getId().equals(rename.get()) ? TableAndColumnRenames.EMPTY : new TableAndColumnRenames(ImmutableMap.<TableId, Pair<@Nullable TableId, ImmutableMap<ColumnId, ColumnId>>>of(old.getId(), new Pair<TableId, ImmutableMap<ColumnId, ColumnId>>(rename.get(), ImmutableMap.<ColumnId, ColumnId>of())));
        else
            return TableAndColumnRenames.EMPTY;
    }

    // Helper method that gives table id modification based on this enum 
    public TableId fromTo(TableId old, @Nullable Supplier<TableId> rename)
    {
        if (this == RENAME_TO_SUGGESTED)
            return rename == null ? old : rename.get();
        else if (this == IF_OLD_AUTO)
            return rename == null || !old.equals(rename.get()) ? old : rename.get();
        else
            return old;
    }
     */
    
    public TableId rename(Table old);
    
    public static RenameOnEdit UNNEEDED = old -> old.getId();
    public static RenameOnEdit renameToSuggested(TableId newTableId)
    {
        return old -> newTableId;
    }
    public static RenameOnEdit ifOldAuto(TableId newTableId)
    {
        return old -> old.getId().equals(old.getSuggestedName()) ? newTableId : old.getId();
    }
}
