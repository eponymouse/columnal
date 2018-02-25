package records.gui.stable;

import annotation.units.TableColIndex;
import annotation.units.TableRowIndex;
import records.gui.stf.StructuredTextField.EditorKit;
import threadchecker.OnThread;
import threadchecker.Tag;

public interface EditorKitCallback
{
    @OnThread(Tag.FXPlatform)
    public void loadedValue(@TableRowIndex int rowIndex, @TableColIndex int colIndex, EditorKit<?> editorKit);
    
    public static class Test
    {
        int width;
        
        public Test(int height)
        {
            this.width = height;
        }
        
    }
}
