package records.gui;

import javafx.scene.control.Label;
import records.gui.grid.VirtualGridSupplierFloating;
import threadchecker.OnThread;
import threadchecker.Tag;

@OnThread(Tag.FXPlatform)
public class ColumnHeaderSupplier extends VirtualGridSupplierFloating<Label>
{
    @Override
    protected Label makeCell()
    {
        return new Label();
    }
}
