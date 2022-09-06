package test;

import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import xyz.columnal.log.Log;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import records.data.CellPosition;
import records.gui.grid.GridArea;
import records.gui.grid.VirtualGrid;
import test.gen.GenGridAreaList;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Utility;

import java.util.Comparator;

import static org.junit.Assert.assertFalse;

@RunWith(JUnitQuickcheck.class)
public class PropGridOverlap extends FXApplicationTest
{
    @Property(trials = 100)
    public void testLoad(@From(GenGridAreaList.class) GenGridAreaList.GridAreaList gridAreas)
    {
        TestUtil.fxTest_(() -> {
            VirtualGrid grid = new VirtualGrid(null, 0, 0);
            ImmutableList<GridArea> sortedByOriginalX = sortByCurrentX(gridAreas.gridAreas);
            grid.addGridAreas(gridAreas.gridAreas);
            checkNoOverlap(gridAreas.gridAreas);
            //checkHorizSorted(sortedByOriginalX);
        });
    }

    // For any given pair of tables, if they overlap vertically, checks
    // that they are still in the horizontal order that they were originally
    @OnThread(Tag.FXPlatform)
    private static void checkHorizSorted(ImmutableList<GridArea> sortedByOriginalX)
    {
        // Check that this list is sorted by current X in the cases where they overlap:
        for (int i = 0; i < sortedByOriginalX.size(); i++)
        {
            CellPosition iPos = sortedByOriginalX.get(i).getPosition();
            for (int j = i; j < sortedByOriginalX.size(); j++)
            {
                CellPosition jPos = sortedByOriginalX.get(j).getPosition();
                if (Utility.rangeOverlaps(0, iPos.rowIndex, sortedByOriginalX.get(i).getBottomRightIncl().rowIndex, jPos.rowIndex, sortedByOriginalX.get(j).getBottomRightIncl().rowIndex))
                {
                    MatcherAssert.assertThat("Comparing " + sortedByOriginalX.get(i) + " to " + sortedByOriginalX.get(j), iPos.columnIndex,
                            Matchers.lessThanOrEqualTo(jPos.columnIndex));
                }
            }
        }
    }

    @OnThread(Tag.FXPlatform)
    private static ImmutableList<GridArea> sortByCurrentX(ImmutableList<GridArea> gridAreas)
    {
        return gridAreas.stream().sorted(Comparator.comparing(g -> g.getPosition().columnIndex)).collect(ImmutableList.toImmutableList());
    }

    @OnThread(Tag.FXPlatform)
    private void checkNoOverlap(ImmutableList<GridArea> gridAreas)
    {
        // Now check that none overlap.  We do a brute force N^2 test as it is simplest:
        for (GridArea a : gridAreas)
        {
            for (GridArea b : gridAreas)
            {
                if (a != b)
                {
                    CellPosition aTopLeft = a.getPosition();
                    CellPosition bTopLeft = b.getPosition();
                    CellPosition aBottomRightIncl = a.getBottomRightIncl();
                    CellPosition bBottomRightIncl = b.getBottomRightIncl();
                    assertFalse("Overlap " + aTopLeft + ", " + aBottomRightIncl + " and " + bTopLeft + ", " + bBottomRightIncl,
                        aTopLeft.columnIndex < bBottomRightIncl.columnIndex && aBottomRightIncl.columnIndex > bTopLeft.columnIndex &&
                        aTopLeft.rowIndex < bBottomRightIncl.rowIndex && aBottomRightIncl.rowIndex > bTopLeft.rowIndex
                    );
                }
            }
        }
    }

    @Property(trials = 100)
    public void testMove(@From(GenGridAreaList.class) GenGridAreaList.GridAreaList gridAreas, int toMove, int newColumn, int newRow)
    {
        TestUtil.fxTest_(() -> {
            VirtualGrid grid = new VirtualGrid(null, 0, 0);
            ImmutableList<GridArea> sortedByOriginalX = sortByCurrentX(gridAreas.gridAreas);
            grid.addGridAreas(gridAreas.gridAreas);
            checkNoOverlap(gridAreas.gridAreas);
            //checkHorizSorted(sortedByOriginalX);

            gridAreas.gridAreas.get(Math.abs(toMove) % gridAreas.gridAreas.size()).setPosition(
                    new CellPosition(CellPosition.row(Math.abs(newColumn) % 25), CellPosition.col(Math.abs(newRow) % 25))
            );


            checkNoOverlap(gridAreas.gridAreas);
            // TODO
            //checkHorizSortedBy(gridAreas.gridAreas, originalX);
        });
    }
    
    @Test
    public void testPair()
    {
        TestUtil.fxTest_(() -> {
            // A particular pair which caused an infinite loop:
            VirtualGrid grid = new VirtualGrid(null, 0, 0);
            ImmutableList<GridArea> gridAreas = ImmutableList.of(
                    GenGridAreaList.makeGridArea(3, 1, 6, 15),
                    GenGridAreaList.makeGridArea(6, 7, 7, 11)

            );
            grid.addGridAreas(gridAreas);
            checkNoOverlap(gridAreas);
        });
    }
}
