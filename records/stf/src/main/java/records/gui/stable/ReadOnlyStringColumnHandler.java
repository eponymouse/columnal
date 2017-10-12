package records.gui.stable;

import records.gui.stable.StableView.ColumnHandler;
import records.gui.stable.VirtScrollStrTextGrid.CellPosition;
import records.gui.stf.EditorKitSimpleLabel;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.FXPlatformRunnable;

public abstract class ReadOnlyStringColumnHandler implements ColumnHandler
{
    private final int columnIndex;

    public ReadOnlyStringColumnHandler(int columnIndex)
    {
        this.columnIndex = columnIndex;
    }

    @Override
    public void fetchValue(int rowIndex, FXPlatformConsumer<Boolean> focusListener, FXPlatformConsumer<CellPosition> relinquishFocus, EditorKitCallback setCellContent, int firstVisibleRowIndexIncl, int lastVisibleRowIndexIncl)
    {
        fetchValueForRow(rowIndex, s -> setCellContent.loadedValue(rowIndex, columnIndex, new EditorKitSimpleLabel(s)));
    }

    @OnThread(Tag.FXPlatform)
    public abstract void fetchValueForRow(int rowIndex, FXPlatformConsumer<String> withValue);

    @Override
    public void columnResized(double width)
    {

    }

    @Override
    public boolean isEditable()
    {
        return false;
    }
}
