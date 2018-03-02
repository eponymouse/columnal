package records.gui;

import javafx.scene.Node;
import javafx.scene.effect.BoxBlur;
import javafx.scene.effect.Effect;
import javafx.scene.effect.GaussianBlur;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.DataCellSupplier.CellStyle;
import records.gui.grid.VirtualGridSupplierIndividual;
import records.gui.stf.StructuredTextField;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.gui.FXUtility;

import java.util.Arrays;

public class DataCellSupplier extends VirtualGridSupplierIndividual<StructuredTextField, CellStyle>
{
    public DataCellSupplier()
    {
        super(ViewOrder.STANDARD, Arrays.asList(CellStyle.values()));
    }
    
    @Override
    protected StructuredTextField makeNewItem()
    {
        StructuredTextField stf = new StructuredTextField();
        stf.getStyleClass().add("table-data-cell");
        return stf;
    }

    public static enum CellStyle
    {
        TABLE_DRAG_SOURCE
        {
            @Override
            public void applyStyle(Node item, boolean on)
            {
                item.setEffect(on ? new GaussianBlur() : null);
            }
        },
        HOVERING_EXPAND_DOWN
        {
            @Override
            public void applyStyle(Node item, boolean on)
            {
                FXUtility.setPseudoclass(item, "hovering-expand-down", on);
            }
        },
        HOVERING_EXPAND_RIGHT
        {
            @Override
            public void applyStyle(Node item, boolean on)
            {
                FXUtility.setPseudoclass(item, "hovering-expand-right", on);
            }
        };

        @OnThread(Tag.FX)
        public abstract void applyStyle(Node item, boolean on);
    }

    @Override
    protected @OnThread(Tag.FX) void adjustStyle(StructuredTextField item, CellStyle style, boolean on)
    {
        style.applyStyle(item, on);
    }
}
