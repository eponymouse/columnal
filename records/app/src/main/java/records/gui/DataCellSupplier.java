package records.gui;

import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import com.google.common.collect.ImmutableList;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.effect.GaussianBlur;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import records.gui.DataCellSupplier.CellStyle;
import records.gui.DataCellSupplier.VersionedSTF;
import records.gui.dtf.Document;
import records.gui.dtf.DocumentTextField;
import records.gui.dtf.ReadOnlyDocument;
import records.gui.grid.VirtualGrid;
import records.gui.grid.VirtualGridSupplierIndividual;
import records.gui.grid.VirtualGridSupplierIndividual.GridCellInfo;
import records.gui.stable.ColumnDetails;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformRunnable;
import utility.gui.FXUtility;
import utility.gui.TranslationUtility;

import java.lang.ref.WeakReference;
import java.util.Arrays;

public class DataCellSupplier extends VirtualGridSupplierIndividual<VersionedSTF, CellStyle, GridCellInfo<VersionedSTF, CellStyle>>
{
    public DataCellSupplier()
    {
        super(Arrays.asList(CellStyle.values()));
    }
    
    @Override
    protected VersionedSTF makeNewItem(VirtualGrid virtualGrid)
    {
        VersionedSTF stf = new VersionedSTF(virtualGrid::positionOrAreaChanged);
        stf.getStyleClass().add("table-data-cell");
        return stf;
    }

    @Override
    protected ItemState getItemState(VersionedSTF stf, Point2D screenPos)
    {
        if (stf.isFocused())
            return ItemState.EDITING;
        else
            return ItemState.NOT_CLICKABLE;
    }

    @Override
    protected void keyboardActivate(CellPosition cellPosition)
    {
        startEditing(null, cellPosition);
    }

    @Override
    protected void startEditing(@Nullable Point2D screenPosition, CellPosition cellPosition)
    {
        @Nullable VersionedSTF stf = getItemAt(cellPosition);
        if (stf != null)
        {
            if (screenPosition != null)
            {
                Point2D localPos = stf.screenToLocal(screenPosition);
                stf.moveTo(localPos);
            }
            else
            {
                stf.home();
            }
            stf.requestFocus();
        }
    }
    
    public static enum CellStyle
    {
        TABLE_DRAG_SOURCE
        {
            @Override
            public void applyStyle(Node item, boolean on)
            {
                // Don't override unrelated effects:
                if (!on && item.getEffect() instanceof GaussianBlur)
                    item.setEffect(null);
                else if (on && item.getEffect() == null)
                    item.setEffect(new GaussianBlur());
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
    protected @OnThread(Tag.FX) void adjustStyle(VersionedSTF item, CellStyle style, boolean on)
    {
        style.applyStyle(item, on);
    }

    @Override
    protected void hideItem(VersionedSTF spareCell)
    {
        super.hideItem(spareCell);
        // Clear EditorKit to avoid keeping it around while spare:
        spareCell.blank(new ReadOnlyDocument(TranslationUtility.getString("data.loading")));
    }

    @OnThread(Tag.FXPlatform)
    public @Nullable VersionedSTF _test_getCellAt(CellPosition position)
    {
        return getItemAt(position);
    }

    @Override
    protected void sizeAndLocateCell(double x, double y, @AbsColIndex int columnIndex, @AbsRowIndex int rowIndex, VersionedSTF cell, VisibleBounds visibleBounds)
    {
        if (cell.isExpanded())
        {
            double width = 350; //visibleBounds.getXCoordAfter(columnIndex + CellPosition.col(1)) - x;
            double rowHeight = 110; // visibleBounds.getYCoordAfter(rowIndex + CellPosition.row(1)) - y;
            FXUtility.resizeRelocate(cell, x, y, width, rowHeight);
        }
        else
            super.sizeAndLocateCell(x, y, columnIndex, rowIndex, cell, visibleBounds);
    }

    @Override
    protected ViewOrder viewOrderFor(VersionedSTF node)
    {
        if (node.isExpanded())
            return ViewOrder.POPUP;
        else
            return super.viewOrderFor(node);
    }
    

    // A simple subclass of STF that holds a version param.  A version is a weak reference
    // to a list of column details
    public static class VersionedSTF extends DocumentTextField
    {
        @OnThread(Tag.FXPlatform)
        private @Nullable WeakReference<ImmutableList<ColumnDetails>> currentVersion = null;
        
        public VersionedSTF(FXPlatformRunnable redoLayout)
        {
            super(redoLayout);
            setFocusTraversable(false);
        }

        @OnThread(Tag.FXPlatform)
        public boolean isUsingColumns(ImmutableList<ColumnDetails> columns)
        {
            // Very important here we use reference equality not .equals()
            // It's not about the content of the columns, it's about using the reference to the
            // immutable list as a simple way of doing a version check
            return currentVersion != null && currentVersion.get() == columns;
        }

        @OnThread(Tag.FXPlatform)
        public void blank(Document editorKit)
        {
            super.setDocument(editorKit);
            currentVersion = null;
        }

        @OnThread(Tag.FXPlatform)
        public void setContent(Document editorKit, ImmutableList<ColumnDetails> columns)
        {
            super.setDocument(editorKit);
            currentVersion = new WeakReference<>(columns);
        }
    }
    
    public static void startPreload()
    {
        // All done in static initialiser, nothing else to do here.
    }
}
