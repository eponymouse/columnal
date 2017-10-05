package records.gui.stable;

import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.layout.Region;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.STFillId;
import records.error.InternalException;
import records.gui.stf.EditorKitSimpleLabel;
import records.gui.stf.StructuredTextField;
import records.gui.stf.StructuredTextField.EditorKit;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
    private final ArrayList<ArrayList<StructuredTextField>> visibleCells;
    private int firstVisibleColumnIndex;
    private int firstVisibleRowIndex;
    // Offset of top visible cell.  Always <= 0
    private double firstVisibleColumnOffset;
    private double firstVisibleRowOffset;

    // TODO: could try to use translate to make scrolling faster?

    // TODO add another sidestrip class for virtualizing row/column headers (line numbers and column names), then add listener to do the updating.

    // Cells which are spare: they are still members of the
    // parent pane to avoid costs of removing and re-adding,
    // but they are held at an off-screen position.
    private final ArrayList<StructuredTextField> spareCells;

    private double rowHeight;
    private int totalRows;
    private double[] columnWidths;

    private final ObjectProperty<@Nullable Pair<Integer, Integer>> focusedCell = new SimpleObjectProperty<>(null);

    private final ValueLoadSave loadSave;

    private final Container container;

    public VirtScrollStrTextGrid(ValueLoadSave loadSave)
    {
        visibleCells = new ArrayList<>();
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
        // TODO
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
        //TODO
    }

    public void showAtOffset(int row, double pixelOffset)
    {
        //TODO
    }

    public static interface ValueLoadSave
    {
        @OnThread(Tag.FXPlatform)
        void fetchEditorKit(int rowIndex, int colIndex, FXPlatformConsumer<EditorKit<?>> setEditorKit);
    }

    public void setData(int numRows, double[] columnWidths)
    {
        // Snap to top:
        firstVisibleRowIndex = 0;
        firstVisibleColumnIndex = 0;
        firstVisibleColumnOffset = 0;
        firstVisibleRowOffset = 0;

        // Empty previous:
        Utility.<ArrayList<StructuredTextField>>resizeList(visibleCells, 0, index -> new ArrayList<>(), cs -> spareCells.addAll(cs));

        // These variables resize the number of elements,
        // and then layout actually rejigs the display:
        this.totalRows = numRows;
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
            int newNumVisibleRows = Math.min(totalRows - firstVisibleRowIndex, (int)Math.ceil(getHeight() / rowHeight) + 1);
            int newNumVisibleCols = 0;
            for (int column = firstVisibleColumnIndex; x < getWidth() && column < columnWidths.length; column++)
            {
                newNumVisibleCols += 1;
                x += columnWidths[column];
            }

            Utility.resizeList(visibleCells, newNumVisibleRows, n -> new ArrayList<>(columnWidths.length), cs -> spareCells.addAll(cs));
            for (int visRowIndex = 0; visRowIndex < visibleCells.size(); visRowIndex++)
            {
                int visRowIndexFinal = visRowIndex;
                ArrayList<StructuredTextField> row = visibleCells.get(visRowIndex);
                Utility.resizeList(row, newNumVisibleCols, visColIndex -> getSpareOrNewCell(visRowIndexFinal, visColIndex), cs -> spareCells.add(cs));
            }


            y = firstVisibleRowOffset;
            for (ArrayList<StructuredTextField> row : visibleCells)
            {
                x = firstVisibleColumnOffset;
                // If we need it, add another visible column
                for (int columnIndex = 0; columnIndex < row.size(); columnIndex++)
                {
                    StructuredTextField cell = row.get(columnIndex);
                    cell.resizeRelocate(x, y, columnWidths[columnIndex], rowHeight);
                    x += columnWidths[columnIndex];
                }
                y += rowHeight;
            }

            // Don't let spare cells be more than two visible rows or columns:
            int maxSpareCells = 2 * Math.max(visibleCells.size(), visibleCells.isEmpty() ? 0 : visibleCells.get(0).size());

            while (spareCells.size() > maxSpareCells)
                container.getChildren().remove(spareCells.remove(spareCells.size() - 1));

            for (StructuredTextField spareCell : spareCells)
            {
                spareCell.relocate(10000, 10000);
            }
        }


        private StructuredTextField getSpareOrNewCell(int visRowIndex, int visColIndex)
        {
            StructuredTextField cell;
            if (!spareCells.isEmpty())
            {
                cell = spareCells.remove(spareCells.size() - 1);
            }
            else
            {
                cell = new StructuredTextField(Container.this::requestFocus);
                getChildren().add(cell);
            }
            int realRow = firstVisibleRowIndex + visRowIndex;
            int realCol = firstVisibleColumnIndex + visColIndex;
            // Blank then queue fetch:
            cell.resetContent(new EditorKitSimpleLabel<>("Loading..."));
            // TODO need to make sure cell hasn't changed position in mean time.
            loadSave.fetchEditorKit(realRow, realCol, k -> cell.resetContent(k));
            return cell;
        }
    }

    public static interface EditorKitCallback
    {
        @OnThread(Tag.FXPlatform)
        public void loadedValue(int rowIndex, int colIndex, EditorKit<?> editorKit);
    }
}
