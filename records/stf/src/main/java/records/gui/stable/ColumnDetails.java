package records.gui.stable;

import com.google.common.collect.ImmutableList;
import javafx.scene.Node;
import org.checkerframework.checker.i18n.qual.Localized;
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
    // What to actually put on screen; usually same as columnId.getRaw()
    private final @Localized String displayHeaderLabel; 
    private final DataType columnType;
    private final @Nullable FXPlatformConsumer<ColumnId> renameColumn;

    public ColumnDetails(ColumnId columnId, DataType columnType, @Nullable FXPlatformConsumer<ColumnId> renameColumn, ColumnHandler columnHandler)
    {
        this(columnId, columnId.getRaw(), columnType, renameColumn, columnHandler);
    }

    private ColumnDetails(ColumnId columnId, @Localized String displayHeaderLabel, DataType columnType, @Nullable FXPlatformConsumer<ColumnId> renameColumn, ColumnHandler columnHandler)
    {
        this.columnId = columnId;
        this.displayHeaderLabel = displayHeaderLabel;
        this.columnType = columnType;
        this.renameColumn = renameColumn;
        this.columnHandler = columnHandler;
    }
    
    public ColumnDetails withDisplayHeaderLabel(@Localized String displayHeaderLabel)
    {
        return new ColumnDetails(columnId, displayHeaderLabel, columnType, renameColumn, columnHandler);
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

    public @Localized String getDisplayHeaderLabel()
    {
        return displayHeaderLabel;
    }
}
