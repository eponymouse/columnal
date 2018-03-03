package test;

import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import log.Log;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.runner.RunWith;
import records.data.CellPosition;
import records.gui.grid.GridArea;
import records.gui.grid.VirtualGrid;
import test.gen.GenGridAreaList;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

@RunWith(JUnitQuickcheck.class)
@OnThread(value = Tag.FXPlatform, ignoreParent = true)
public class PropGridOverlap
{
    @Rule
    public JavaFXThreadingRule javafxRule = new JavaFXThreadingRule();
    
    @Property(trials = 10000)
    public void testLoad(@When(seed=1) @From(GenGridAreaList.class) GenGridAreaList.GridAreaList gridAreas)
    {
        Log.debug("\n\n\nStarting\n\n\n");
        VirtualGrid grid = new VirtualGrid(null);
        ImmutableList<GridArea> sortedByOriginalX = sortByCurrentX(gridAreas.gridAreas);
        grid.addGridAreas(gridAreas.gridAreas);
        checkNoOverlap(gridAreas);
        //checkHorizSorted(sortedByOriginalX);

    }

    // For any given pair of tables, if they overlap vertically, checks
    // that they are still in the horizontal order that they were originally
    @SuppressWarnings("deprecation")
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
                    assertThat("Comparing " + sortedByOriginalX.get(i) + " to " + sortedByOriginalX.get(j), iPos.columnIndex,
                            Matchers.lessThanOrEqualTo(jPos.columnIndex));
                }
            }
        }
    }

    private static ImmutableList<GridArea> sortByCurrentX(ImmutableList<GridArea> gridAreas)
    {
        return gridAreas.stream().sorted(Comparator.comparing(g -> g.getPosition().columnIndex)).collect(ImmutableList.toImmutableList());
    }

    private void checkNoOverlap(GenGridAreaList.GridAreaList gridAreas)
    {
        // Now check that none overlap.  We do a brute force N^2 test as it is simplest:
        for (GridArea a : gridAreas.gridAreas)
        {
            for (GridArea b : gridAreas.gridAreas)
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

    @Property(trials = 10000)
    public void testMove(@From(GenGridAreaList.class) GenGridAreaList.GridAreaList gridAreas, int toMove, int newColumn, int newRow)
    {
        VirtualGrid grid = new VirtualGrid(null);
        ImmutableList<GridArea> sortedByOriginalX = sortByCurrentX(gridAreas.gridAreas);
        grid.addGridAreas(gridAreas.gridAreas);
        checkNoOverlap(gridAreas);
        //checkHorizSorted(sortedByOriginalX);
        
        gridAreas.gridAreas.get(Math.abs(toMove) % gridAreas.gridAreas.size()).setPosition(
            new CellPosition(CellPosition.row(Math.abs(newColumn) % 25), CellPosition.col(Math.abs(newRow) % 25))
        );
        
        
        checkNoOverlap(gridAreas);
        // TODO
        //checkHorizSortedBy(gridAreas.gridAreas, originalX);
    }
}
