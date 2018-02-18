package records.gui;

import javafx.scene.control.Button;
import log.Log;
import records.data.CellPosition;
import records.gui.grid.VirtualGridSupplierIndividual;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformSupplier;

@OnThread(Tag.FXPlatform)
public class ExpandTableArrowSupplier extends VirtualGridSupplierIndividual<Button>
{
    @Override
    protected Button makeNewItem()
    {
        Button button = new Button();
        // By default buttons have quite constrained sizes.  Let them take on any size that the grid specifies:
        button.setMinWidth(0.0);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setMinHeight(0.0);
        button.setMaxHeight(Double.MAX_VALUE);
        return button;
    }
    
    public void addTable(TableDisplay tableDisplay)
    {
        super.addGrid(tableDisplay.getGridArea(), new GridCellInfo<Button>()
        {
            @Override
            public boolean hasCellAt(CellPosition cellPosition)
            {
                // Work out if the cell is either just to the right, or just below:
                return hasAddColumnArrow(cellPosition) || hasAddRowArrow(cellPosition);
            }

            @OnThread(Tag.FXPlatform)
            private boolean hasAddRowArrow(CellPosition cellPosition)
            {
                return tableDisplay.getTable().getOperations().appendRows != null
                    && cellPosition.rowIndex == tableDisplay.getLastDataDisplayRowIncl() + 1
                    && cellPosition.columnIndex >= tableDisplay.getFirstDataDisplayColumnIncl()
                    && cellPosition.columnIndex <= tableDisplay.getLastDataDisplayColumnIncl();
            }

            @OnThread(Tag.FXPlatform)
            public boolean hasAddColumnArrow(CellPosition cellPosition)
            {
                int firstRow = tableDisplay.getFirstDataDisplayRowIncl();
                int lastRow = tableDisplay.getLastDataDisplayRowIncl();
                return tableDisplay.getTable().getOperations().appendColumn != null
                    && cellPosition.columnIndex == tableDisplay.getLastDataDisplayColumnIncl() + 1
                    && ((cellPosition.rowIndex >= firstRow && cellPosition.rowIndex <= lastRow)
                        || (lastRow < firstRow && cellPosition.rowIndex == firstRow));
            }

            @Override
            public void useCellFor(Button item, CellPosition cellPosition, FXPlatformSupplier<Boolean> samePositionCheck)
            {
                if (hasAddColumnArrow(cellPosition))
                {
                    item.setVisible(true);
                    item.setText("->");
                    // TODO set action
                }
                else if (hasAddRowArrow(cellPosition))
                {
                    item.setVisible(true);
                    item.setText("v");
                }
                else
                {
                    Log.error("Table arrow button found but not for column or for row! "
                        + "Position: " + cellPosition
                        + "Rows: " + tableDisplay.getFirstDataDisplayRowIncl() + " to " + tableDisplay.getLastDataDisplayRowIncl()
                        + "Columns: " + tableDisplay.getFirstDataDisplayColumnIncl() + " to " + tableDisplay.getLastDataDisplayColumnIncl());
                    // Panic -- hide it:
                    item.setVisible(false);
                }
            }
        });
    }
    
}
