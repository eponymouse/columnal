package records.gui;

import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.effect.BoxBlur;
import javafx.scene.effect.Effect;
import javafx.scene.effect.GaussianBlur;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.fxmisc.richtext.CharacterHit;
import org.fxmisc.richtext.model.NavigationActions.SelectionPolicy;
import records.data.CellPosition;
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

    @Override
    protected ItemState getItemState(StructuredTextField stf)
    {
        if (stf.isFocused())
            return ItemState.EDITING;
        else
            return ItemState.NOT_CLICKABLE;
    }

    @Override
    protected void startEditing(Point2D screenPosition, CellPosition cellPosition)
    {
        @Nullable StructuredTextField stf = getItemAt(cellPosition);
        if (stf != null)
        {
            stf.requestFocus();
            Point2D localPos = stf.screenToLocal(screenPosition);
            CharacterHit hit = stf.hit(localPos.getX(), localPos.getY());
            stf.moveTo(hit.getInsertionIndex(), SelectionPolicy.CLEAR);
        }
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
