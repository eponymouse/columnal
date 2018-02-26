package records.gui.stable;

import com.google.common.collect.ImmutableList;
import javafx.scene.Node;
import records.data.ColumnId;
import records.data.datatype.DataType;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.gui.GUI;

public class ColumnDetails
{
    private final ColumnHandler columnHandler;
    private final ColumnId columnId;
    private final DataType columnType;

    public ColumnDetails(ColumnId columnId, DataType columnType, ColumnHandler columnHandler)
    {
        this.columnId = columnId;
        this.columnType = columnType;
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
}
