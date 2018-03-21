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
import javafx.scene.shape.Line;
import javafx.stage.Stage;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.controlsfx.control.spreadsheet.Grid;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
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
    public static final int WINDOW_WIDTH = 810;
    public static final int WINDOW_HEIGHT = 600;
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
        stage.setWidth(WINDOW_WIDTH);
        stage.setHeight(WINDOW_HEIGHT);
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
        // Scrolling back should still not re-fetch any -- items should either have been fetched during first
        // scroll, or during second, but not both (because they should just stay fetched):
        assertEquals(1, (long)TestUtil.<Integer>fx(() -> {
            virtualGrid.getScrollGroup().requestScrollBy(1000.0, 1000.0);
            System.err.println(simpleGridArea.fetches.entrySet().stream().filter(e -> e.getCount() > 1).map(e -> e.toString()).collect(Collectors.joining("\n")));
            return simpleGridArea.fetches.entrySet().stream().mapToInt(e -> e.getCount()).max().orElse(0);
        }));
    }
    
    @Test
    public void testGridLines()
    {
        SimpleGridArea simpleGridArea = new SimpleGridArea();
        VirtualGridLineSupplier gridLineSupplier = new VirtualGridLineSupplier();
        TestUtil.fx_(() -> {
            virtualGrid.addGridAreas(ImmutableList.of(simpleGridArea));
            virtualGrid.addNodeSupplier(gridLineSupplier);
        });
        
        // Scroll slowly sideways, and check that the lines are always valid:
        double curScrollOffset = 0;
        for (int i = 0; i < 100; i++)
        {
            int expectedLines = (int)Math.ceil(WINDOW_WIDTH / 100.0);
            final int SCROLL_AMOUNT = 29;
            
            TestUtil.fx_(() -> virtualGrid.getScrollGroup().requestScrollBy(-SCROLL_AMOUNT, 0.0));
            curScrollOffset += SCROLL_AMOUNT;
            // Don't let them all turn into one big smooth scroll:
            if (i % 20 == 0)
                TestUtil.sleep(500);

            Collection<Line> columnDividers = TestUtil.fx(() -> gridLineSupplier._test_getColumnDividers());
            MatcherAssert.assertThat(columnDividers.size(), Matchers.greaterThanOrEqualTo(expectedLines));
            for (Line columnDivider : columnDividers)
            {
                assertEquals(0.0, (columnDivider.getLayoutX() + 0.5 + curScrollOffset) % 100, 0.01);
            }
            // Should all be different X:
            assertEquals(columnDividers.size(), columnDividers.stream().mapToDouble(l -> l.getLayoutX()).distinct().count());
        }

        // Same for Y:
        curScrollOffset = 0;
        for (int i = 0; i < 100; i++)
        {
            int expectedLines = (int)Math.ceil(WINDOW_HEIGHT / 24.0);
            final int SCROLL_AMOUNT = 7;

            TestUtil.fx_(() -> virtualGrid.getScrollGroup().requestScrollBy(0.0, -SCROLL_AMOUNT));
            curScrollOffset += SCROLL_AMOUNT;
            // Don't let them all turn into one big smooth scroll:
            if (i % 20 == 0)
                TestUtil.sleep(500);

            Collection<Line> rowDividers = TestUtil.fx(() -> gridLineSupplier._test_getRowDividers());
            MatcherAssert.assertThat(rowDividers.size(), Matchers.greaterThanOrEqualTo(expectedLines));
            for (Line rowDivider : rowDividers)
            {
                assertEquals(0.0, (rowDivider.getLayoutY() + 0.5 + curScrollOffset) % 24, 0.01);
            }
            // Should all be different X:
            assertEquals(rowDividers.size(), rowDividers.stream().mapToDouble(l -> l.getLayoutY()).distinct().count());
        }
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
        
        public SimpleGridArea()
        {
            setPosition(CellPosition.ORIGIN);
        }
        
        @Override
        protected @OnThread(Tag.FXPlatform) void updateKnownRows(@GridAreaRowIndex int checkUpToRowIncl, FXPlatformRunnable updateSizeAndPositions)
        {
            
        }

        @Override
        protected CellPosition recalculateBottomRightIncl()
        {
            return new CellPosition(CellPosition.row(400), CellPosition.col(1000));
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
        public @Nullable GridAreaCellPosition cellAt(CellPosition cellPosition)
        {
            if (cellPosition.rowIndex <= 400 && cellPosition.columnIndex <= 1000)
                return GridAreaCellPosition.relativeFrom(cellPosition, getPosition());
            else
                return null;
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
