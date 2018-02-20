package records.gui.stable;

import com.google.common.collect.ImmutableList;
import javafx.scene.Node;
import records.data.ColumnId;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.gui.GUI;

public class ColumnDetails
{
    private final ColumnHandler columnHandler;
    private final ColumnId columnId;

    public ColumnDetails(ColumnId columnId, ColumnHandler columnHandler)
    {
        this.columnId = columnId;
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
    
    public final ColumnHandler getColumnHandler()
    {
        return columnHandler;
    }
}
