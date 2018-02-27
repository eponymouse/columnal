package records.gui.stable;

import com.google.common.collect.ImmutableList;
import javafx.scene.Node;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.ColumnId;
import records.data.datatype.DataType;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.gui.GUI;

public class ColumnDetails
{
    private final ColumnHandler columnHandler;
    private final ColumnId columnId;
    private final DataType columnType;
    private final @Nullable FXPlatformConsumer<ColumnId> renameColumn;

    public ColumnDetails(ColumnId columnId, DataType columnType, @Nullable FXPlatformConsumer<ColumnId> renameColumn, ColumnHandler columnHandler)
    {
        this.columnId = columnId;
        this.columnType = columnType;
        this.renameColumn = renameColumn;
        this.columnHandler = columnHandler;
    }

    @OnThread(Tag.FXPlatform)
    protected ImmutableList<Node> makeHeaderContent()
    {
        return ImmutableList.of(
            GUI.labelRaw(columnId.getRaw(), "stable-view-column-title")
        );
    }

    public final ColumnId getColumnId()
    {
        return columnId;
    }

    public DataType getColumnType()
    {
        return columnType;
    }

    public final ColumnHandler getColumnHandler()
    {
        return columnHandler;
    }

    public @Nullable FXPlatformConsumer<ColumnId> getRenameColumn()
    {
        return renameColumn;
    }
}
