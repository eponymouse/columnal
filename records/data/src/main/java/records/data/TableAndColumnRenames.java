package records.data;

import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

/**
 * A class keeping track of any pending table and/or column renames.  Column renames
 * are stored with a reference to the table that they originate from.
 */
@OnThread(Tag.Any)
public class TableAndColumnRenames
{
    private final ImmutableMap<TableId, Pair<@Nullable TableId, ImmutableMap<ColumnId, ColumnId>>> renames;
    private final @Nullable TableId defaultTableId;

    private TableAndColumnRenames(@Nullable TableId defaultTableId, ImmutableMap<TableId, Pair<@Nullable TableId, ImmutableMap<ColumnId, ColumnId>>> renames)
    {
        this.defaultTableId = defaultTableId;
        this.renames = renames;
    }
    
    public TableAndColumnRenames(ImmutableMap<TableId, Pair<@Nullable TableId, ImmutableMap<ColumnId, ColumnId>>> renames)
    {
        this(null, renames);
    }

    public TableId tableId(TableId tableId)
    {
        Pair<@Nullable TableId, ImmutableMap<ColumnId, ColumnId>> info = renames.get(tableId);
        if (info != null && info.getFirst() != null)
            return info.getFirst();
        else
            return tableId;
    }

    // Note: pass the OLD TableId, not the new one.  If you pass null, the default is used (if set)
    public ColumnId columnId(@Nullable TableId oldTableId, ColumnId columnId)
    {
        Pair<@Nullable TableId, ImmutableMap<ColumnId, ColumnId>> info = renames.get(oldTableId != null ? oldTableId : defaultTableId);
        if (info != null)
            return info.getSecond().getOrDefault(columnId, columnId);
        else
            return columnId;
    }
    
    public static final TableAndColumnRenames EMPTY = new TableAndColumnRenames(ImmutableMap.of());

    public TableAndColumnRenames withDefaultTableId(TableId tableId)
    {
        return new TableAndColumnRenames(tableId, renames);
    }

    public boolean isRenamingTableId(TableId tableId)
    {
        Pair<@Nullable TableId, ImmutableMap<ColumnId, ColumnId>> info = renames.get(tableId);
        return info != null && info.getFirst() != null;
    }
}
