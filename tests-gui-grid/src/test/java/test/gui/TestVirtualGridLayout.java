/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package test.gui;

import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import annotation.units.GridAreaRowIndex;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multiset;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.shape.Line;
import javafx.stage.Stage;
import xyz.columnal.log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import xyz.columnal.data.CellPosition;
import xyz.columnal.gui.grid.CellSelection;
import xyz.columnal.gui.grid.GridArea;
import xyz.columnal.gui.grid.GridAreaCellPosition;
import xyz.columnal.gui.grid.RectangleBounds;
import xyz.columnal.gui.grid.VirtualGrid;
import xyz.columnal.gui.grid.VirtualGridLineSupplier;
import xyz.columnal.gui.grid.VirtualGridSupplier;
import xyz.columnal.gui.grid.VirtualGridSupplier.VisibleBounds;
import xyz.columnal.gui.grid.VirtualGridSupplierIndividual;
import xyz.columnal.gui.grid.VirtualGridSupplierIndividual.GridCellInfo;
import xyz.columnal.styled.StyledString;
import test.gen.GenRandom;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.function.fx.FXPlatformFunction;
import xyz.columnal.utility.function.fx.FXPlatformRunnable;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@RunWith(JUnitQuickcheck.class)
public class TestVirtualGridLayout extends FXApplicationTest
{
    public static final int WINDOW_WIDTH = 810;
    public static final int WINDOW_HEIGHT = 600;
    @SuppressWarnings("nullness")
    private VirtualGrid virtualGrid;
    @SuppressWarnings("nullness")
    private DummySupplier dummySupplier;

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void start(Stage _stage) throws Exception
    {
        super.start(_stage);
        Stage stage = windowToUse;
        dummySupplier = new DummySupplier();
        virtualGrid = new VirtualGrid(null, 0, 0);
        virtualGrid.addNodeSupplier(dummySupplier);
        stage.setScene(new Scene(new BorderPane(virtualGrid.getNode())));
        stage.setWidth(WINDOW_WIDTH);
        stage.setHeight(WINDOW_HEIGHT);
        stage.show();
        TFXUtil.sleep(500);
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
        TFXUtil.fx_(() -> {
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
        TFXUtil.fx_(() -> {
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
            TFXUtil.fx_(() -> {
                virtualGrid.getScrollGroup().requestScrollBy(-1.0 * xf, -1.0 * yf);
                virtualGrid.getScrollGroup().requestScrollBy(1.0 * xf, 1.0 * yf);
            });
            // Wait for smooth scrolling to finish:
            TFXUtil.sleep(500);
            assertEquals("ST " + scrollType, 0, dummySupplier.layoutCount);
            // True even for medium scroll:
            TFXUtil.fx_(() -> {
                virtualGrid.getScrollGroup().requestScrollBy(-100.0 * xf, -100.0 * yf);
                virtualGrid.getScrollGroup().requestScrollBy(100.0 * xf, 100.0 * yf);
            });
            // Wait for smooth scrolling to finish:
            TFXUtil.sleep(500);
            assertEquals("ST " + scrollType, 0, dummySupplier.layoutCount);

            // However, a large scroll will require a layout -- but should only need two (one up, one down):
            TFXUtil.fx_(() -> {
                virtualGrid.getScrollGroup().requestScrollBy(-1000.0 * xf, -1000.0 * yf);
                virtualGrid.getScrollGroup().requestScrollBy(1000.0 * xf, 1000.0 * yf);
            });
            // Wait for smooth scrolling to finish:
            TFXUtil.sleep(500);
            // This should be 2 scrolls even for X and Y together:
            assertEquals("ST " + scrollType, 2, dummySupplier.layoutCount);
        }
        
        //#error TODO checking that cells are not loaded/reallocated more than needed.
    }
    
    @Test
    public void testCellAllocation()
    {
        SimpleGridArea simpleGridArea = TFXUtil.fx(() -> new SimpleGridArea());
        assertTrue(simpleGridArea.fetches.isEmpty());
        TFXUtil.fx_(() -> {
            SimpleCellSupplier simpleCellSupplier = new SimpleCellSupplier();
            virtualGrid.addGridAreas(ImmutableList.of(simpleGridArea));
            simpleCellSupplier.addGrid(simpleGridArea, simpleGridArea);
            virtualGrid.addNodeSupplier(simpleCellSupplier);
            virtualGrid.addNodeSupplier(new VirtualGridLineSupplier());
        });
        // Small scroll and back shouldn't fetch:
        assertEquals(0, (long) TFXUtil.<Integer>fx(() -> {
            simpleGridArea.fetches.clear();
            virtualGrid.getScrollGroup().requestScrollBy(-100.0, -100.0);
            virtualGrid.getScrollGroup().requestScrollBy(100.0, 100.0);
            return simpleGridArea.fetches.size();
        }));
        // Scrolling only downwards should not re-fetch any cells:
        assertEquals(1, (long) TFXUtil.<Integer>fx(() -> {
            virtualGrid.getScrollGroup().requestScrollBy(-1000.0, -1000.0);
            return simpleGridArea.fetches.entrySet().stream().mapToInt(e -> e.getCount()).max().orElse(0);
        }));
        // Scrolling back should still not re-fetch any -- items should either have been fetched during first
        // scroll, or during second, but not both (because they should just stay fetched):
        assertEquals(1, (long) TFXUtil.<Integer>fx(() -> {
            virtualGrid.getScrollGroup().requestScrollBy(1000.0, 1000.0);
            System.err.println(simpleGridArea.fetches.entrySet().stream().filter(e -> e.getCount() > 1).map(e -> e.toString()).collect(Collectors.joining("\n")));
            return simpleGridArea.fetches.entrySet().stream().mapToInt(e -> e.getCount()).max().orElse(0);
        }));
    }
    
    @Test
    public void testGridLines()
    {
        SimpleGridArea simpleGridArea = TFXUtil.fx(() -> new SimpleGridArea());
        VirtualGridLineSupplier gridLineSupplier = new VirtualGridLineSupplier();
        TFXUtil.fx_(() -> {
            virtualGrid.addGridAreas(ImmutableList.of(simpleGridArea));
            virtualGrid.addNodeSupplier(gridLineSupplier);
        });
        
        // Scroll slowly sideways, and check that the lines are always valid:
        double curScrollOffset = 0;
        for (int i = 0; i < 100; i++)
        {
            int expectedLines = (int)Math.ceil(WINDOW_WIDTH / 100.0);
            final int SCROLL_AMOUNT = 29;
            
            TFXUtil.fx_(() -> virtualGrid.getScrollGroup().requestScrollBy(-SCROLL_AMOUNT, 0.0));
            curScrollOffset += SCROLL_AMOUNT;
            // Don't let them all turn into one big smooth scroll:
            if (i % 20 == 0)
                TFXUtil.sleep(500);

            Collection<Line> columnDividers = TFXUtil.fx(() -> gridLineSupplier._test_getColumnDividers());
            MatcherAssert.assertThat(columnDividers.size(), Matchers.greaterThanOrEqualTo(expectedLines));
            for (Line columnDivider : columnDividers)
            {
                assertEquals(0.0, (TFXUtil.fx(() -> columnDivider.getLayoutX()) + 0.5 + curScrollOffset - 20.0) % 100, 0.01);
            }
            // Should all be different X:
            assertEquals(columnDividers.size(), columnDividers.stream().mapToDouble(l -> TFXUtil.fx(() -> l.getLayoutX())).distinct().count());
        }

        // Same for Y:
        curScrollOffset = 0;
        for (int i = 0; i < 100; i++)
        {
            int expectedLines = (int)Math.ceil(WINDOW_HEIGHT / 24.0);
            final int SCROLL_AMOUNT = 7;

            TFXUtil.fx_(() -> virtualGrid.getScrollGroup().requestScrollBy(0.0, -SCROLL_AMOUNT));
            curScrollOffset += SCROLL_AMOUNT;
            // Don't let them all turn into one big smooth scroll:
            if (i % 20 == 0)
                TFXUtil.sleep(500);

            Collection<Line> rowDividers = TFXUtil.fx(() -> gridLineSupplier._test_getRowDividers());
            MatcherAssert.assertThat(rowDividers.size(), Matchers.greaterThanOrEqualTo(expectedLines));
            for (Line rowDivider : rowDividers)
            {
                assertEquals(0.0, (TFXUtil.fx(() -> rowDivider.getLayoutY()) + 0.5 + curScrollOffset) % 24, 0.01);
            }
            // Should all be different X:
            assertEquals(rowDividers.size(), rowDividers.stream().mapToDouble(l -> TFXUtil.fx(() -> l.getLayoutY())).distinct().count());
        }
    }
    
    @Test
    public void testTableMove()
    {
        SimpleGridArea simpleGridArea = TFXUtil.fx(() -> new SimpleGridArea());
        // Test that when table moves, cells are updated properly
        TFXUtil.fx_(() -> {
            SimpleCellSupplier simpleCellSupplier = new SimpleCellSupplier();
            virtualGrid.addGridAreas(ImmutableList.of(simpleGridArea));
            simpleCellSupplier.addGrid(simpleGridArea, simpleGridArea);
            virtualGrid.addNodeSupplier(simpleCellSupplier);
            virtualGrid.addNodeSupplier(new VirtualGridLineSupplier());
        });
        
        Pattern posPattern = Pattern.compile("\\(([0-9]+), ([0-9]+)\\)");

        for (int i = 0; i < 2; i++)
        {
            // Table begins at 0,0.  Check that cells we can find do match up:
            for (Label cell : TFXUtil.fx(() -> lookup(".simple-cell").match(Label::isVisible).<Label>queryAll()))
            {
                // Find out what position it thinks it is:
                String content = TFXUtil.fx(() -> cell.getText());
                Matcher m = posPattern.matcher(content);
                assertTrue(content, m.matches());
                @SuppressWarnings({"units", "nullness"})
                @AbsColIndex int column = Integer.valueOf(m.group(1));
                @SuppressWarnings({"units", "nullness"})
                @AbsRowIndex int row = Integer.valueOf(m.group(2));

                Point2D expectedLayoutPos = TFXUtil.fx(() -> {
                    VisibleBounds visibleBounds = virtualGrid.getVisibleBounds();
                    return new Point2D(visibleBounds.getXCoord(column + simpleGridArea.getPosition().columnIndex), visibleBounds.getYCoord(row + simpleGridArea.getPosition().rowIndex));
                });
                Point2D actualLayoutPos = TFXUtil.fx(() -> new Point2D(cell.getLayoutX(), cell.getLayoutY()));
                
                assertEquals(content, expectedLayoutPos, actualLayoutPos);
                MatcherAssert.assertThat(column, Matchers.greaterThanOrEqualTo(0));
                MatcherAssert.assertThat(row, Matchers.greaterThanOrEqualTo(0));
            }
            // Try again after a move:
            TFXUtil.fx_(() -> simpleGridArea.setPosition(new CellPosition(CellPosition.row(3), CellPosition.col(5))));
        }
    }
    
    @OnThread(Tag.FXPlatform)
    private static final class BoundsGridArea extends SimpleGridArea
    {
        private final RectangleBounds origBounds;

        public BoundsGridArea(RectangleBounds bounds)
        {
            this.origBounds = bounds;
            setPosition(bounds.topLeftIncl);
            getAndUpdateBottomRow(CellPosition.row(1000), () -> {});
        }

        @Override
        protected CellPosition recalculateBottomRightIncl()
        {
            return getPosition().offsetByRowCols(origBounds.bottomRightIncl.rowIndex - origBounds.topLeftIncl.rowIndex, origBounds.bottomRightIncl.columnIndex - origBounds.topLeftIncl.columnIndex);
        }
    }
    
    private void checkOverlap(List<RectangleBounds> bounds)
    {
        List<BoundsGridArea> gridAreas = Utility.mapList(bounds, r -> {
            return TFXUtil.fx(() -> new BoundsGridArea(r));
        });
        TFXUtil.fx_(() -> virtualGrid.addGridAreas(gridAreas));
        sleep(1000);
        // Should already not be touching:
        for (BoundsGridArea a : gridAreas)
        {
            RectangleBounds aBounds = TFXUtil.fx(() -> new RectangleBounds(a.getPosition(), a.getBottomRightIncl()));
            for (BoundsGridArea b : gridAreas)
            {
                RectangleBounds bBounds = TFXUtil.fx(() -> new RectangleBounds(b.getPosition(), b.getBottomRightIncl()));
                // Don't check if grid area overlaps itself:
                if (a != b)
                {
                    assertFalse("Bounds " + aBounds + " should not touch " + bBounds, aBounds.touches(bBounds));
                }
            }
            
        }
    }
    
    private RectangleBounds b(int x0, int y0, int x1, int y1)
    {
        return new RectangleBounds(new CellPosition(CellPosition.row(y0), CellPosition.col(x0)), new CellPosition(CellPosition.row(y1), CellPosition.col(x1)));
    }
    
    @Test
    public void testOverlap1()
    {
        checkOverlap(ImmutableList.of(
            b(1,1, 2, 3),
            // This should shunt:
            b(2, 2, 3, 2)
        ));
    }

    @Test
    public void testOverlap2()
    {
        checkOverlap(ImmutableList.of(
                b(1,1, 2, 3),
                // This should shunt and cause knock-on shunt:
                b(2, 2, 3, 2),
                b(3,1, 5, 3)
        ));
    }
    
    @Property(trials = 30)
    public void testOverlap(@From(GenRandom.class) Random r)
    {
        ArrayList<RectangleBounds> gridAreas = new ArrayList<>();
        for (int i = 0; i < 20; i++)
        {
            CellPosition topLeft = new CellPosition(CellPosition.row(1 + r.nextInt(40)), CellPosition.col(1 + r.nextInt(40)));
            CellPosition bottomRight = topLeft.offsetByRowCols(r.nextInt(15), r.nextInt(15));
            gridAreas.add(new RectangleBounds(topLeft, bottomRight));
        }
        checkOverlap(gridAreas);
    }
    
    
    
    /*
    @Test
    public void testSetColumns()
    {
        // Test that when columns change, cells are updated properly
        SimpleGridArea simpleGridArea = new SimpleGridArea();
        // Test that when table moves, cells are updated properly
        TFXUtil.fx_(() -> {
            SimpleCellSupplier simpleCellSupplier = new SimpleCellSupplier();
            virtualGrid.addGridAreas(ImmutableList.of(simpleGridArea));
            simpleCellSupplier.addGrid(simpleGridArea, simpleGridArea);
            virtualGrid.addNodeSupplier(simpleCellSupplier);
            virtualGrid.addNodeSupplier(new VirtualGridLineSupplier());
        });

        Pattern posPattern = Pattern.compile("\\(([0-9]+), ([0-9]+)\\)");

        for (int numColumns : 
        {
            // Table begins at 0,0.  Check that cells we can find do match up:
            for (Label cell : lookup(".simple-cell").match(Label::isVisible).<Label>queryAll())
            {
                // Find out what position it thinks it is:
                String content = TFXUtil.fx(() -> cell.getText());
                Matcher m = posPattern.matcher(content);
                assertTrue(content, m.matches());
                @SuppressWarnings({"units", "nullness"})
                @AbsColIndex int column = Integer.valueOf(m.group(1));
                @SuppressWarnings({"units", "nullness"})
                @AbsRowIndex int row = Integer.valueOf(m.group(2));

                Point2D expectedLayoutPos = TFXUtil.fx(() -> {
                    VisibleBounds visibleBounds = virtualGrid.getVisibleBounds();
                    return new Point2D(visibleBounds.getXCoord(column + simpleGridArea.getPosition().columnIndex), visibleBounds.getYCoord(row + simpleGridArea.getPosition().rowIndex));
                });
                Point2D actualLayoutPos = TFXUtil.fx(() -> new Point2D(cell.getLayoutX(), cell.getLayoutY()));

                assertEquals(content, expectedLayoutPos, actualLayoutPos);
                MatcherAssert.assertThat(column, Matchers.greaterThanOrEqualTo(0));
                MatcherAssert.assertThat(row, Matchers.greaterThanOrEqualTo(0));
            }
            // Try again after a move:
            TFXUtil.fx_(() -> simpleGridArea.setPosition(new CellPosition(CellPosition.row(3), CellPosition.col(5))));
        }
    }    
    */
    
    private static class DummySupplier extends VirtualGridSupplier<Label>
    {
        // Leave it negative while starting up:
        private int layoutCount = -100;

        @Override
        protected void layoutItems(ContainerChildren containerChildren, VisibleBounds visibleBounds, VirtualGrid virtualGrid)
        {
            layoutCount += 1;
            if (layoutCount > 0)
                Log.logStackTrace("");
        }

        @Override
        public OptionalDouble getPrefColumnWidth(@AbsColIndex int colIndex)
        {
            return OptionalDouble.empty();
        }

        @Override
        protected @Nullable Pair<ItemState, @Nullable StyledString> getItemState(CellPosition cellPosition, Point2D screenPosition)
        {
            return null;
        }

        @Override
        protected void keyboardActivate(CellPosition cellPosition)
        {
        }
    }

    private static class SimpleCellSupplier extends VirtualGridSupplierIndividual<Label, String, GridCellInfo<Label, String>>
    {
        protected SimpleCellSupplier()
        {
            super(ImmutableList.of());
        }

        @Override
        protected Label makeNewItem(VirtualGrid virtualGrid)
        {
            Label label = new Label("X");
            label.getStyleClass().add("simple-cell");
            return label;
        }

        @Override
        public OptionalDouble getPrefColumnWidth(@AbsColIndex int colIndex)
        {
            return OptionalDouble.empty();
        }

        @Override
        protected @OnThread(Tag.FX) void adjustStyle(Label item, String style, boolean on)
        {

        }

        @Override
        protected @Nullable Pair<ItemState, @Nullable StyledString> getItemState(Label item, Point2D screenPos)
        {
            return new Pair<>(ItemState.NOT_CLICKABLE, null);
        }

        @Override
        protected void keyboardActivate(CellPosition cellPosition)
        {
        }
    }

    @OnThread(Tag.FXPlatform)
    private static class SimpleGridArea extends GridArea implements GridCellInfo<Label,String>
    {
        private static int nextId = 0;
        int id = nextId++;
        Multiset<GridAreaCellPosition> fetches = HashMultiset.create();
        
        public SimpleGridArea()
        {
            setPosition(CellPosition.ORIGIN.offsetByRowCols(1, 1));
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
            return Utility.codePointToString(id);
        }

        @Override
        public @Nullable GridAreaCellPosition cellAt(CellPosition cellPosition)
        {
            if (contains(cellPosition))
                return GridAreaCellPosition.relativeFrom(cellPosition, getPosition());
            else
                return null;
        }

        @Override
        public ImmutableList<RectangleBounds> getCellBounds()
        {
            return ImmutableList.of(new RectangleBounds(getPosition(), getBottomRightIncl()));
        }

        @Override
        public void fetchFor(GridAreaCellPosition cellPosition, FXPlatformFunction<CellPosition, @Nullable Label> getCell, FXPlatformRunnable scheduleStyleTogether)
        {
            fetches.add(cellPosition);
            Label label = getCell.apply(cellPosition.from(getPosition()));
            if (label != null)
                label.setText(cellPosition.toString());
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
