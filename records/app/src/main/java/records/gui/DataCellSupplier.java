package records.gui;

import javafx.scene.effect.BoxBlur;
import javafx.scene.effect.Effect;
import javafx.scene.effect.GaussianBlur;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.DataCellSupplier.CellStyle;
import records.gui.grid.VirtualGridSupplierIndividual;
import records.gui.stf.StructuredTextField;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Arrays;

public class DataCellSupplier extends VirtualGridSupplierIndividual<StructuredTextField, CellStyle>
{
    public DataCellSupplier()
    {
        super(Arrays.asList(new CellStyle()));
    }
    
    @Override
    protected StructuredTextField makeNewItem()
    {
        StructuredTextField stf = new StructuredTextField();
        stf.getStyleClass().add("table-data-cell");
        return stf;
    }

    public static class CellStyle
    {
        @Override
        public int hashCode()
        {
            return 0;
        }

        @Override
        public boolean equals(@Nullable Object obj)
        {
            return true;
        }

        public Effect getEffect()
        {
            return new GaussianBlur();
        }
    }

    @Override
    protected @OnThread(Tag.FX) void adjustStyle(StructuredTextField item, CellStyle style, boolean on)
    {
        item.setEffect(on ? style.getEffect() : null);
    }
}
