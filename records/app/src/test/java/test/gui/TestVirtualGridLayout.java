package test.gui;

import annotation.units.GridAreaRowIndex;
import com.google.common.collect.ImmutableList;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Point2D;
import javafx.geometry.VerticalDirection;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.controlsfx.control.spreadsheet.Grid;
import org.junit.Test;
import org.testfx.framework.junit.ApplicationTest;
import records.data.CellPosition;
import records.gui.grid.CellSelection;
import records.gui.grid.GridArea;
import records.gui.grid.GridAreaCellPosition;
import records.gui.grid.VirtualGrid;
import records.gui.grid.VirtualGridLineSupplier;
import records.gui.grid.VirtualGridSupplier;
import records.gui.grid.VirtualGridSupplierIndividual;
import records.gui.grid.VirtualGridSupplierIndividual.GridCellInfo;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformFunction;
import utility.FXPlatformRunnable;

import java.util.Collection;

import static org.junit.Assert.assertEquals;

public class TestVirtualGridLayout extends ApplicationTest
{
    @SuppressWarnings("nullness")
    private VirtualGrid virtualGrid;
    @SuppressWarnings("nullness")
    private DummySupplier dummySupplier;

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void start(Stage stage) throws Exception
    {
        dummySupplier = new DummySupplier();
        virtualGrid = new VirtualGrid(null, 0, 0);
        virtualGrid.addNodeSupplier(dummySupplier);
        stage.setScene(new Scene(new BorderPane(virtualGrid.getNode())));
        stage.setWidth(800);
        stage.setHeight(600);
        stage.show();
        TestUtil.sleep(500);
    }
    
    @Test
    public void testBlankNoLayout()
    {
        dummySupplier.layoutCount = 0;
        moveTo(0, 0);
        moveBy(500, 500);
        assertEquals(0, dummySupplier.layoutCount);
    }

    @Test
    public void testCellsNoLayout()
    {
        SimpleCellSupplier simpleCellSupplier = new SimpleCellSupplier();
        SimpleGridArea simpleGridArea = new SimpleGridArea();
        simpleCellSupplier.addGrid(simpleGridArea, simpleGridArea);
        virtualGrid.addNodeSupplier(simpleCellSupplier);
        virtualGrid.addNodeSupplier(new VirtualGridLineSupplier());
        dummySupplier.layoutCount = 0;
        moveTo(0, 0);
        moveBy(500, 500);
        assertEquals(0, dummySupplier.layoutCount);
    }

    @Test
    public void testCellsScroll()
    {
        SimpleCellSupplier simpleCellSupplier = new SimpleCellSupplier();
        SimpleGridArea simpleGridArea = new SimpleGridArea();
        simpleCellSupplier.addGrid(simpleGridArea, simpleGridArea);
        virtualGrid.addNodeSupplier(simpleCellSupplier);
        virtualGrid.addNodeSupplier(new VirtualGridLineSupplier());
        dummySupplier.layoutCount = 0;
        // For small scroll, shouldn't need any new layout
        // as should just be handled by translation
        scroll(1, VerticalDirection.DOWN);
        scroll(1, VerticalDirection.UP);
        // Wait for smooth scrolling to finish:
        TestUtil.sleep(500);
        assertEquals(0, dummySupplier.layoutCount);
    }
    
    private static class DummySupplier extends VirtualGridSupplier<Label>
    {
        private int layoutCount = 0;

        @Override
        protected void layoutItems(ContainerChildren containerChildren, VisibleBounds visibleBounds)
        {
            layoutCount += 1;
        }

        @Override
        protected @Nullable ItemState getItemState(CellPosition cellPosition, Point2D screenPosition)
        {
            return null;
        }
    }

    private static class SimpleCellSupplier extends VirtualGridSupplierIndividual<Label, String, GridCellInfo<Label, String>>
    {
        protected SimpleCellSupplier()
        {
            super(ViewOrder.STANDARD_CELLS, ImmutableList.of());
        }

        @Override
        protected Label makeNewItem()
        {
            return new Label("X");
        }

        @Override
        protected @OnThread(Tag.FX) void adjustStyle(Label item, String style, boolean on)
        {

        }

        @Override
        protected ItemState getItemState(Label item, Point2D screenPos)
        {
            return ItemState.NOT_CLICKABLE;
        }
    }

    private static class SimpleGridArea extends GridArea implements GridCellInfo<Label,String>
    {
        @Override
        protected @OnThread(Tag.FXPlatform) void updateKnownRows(@GridAreaRowIndex int checkUpToRowIncl, FXPlatformRunnable updateSizeAndPositions)
        {
            
        }

        @Override
        protected CellPosition recalculateBottomRightIncl()
        {
            return new CellPosition(CellPosition.row(400), CellPosition.col(100));
        }

        @Override
        public @Nullable CellSelection getSelectionForSingleCell(CellPosition cellPosition)
        {
            return null;
        }

        @Override
        public String getSortKey()
        {
            return "" + hashCode();
        }

        @Override
        public GridAreaCellPosition cellAt(CellPosition cellPosition)
        {
            return GridAreaCellPosition.relativeFrom(cellPosition, getPosition());
        }

        @Override
        public void fetchFor(GridAreaCellPosition cellPosition, FXPlatformFunction<CellPosition, @Nullable Label> getCell)
        {
            // TODO record number of fetches
        }

        @Override
        public ObjectExpression<? extends Collection<String>> styleForAllCells()
        {
            return new ReadOnlyObjectWrapper<>(ImmutableList.of());
        }

        @Override
        public boolean checkCellUpToDate(GridAreaCellPosition cellPosition, Label cellFirst)
        {
            return true;
        }
    }
}
