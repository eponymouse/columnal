package records.gui.stable;

import annotation.units.TableDataColIndex;
import annotation.units.TableDataRowIndex;
import javafx.scene.input.KeyCode;
import records.data.CellPosition;
import records.data.Column;
import records.data.ColumnId;
import records.gui.dtf.ReadOnlyDocument;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformBiConsumer;
import utility.FXPlatformConsumer;

public abstract class ReadOnlyStringColumnHandler implements ColumnHandler
{
    private final @TableDataColIndex int columnIndex;

    public ReadOnlyStringColumnHandler(@TableDataColIndex int columnIndex)
    {
        this.columnIndex = columnIndex;
    }

    @Override
    public void fetchValue(@TableDataRowIndex int rowIndex, FXPlatformConsumer<Boolean> focusListener, FXPlatformBiConsumer<KeyCode, CellPosition> relinquishFocus, EditorKitCallback setCellContent)
    {
        fetchValueForRow(rowIndex, s -> setCellContent.loadedValue(rowIndex, columnIndex, new ReadOnlyDocument(s)));
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
