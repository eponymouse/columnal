package test;

import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.junit.Rule;
import org.junit.runner.RunWith;
import records.data.CellPosition;
import records.gui.grid.GridArea;
import records.gui.grid.VirtualGrid;
import test.gen.GenGridAreaList;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.List;

import static org.junit.Assert.assertFalse;

@RunWith(JUnitQuickcheck.class)
@OnThread(value = Tag.FXPlatform, ignoreParent = true)
public class PropGridOverlap
{
    @Rule
    public JavaFXThreadingRule javafxRule = new JavaFXThreadingRule();
    
    @Property(trials = 1000)
    public void testLoad(@From(GenGridAreaList.class) GenGridAreaList.GridAreaList gridAreas)
    {
        VirtualGrid grid = new VirtualGrid(null);
        for (GridArea gridArea : gridAreas.gridAreas)
        {
            grid.addGridArea(gridArea);
        }
        checkNoOverlap(gridAreas);

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
                    CellPosition aBottomRightIncl = new CellPosition(aTopLeft.rowIndex + a.getCurrentKnownRows() - 1, aTopLeft.columnIndex + a.getColumnCount() - 1);
                    CellPosition bBottomRightIncl = new CellPosition(bTopLeft.rowIndex + b.getCurrentKnownRows() - 1, bTopLeft.columnIndex + b.getColumnCount() - 1);
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
        for (GridArea gridArea : gridAreas.gridAreas)
        {
            grid.addGridArea(gridArea);
        }
        
        gridAreas.gridAreas.get(Math.abs(toMove) % gridAreas.gridAreas.size()).setPosition(
            new CellPosition(Math.abs(newColumn) % 25, Math.abs(newRow) % 25)
        );
        
        checkNoOverlap(gridAreas);
    }
}
