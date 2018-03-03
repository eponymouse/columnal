package records.gui.stable;

import annotation.units.TableDataColIndex;
import annotation.units.TableDataRowIndex;
import records.gui.stf.StructuredTextField.EditorKit;
import threadchecker.OnThread;
import threadchecker.Tag;

public interface EditorKitCallback
{
    @OnThread(Tag.FXPlatform)
    public void loadedValue(@TableDataRowIndex int rowIndex, @TableDataColIndex int colIndex, EditorKit<?> editorKit);
}
