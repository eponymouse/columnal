package records.gui.stable;

import javafx.application.Platform;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.layout.Region;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.InternalException;
import records.error.UserException;
import records.gui.stf.EditorKitSimpleLabel;
import records.gui.stf.StructuredTextField;
import records.gui.stf.StructuredTextField.EditorKit;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.SimulationFunction;
import utility.Utility;
import utility.Workers;
import utility.Workers.Priority;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * This is a lot like a VirtualizedScrollPane of HBox of
 * StructuredTextField, which was the original model for displaying
 * data tables.  But it turned out to be too slow, so it's rewritten
 * here to take advantage of several aspects:
 *  - We can virtualize horizontally and vertically
 *  - We can re-use cells by replacing their content components
 *    (i.e. all cells have the same GUI element type)
 *  - Columns have the same width all the way down, and rows
 *    have same height throughout the grid.
 */
@OnThread(Tag.FXPlatform)
public class VirtScrollStrTextGrid
{
    // Cells which are visible, organised as a 2D array
    // (inner is a row, outer is list of rows)
    private final Map<CellPosition, StructuredTextField> visibleCells;
    private int firstVisibleColumnIndex;
    private int firstVisibleRowIndex;
    // Offset of top visible cell.  Always -rowHeight <= y <= 0
    private double firstVisibleColumnOffset;
    private double firstVisibleRowOffset;
    private int visibleRowCount;

    // TODO: could try to use translate to make scrolling faster?

    // TODO add another sidestrip class for virtualizing row/column headers (line numbers and column names), then add listener to do the updating.

    // Cells which are spare: they are still members of the
    // parent pane to avoid costs of removing and re-adding,
    // but they are held at an off-screen position.
    private final ArrayList<StructuredTextField> spareCells;

    private double rowHeight;
    // This is a minimum number of rows known to be in the table:
    @OnThread(Tag.FXPlatform)
    private int currentKnownRows = 0;
    // A function to ask if the row index is valid.  If isRowValid(n)
    // returns false, it's guaranteed that isRowValid(m) for m>=n is also false.
    @OnThread(Tag.FXPlatform)
    private @Nullable SimulationFunction<Integer, Boolean> isRowValid;
    private double[] columnWidths;

    private final ObjectProperty<@Nullable Pair<Integer, Integer>> focusedCell = new SimpleObjectProperty<>(null);

    private final ValueLoadSave loadSave;

    private final Container container;

    public VirtScrollStrTextGrid(ValueLoadSave loadSave)
    {
        visibleCells = new HashMap<>();
        firstVisibleColumnIndex = 0;
        firstVisibleRowIndex = 0;
        firstVisibleColumnOffset = 0;
        firstVisibleRowOffset = 0;
        spareCells = new ArrayList<>();
        rowHeight = 24;
        columnWidths = new double[0];
        this.loadSave = loadSave;

        container = new Container();
    }

    public ObjectExpression<@Nullable Pair<Integer, Integer>> focusedCellProperty()
    {
        return focusedCell;
    }

    public void scrollYToPixel(double y)
    {
        // Can't scroll to negative:
        if (y < 0)
            y = 0;

        // So, if y == 0 then we make first cell the top and offset == 0
        // If y < 1*rowHeight, still first cell, and offset == -y
        // If y < 2*rowHeight, second cell, and offset == rowHeight - y
        // General pattern: divide by rowHeight and round down to get topCell
        // Then offset is topCell*rowHeight - y
        int topCell = (int)Math.floor(y / rowHeight);
        this.firstVisibleRowIndex = topCell;
        this.firstVisibleRowOffset = (topCell * rowHeight) - y;
        updateKnownRows();
        container.requestLayout();
    }

    private void updateKnownRows()
    {
        final int prevKnownRows = currentKnownRows;
        if (prevKnownRows > firstVisibleRowIndex + visibleRowCount + 200)
        {
            // If there's more than 200 unshown, that will do as an estimate:
            return;
        }

        int searchMax = firstVisibleRowIndex + visibleRowCount + 250;
        @Nullable SimulationFunction<Integer, Boolean> isRowValidFinal = this.isRowValid;
        Workers.onWorkerThread("Calculating number of rows", Priority.FETCH, () -> {
            int knownRows;
            for (knownRows = prevKnownRows + 1; knownRows < searchMax; knownRows++)
            {
                try
                {
                    if (isRowValidFinal == null || !isRowValidFinal.apply(knownRows))
                        break;
                }
                catch (InternalException | UserException e)
                {
                    Utility.log(e);
                    break;
                }
            }
            int knownRowsFinal = knownRows - 1;
            Platform.runLater(() -> {
                currentKnownRows = knownRowsFinal;
                container.requestLayout();
            });
        });
    }

    public void scrollXToPixel(double x)
    {
        // TODO
    }

    // Focuses cell so that you can navigate around with keyboard
    public void focusCell(int rowIndex, int columnIndex)
    {
        // TODO
    }

    // Edits cell so that you can start typing content
    public void editCell(int rowIndex, int columnIndex)
    {
        // TODO
    }

    public boolean isEditingCell(int rowIndex, int columnIndex)
    {
        return false; // TODO
    }

    public void scrollXBy(double x)
    {
        //TODO
    }

