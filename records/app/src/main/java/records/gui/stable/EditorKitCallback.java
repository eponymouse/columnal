package records.gui.stable;

import records.gui.stf.StructuredTextField.EditorKit;
import threadchecker.OnThread;
import threadchecker.Tag;

public interface EditorKitCallback
{
    @OnThread(Tag.FXPlatform)
    public void loadedValue(int rowIndex, int colIndex, EditorKit<?> editorKit);
}
