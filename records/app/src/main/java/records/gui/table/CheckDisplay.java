package records.gui.table;

import annotation.units.AbsColIndex;
import annotation.units.GridAreaRowIndex;
import annotation.units.TableDataColIndex;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import javafx.geometry.BoundingBox;
import javafx.geometry.Point2D;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Window;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import records.data.ColumnId;
import records.data.Table;
import records.data.Table.Display;
import records.data.Table.TableDisplayBase;
import records.data.TableId;
import records.data.TableManager;
import records.data.Transformation;
import records.gui.View;
import records.gui.grid.CellSelection;
import records.gui.grid.GridArea;
import records.gui.grid.RectangleBounds;
import records.gui.grid.VirtualGrid.ListenerOutcome;
import records.gui.grid.VirtualGrid.SelectionListener;
import records.gui.grid.VirtualGridSupplier;
import records.gui.grid.VirtualGridSupplier.ItemState;
import records.gui.grid.VirtualGridSupplier.ViewOrder;
import records.gui.grid.VirtualGridSupplier.VisibleBounds;
import records.gui.grid.VirtualGridSupplierFloating;
import records.gui.grid.VirtualGridSupplierFloating.FloatingItem;
import records.transformations.Check;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.FXPlatformRunnable;
import utility.Pair;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Display of a Check transformation.  Has two cells: a title
 * above a cell showing the result.
 */
public final class CheckDisplay extends HeadedDisplay implements TableDisplayBase, SelectionListener
{
    private final Check check;
    @OnThread(Tag.Any)
    private final AtomicReference<CellPosition> mostRecentBounds;
    private final TableHat tableHat;
    private final TableBorderOverlay tableBorderOverlay;
    private final FloatingItem<Label> resultFloatingItem;

    public CheckDisplay(View parent, VirtualGridSupplierFloating floatingSupplier, Check check)
    {
        super(new TableHeaderItemParams(parent.getManager(), check.getId(), check, floatingSupplier), floatingSupplier);
        this.check = check;
        mostRecentBounds = new AtomicReference<>(getPosition());
        
        this.resultFloatingItem = new FloatingItem<Label>(ViewOrder.STANDARD_CELLS) {

            @Override
            protected Optional<BoundingBox> calculatePosition(VisibleBounds visibleBounds)
            {
                CellPosition titlePos = getPosition();
                double left = visibleBounds.getXCoord(titlePos.columnIndex);
                double right = visibleBounds.getXCoordAfter(titlePos.columnIndex);
                double top = visibleBounds.getYCoord(titlePos.rowIndex + CellPosition.row(1));
                double bottom = visibleBounds.getYCoordAfter(titlePos.rowIndex + CellPosition.row(1));
                return Optional.of(new BoundingBox(left, top, right - left, bottom - top));
            }

            @Override
            protected Label makeCell(VisibleBounds visibleBounds)
            {
                return new Label("Result");
            }

            @Override
            public VirtualGridSupplier.@Nullable ItemState getItemState(CellPosition cellPosition, Point2D screenPos)
            {
                return getPosition().offsetByRowCols(1, 0).equals(cellPosition) ? ItemState.DIRECTLY_CLICKABLE : ItemState.NOT_CLICKABLE;
            }

            @Override
            public void keyboardActivate(CellPosition cellPosition)
            {

            }
        };
        floatingSupplier.addItem(resultFloatingItem);

        // Border overlay.  Note this makes use of calculations based on hat and row label border,
        // so it is important that we add this after them (since floating supplier iterates in order of addition):
        tableBorderOverlay = new TableBorderOverlay();
        floatingSupplier.addItem(tableBorderOverlay);

        @SuppressWarnings("initialization") // Don't understand why I need this
        @Initialized TableHat hat = new TableHat(this, parent, check);
        this.tableHat = hat;
        floatingSupplier.addItem(this.tableHat);

        // Must be done as last item:
        @Initialized CheckDisplay usInit = this;
        this.check.setDisplay(usInit);
    }

    @Override
    public void cleanupFloatingItems(VirtualGridSupplierFloating floating)
    {
        super.cleanupFloatingItems(floating);
        floating.removeItem(tableBorderOverlay);
        floating.removeItem(tableHat);
        floating.removeItem(resultFloatingItem);
        
    }

    @Override
    @SuppressWarnings("units")
    protected @TableDataRowIndex int getCurrentKnownRows()
    {
        return 1;
    }

    @Override
    protected CellPosition getDataPosition(@TableDataRowIndex int rowIndex, @TableDataColIndex int columnIndex)
    {
        return getPosition().offsetByRowCols(1 + rowIndex, columnIndex);
    }

    @Override
    protected @Nullable FXPlatformConsumer<TableId> renameTableOperation(Table table)
    {
        return null;
    }

    @Override
    public void gotoRow(Window parent, @AbsColIndex int column)
    {
        // Only one row...
    }

    @Override
    public void doCopy(@Nullable RectangleBounds bounds)
    {
        // Nothing really to copy
    }

    @Override
    protected boolean isShowingRowLabels()
    {
        return false;
    }

    @Override
    protected void setTableDragSource(boolean on, BorderPane tableNamePane)
    {
        // TODO
    }

    @Override
    public @OnThread(Tag.FXPlatform) void loadPosition(CellPosition position, Pair<Display, ImmutableList<ColumnId>> display)
    {
        mostRecentBounds.set(position);
        this.setPosition(position);
    }

    @Override
    public @OnThread(Tag.Any) CellPosition getMostRecentPosition()
    {
        return mostRecentBounds.get();
    }

    @Override
    protected @OnThread(Tag.FXPlatform) void updateKnownRows(@GridAreaRowIndex int checkUpToRowIncl, FXPlatformRunnable updateSizeAndPositions)
    {
        // Nothing to do
    }

    @Override
    protected CellPosition recalculateBottomRightIncl()
    {
        return getPosition().offsetByRowCols(1, 0);
    }

    @Override
    public @Nullable CellSelection getSelectionForSingleCell(CellPosition cellPosition)
    {
        // TODO
        return null;
    }

    @Override
    public String getSortKey()
    {
        return check.getId().getRaw();
    }

    @Override
    public @OnThread(Tag.FXPlatform) Pair<ListenerOutcome, @Nullable FXPlatformConsumer<VisibleBounds>> selectionChanged(@Nullable CellSelection oldSelection, @Nullable CellSelection newSelection)
    {
        return new Pair<>(ListenerOutcome.KEEP, null);
    }

    @Override
    public void setPosition(@UnknownInitialization(GridArea.class) CheckDisplay this, CellPosition cellPosition)
    {
        super.setPosition(cellPosition);
        if (mostRecentBounds != null)
            mostRecentBounds.set(cellPosition);
    }
}
