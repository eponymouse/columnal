package records.gui;

import records.data.CellPosition;
import records.gui.grid.CellSelection;
import records.gui.grid.RectangleBounds;
import utility.Either;

public class EntireTableSelection implements CellSelection
{
    private final DataDisplay selected;

    public EntireTableSelection(DataDisplay selected)
    {
        this.selected = selected;
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
        // TODO allow moving out
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
        return new RectangleBounds(selected.getPosition(), selected.getPosition().offsetByRowCols(selected.getCurrentKnownRows(), selected.getColumnCount()));
    }
}