    public void scrollYBy(double y)
    {
        scrollYToPixel(firstVisibleRowIndex * rowHeight - firstVisibleRowOffset + y);
    }

    public void showAtOffset(int row, double pixelOffset)
    {
        // Shouldn't happen, but clamp row:
        if (row < 0)
            row = 0;
        this.firstVisibleRowOffset = pixelOffset;
        this.firstVisibleRowIndex = row;
        updateKnownRows();
        container.requestLayout();
    }

    public static interface ValueLoadSave
    {
        @OnThread(Tag.FXPlatform)
        void fetchEditorKit(int rowIndex, int colIndex, FXPlatformConsumer<EditorKit<?>> setEditorKit);
    }

    public void setData(SimulationFunction<Integer, Boolean> isRowValid, double[] columnWidths)
    {
        // Snap to top:
        firstVisibleRowIndex = 0;
        firstVisibleColumnIndex = 0;
        firstVisibleColumnOffset = 0;
        firstVisibleRowOffset = 0;

        // Empty previous:
        spareCells.addAll(visibleCells.values());
        visibleCells.clear();

        // These variables resize the number of elements,
        // and then layout actually rejigs the display:
        this.isRowValid = isRowValid;
        this.currentKnownRows = 0;
        updateKnownRows();
        this.columnWidths = Arrays.copyOf(columnWidths, columnWidths.length);

        container.requestLayout();
    }

    public Region getNode()
    {
        return container;
    }

    @OnThread(Tag.FXPlatform)
    private class Container extends Region
    {
        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        protected double computePrefWidth(double height)
        {
            return super.computePrefWidth(height);
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        protected double computePrefHeight(double width)
        {
            return super.computePrefHeight(width);
        }

        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        @Override
        protected void layoutChildren()
        {
            double x = firstVisibleColumnOffset;
            double y = firstVisibleRowOffset;

            // We may not need the +1, but play safe:
            int newNumVisibleRows = Math.min(currentKnownRows - firstVisibleRowIndex, (int)Math.ceil(getHeight() / rowHeight) + 1);
            int newNumVisibleCols = 0;
            for (int column = firstVisibleColumnIndex; x < getWidth() && column < columnWidths.length; column++)
            {
                newNumVisibleCols += 1;
                x += columnWidths[column];
            }
            VirtScrollStrTextGrid.this.visibleRowCount = newNumVisibleRows;

            //TODO remove not-visible cells and put them in spare cells

            y = firstVisibleRowOffset;
            for (int rowIndex = firstVisibleRowIndex; rowIndex < firstVisibleRowIndex + newNumVisibleRows; rowIndex++)
            {
                x = firstVisibleColumnOffset;
                // If we need it, add another visible column
                for (int columnIndex = firstVisibleColumnIndex; columnIndex < firstVisibleColumnIndex + newNumVisibleCols; columnIndex++)
                {
                    CellPosition cellPosition = new CellPosition(rowIndex, columnIndex);
                    StructuredTextField cell = visibleCells.get(cellPosition);
                    // If cell isn't present, grab from spareCells:
                    if (cell == null)
                    {
                        if (!spareCells.isEmpty())
                        {
                            cell = spareCells.remove(spareCells.size() - 1);
                        }
                        else
                        {
                            cell = new StructuredTextField(() -> focusCell(cellPosition.rowIndex, cellPosition
                                    .columnIndex));
                            getChildren().add(cell);
                        }

                        visibleCells.put(cellPosition, cell);
                        StructuredTextField newCellFinal = cell;
                        // Blank then queue fetch:
                        cell.resetContent(new EditorKitSimpleLabel<>("Loading..."));
                        loadSave.fetchEditorKit(rowIndex, columnIndex, k -> {
                            // Check cell hasn't been re-used since:
                            if (visibleCells.get(cellPosition) == newCellFinal)
                                newCellFinal.resetContent(k);
                        });
                    }
                    cell.resizeRelocate(x, y, columnWidths[columnIndex], rowHeight);
                    x += columnWidths[columnIndex];
                }
                y += rowHeight;
            }

            // Don't let spare cells be more than two visible rows or columns:
            int maxSpareCells = 2 * Math.max(newNumVisibleCols * 2, newNumVisibleRows * 2);

            while (spareCells.size() > maxSpareCells)
                container.getChildren().remove(spareCells.remove(spareCells.size() - 1));

            for (StructuredTextField spareCell : spareCells)
            {
                spareCell.relocate(10000, 10000);
            }
        }
    }

    public static interface EditorKitCallback
    {
        @OnThread(Tag.FXPlatform)
        public void loadedValue(int rowIndex, int colIndex, EditorKit<?> editorKit);
    }

    private static class CellPosition
    {
        public final int rowIndex;
        public final int columnIndex;

        private CellPosition(int rowIndex, int columnIndex)
        {
            this.rowIndex = rowIndex;
            this.columnIndex = columnIndex;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            CellPosition that = (CellPosition) o;

            if (rowIndex != that.rowIndex) return false;
            return columnIndex == that.columnIndex;
        }

        @Override
        public int hashCode()
        {
            int result = rowIndex;
            result = 31 * result + columnIndex;
            return result;
        }
    }
}
