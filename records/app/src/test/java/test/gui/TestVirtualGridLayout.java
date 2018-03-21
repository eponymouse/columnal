package test.gui;

import annotation.units.GridAreaRowIndex;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multiset;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Point2D;
import javafx.geometry.VerticalDirection;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import log.Log;
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
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
        TestUtil.fx_(() -> {
            SimpleCellSupplier simpleCellSupplier = new SimpleCellSupplier();
            SimpleGridArea simpleGridArea = new SimpleGridArea();
            virtualGrid.addGridAreas(ImmutableList.of(simpleGridArea));
            simpleCellSupplier.addGrid(simpleGridArea, simpleGridArea);
            virtualGrid.addNodeSupplier(simpleCellSupplier);
            virtualGrid.addNodeSupplier(new VirtualGridLineSupplier());
        });
        dummySupplier.layoutCount = 0;
        moveTo(0, 0);
        moveBy(500, 500);
        assertEquals(0, dummySupplier.layoutCount);
    }

    @Test
    public void testCellsScroll()
    {
        TestUtil.fx_(() -> {
            SimpleCellSupplier simpleCellSupplier = new SimpleCellSupplier();
            SimpleGridArea simpleGridArea = new SimpleGridArea();
            virtualGrid.addGridAreas(ImmutableList.of(simpleGridArea));
            simpleCellSupplier.addGrid(simpleGridArea, simpleGridArea);
            virtualGrid.addNodeSupplier(simpleCellSupplier);
            virtualGrid.addNodeSupplier(new VirtualGridLineSupplier());
        });
        for (int scrollType = 0; scrollType < 3; scrollType++)
        {
            // First loop: Y, second: X, third: X & Y
            double xf = scrollType >= 1 ? 1 : 0;
            double yf = scrollType != 1 ? 1 : 0;
            dummySupplier.layoutCount = 0;
            // For small scroll, shouldn't need any new layout
            // as should just be handled by translation
            TestUtil.fx_(() -> {
                virtualGrid.getScrollGroup().requestScrollBy(-1.0 * xf, -1.0 * yf);
                virtualGrid.getScrollGroup().requestScrollBy(1.0 * xf, 1.0 * yf);
            });
            // Wait for smooth scrolling to finish:
            TestUtil.sleep(500);
            assertEquals("ST " + scrollType, 0, dummySupplier.layoutCount);
            // True even for medium scroll:
            TestUtil.fx_(() -> {
                virtualGrid.getScrollGroup().requestScrollBy(-100.0 * xf, -100.0 * yf);
                virtualGrid.getScrollGroup().requestScrollBy(100.0 * xf, 100.0 * yf);
            });
            // Wait for smooth scrolling to finish:
            TestUtil.sleep(500);
            assertEquals("ST " + scrollType, 0, dummySupplier.layoutCount);

            // However, a large scroll will require a layout -- but should only need two (one up, one down):
            TestUtil.fx_(() -> {
                virtualGrid.getScrollGroup().requestScrollBy(-1000.0 * xf, -1000.0 * yf);
                virtualGrid.getScrollGroup().requestScrollBy(1000.0 * xf, 1000.0 * yf);
            });
            // Wait for smooth scrolling to finish:
            TestUtil.sleep(500);
            // This should be 2 scrolls even for X and Y together:
            assertEquals("ST " + scrollType, 2, dummySupplier.layoutCount);
        }
        
        //#error TODO checking that cells are not loaded/reallocated more than needed.
    }
    
    @Test
    public void testCellAllocation()
    {
        SimpleGridArea simpleGridArea = new SimpleGridArea();
        assertTrue(simpleGridArea.fetches.isEmpty());
        TestUtil.fx_(() -> {
            SimpleCellSupplier simpleCellSupplier = new SimpleCellSupplier();
            virtualGrid.addGridAreas(ImmutableList.of(simpleGridArea));
            simpleCellSupplier.addGrid(simpleGridArea, simpleGridArea);
            virtualGrid.addNodeSupplier(simpleCellSupplier);
            virtualGrid.addNodeSupplier(new VirtualGridLineSupplier());
        });
        // Small scroll and back shouldn't fetch:
        assertEquals(0, (long)TestUtil.<Integer>fx(() -> {
            simpleGridArea.fetches.clear();
            virtualGrid.getScrollGroup().requestScrollBy(-100.0, -100.0);
            virtualGrid.getScrollGroup().requestScrollBy(100.0, 100.0);
            return simpleGridArea.fetches.size();
        }));
        // Scrolling only downwards should not re-fetch any cells:
        assertEquals(1, (long)TestUtil.<Integer>fx(() -> {
            virtualGrid.getScrollGroup().requestScrollBy(-1000.0, -1000.0);
            return simpleGridArea.fetches.entrySet().stream().mapToInt(e -> e.getCount()).max().orElse(0);
        }));
        // Scrolling back should still not re-fetch:
        assertEquals(1, (long)TestUtil.<Integer>fx(() -> {
            virtualGrid.getScrollGroup().requestScrollBy(1000.0, 1000.0);
            System.err.println(simpleGridArea.fetches.entrySet().stream().filter(e -> e.getCount() > 1).map(e -> e.toString()).collect(Collectors.joining("\n")));
            return simpleGridArea.fetches.entrySet().stream().mapToInt(e -> e.getCount()).max().orElse(0);
        }));
    }
    
    private static class DummySupplier extends VirtualGridSupplier<Label>
    {
        // Leave it negative while starting up:
        private int layoutCount = -100;

        @Override
        protected void layoutItems(ContainerChildren containerChildren, VisibleBounds visibleBounds)
        {
            layoutCount += 1;
            if (layoutCount > 0)
                Log.logStackTrace("");
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
        Multiset<GridAreaCellPosition> fetches = HashMultiset.create();
        
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
            fetches.add(cellPosition);
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
