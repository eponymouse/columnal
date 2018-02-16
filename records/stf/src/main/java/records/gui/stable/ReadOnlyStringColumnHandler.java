package records.gui.stable;

import records.data.Column;
import records.data.ColumnId;
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
    public void fetchValue(int rowIndex, FXPlatformConsumer<Boolean> focusListener, FXPlatformConsumer<CellPosition> relinquishFocus, EditorKitCallback setCellContent)
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
    public @OnThread(Tag.FXPlatform) void modifiedDataItems(int startRowIncl, int endRowIncl)
    {
    }

    @Override
    public @OnThread(Tag.FXPlatform) void removedAddedRows(int startRowIncl, int removedRowsCount, int addedRowsCount)
    {
    }

    @Override
    public @OnThread(Tag.FXPlatform) void addedColumn(Column newColumn)
    {
    }

    @Override
    public @OnThread(Tag.FXPlatform) void removedColumn(ColumnId oldColumnId)
    {
    }

    @Override
    public boolean isEditable()
    {
        return false;
    }
}
