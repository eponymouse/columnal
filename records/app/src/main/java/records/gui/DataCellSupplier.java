package records.gui;

import records.data.CellPosition;
import records.gui.grid.VirtualGridSupplier;
import records.gui.grid.VirtualGridSupplierIndividual;
import records.gui.stf.StructuredTextField;
import utility.gui.FXUtility;

public class DataCellSupplier extends VirtualGridSupplierIndividual<StructuredTextField>
{
    @Override
    protected StructuredTextField makeNewItem()
    {
        StructuredTextField stf = new StructuredTextField();
        stf.getStyleClass().add("virt-grid-cell");
        return stf;
    }
}
