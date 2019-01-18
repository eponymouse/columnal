package records.gui;

import annotation.units.AbsColIndex;
import javafx.stage.Window;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import records.data.CellPosition;
import records.gui.grid.CellSelection;
import records.gui.grid.GridArea;
import records.gui.grid.RectangleBounds;
import utility.Either;

public class EntireTableSelection implements CellSelection
{
    private final DataDisplay selected;
    // Although we select the whole table, if they move out up/down, we stay
    // in the column they entered from:
    private final @AbsColIndex int column;

    public EntireTableSelection(DataDisplay selected, @AbsColIndex int column)
    {
        this.selected = selected;
        this.column = column;
    }

    @Override
    public void doCopy()
    {
        selected.doCopy(null);
    }

    @Override
    public void doPaste()
    {
        // TODO
    }

    @Override
    public CellPosition getActivateTarget()
    {
        return selected.getPosition();
    }

    @Override
    public CellSelection atHome(boolean extendSelection)
    {
        return this;
    }

    @Override
    public CellSelection atEnd(boolean extendSelection)
    {
        return this;
    }

    @Override
    public Either<CellPosition, CellSelection> move(boolean extendSelection, int byRows, int byColumns)
    {
        if (!extendSelection)
        {
            if (byRows != 0)
            {
                // Conceieve of this as going up/down first to a single cell, then across:
                return Either.left(selected.getPosition().offsetByRowCols(byRows, (column - selected.getPosition().columnIndex) + byColumns));
            }
            else
            {
                // Only going left/right, just move out of the end of the table:
                if (byColumns <= 0)
                    return Either.left(selected.getPosition().offsetByRowCols(byRows, byColumns));
                else
                    return Either.left(selected.getPosition().offsetByRowCols(byRows, byColumns + selected.getBottomRightIncl().columnIndex - selected.getPosition().columnIndex));
            }
        }
        return Either.right(this);
    }

    @Override
    public CellPosition positionToEnsureInView()
    {
        return selected.getPosition();
    }

    @Override
    public RectangleBounds getSelectionDisplayRectangle()
    {
        return new RectangleBounds(selected.getPosition(), selected.getBottomRightIncl());
    }

    @Override
    public boolean isExactly(CellPosition cellPosition)
    {
        // Whole table, so won't be single cell:
        return false;
    }

    @Override
    public boolean includes(@UnknownInitialization(GridArea.class) GridArea tableDisplay)
    {
        return this.selected == tableDisplay;
    }

    @Override
    public void gotoRow(Window parent)
    {
        selected.gotoRow(parent, column);
    }
}
