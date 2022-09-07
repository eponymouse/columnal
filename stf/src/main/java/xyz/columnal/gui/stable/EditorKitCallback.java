package xyz.columnal.gui.stable;

import annotation.units.TableDataColIndex;
import annotation.units.TableDataRowIndex;
import xyz.columnal.gui.dtf.Document;
import threadchecker.OnThread;
import threadchecker.Tag;

public interface EditorKitCallback
{
    @OnThread(Tag.FXPlatform)
    public void loadedValue(@TableDataRowIndex int rowIndex, @TableDataColIndex int colIndex, Document document);
}
