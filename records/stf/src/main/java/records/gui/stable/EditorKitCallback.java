package records.gui.stable;

import annotation.units.TableDataColIndex;
import annotation.units.TableDataRowIndex;
import records.gui.flex.EditorKit;
import records.gui.flex.EditorKitInterface;
import records.gui.kit.Document;
import threadchecker.OnThread;
import threadchecker.Tag;

public interface EditorKitCallback
{
    @OnThread(Tag.FXPlatform)
    public void loadedValue(@TableDataRowIndex int rowIndex, @TableDataColIndex int colIndex, Document document);
}
