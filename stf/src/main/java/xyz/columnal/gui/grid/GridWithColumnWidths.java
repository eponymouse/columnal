package records.gui.grid;

import annotation.units.AbsColIndex;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.dataflow.qual.Pure;

import java.util.HashMap;
import java.util.Map;

class GridWithColumnWidths
{
    static final double DEFAULT_COLUMN_WIDTH = 100;
    static final double fixedFirstColumnWidth = 20;

    protected final Map<@AbsColIndex Integer, Double> customisedColumnWidths = new HashMap<>();
    @Pure
    public final double getColumnWidth(@UnknownInitialization(GridWithColumnWidths.class) GridWithColumnWidths this, int columnIndex)
    {
        if (columnIndex == 0)
            return fixedFirstColumnWidth;
        else
            return customisedColumnWidths.getOrDefault(columnIndex, DEFAULT_COLUMN_WIDTH);
    }
}
