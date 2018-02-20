package records.gui.stable;

import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Doubles;
import com.sun.javafx.scene.control.skin.ScrollBarSkin;
import javafx.application.Platform;
import javafx.beans.binding.DoubleExpression;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ObservableDoubleValue;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.ColumnId;
import records.data.RecordSet.RecordSetListener;
import records.data.Table.MessageWhenEmpty;
import records.data.TableOperations;
import records.data.TableOperations.AppendColumn;
import records.data.TableOperations.AppendRows;
import records.data.TableOperations.DeleteColumn;
import records.data.TableOperations.InsertRows;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.FXPlatformFunction;
import utility.SimulationFunction;
import utility.Utility;
import utility.Workers;
import utility.Workers.Priority;
import utility.gui.FXUtility;
import utility.gui.GUI;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A customised equivalent of TableView
 *
 * The internal node architecture is a bit complicated.
 *
 * .stable-view: StackPane
 *     .stable-view-placeholderLabel: Label
 *     *: BorderPane
 *         .stable-view-scroll-pane: VirtualizedScrollPane
 *             .: VirtualFlow [table contents]
 *         .stable-view-top: BorderPane [container to hold column headers and jump to top button]
 *             .stable-view-top-left: Region [item to take up top-left space]
 *             .stable-view-header: BorderPane [needed to clip and position the actual header items]
 *                 .: HBox [actual container of column headers]
 *                     .stable-view-header-item*: HeaderItem [each column header]
 *             .stable-button-top-wrapper: BorderPane [container to occupy spare height above the jump to top button]
 *                 .stable-button-top: Button [button to jump to top]
 *                     .stable-view-button-arrow: Region [the arrow graphic]
 *         .stable-view-left: BorderPane [container to hold row headers and jump to top button]
 *             .stable-view-side: BorderPane [needed to have clip and shadow for row numbers]
 *                 .: VirtualFlow [row numbers]
 *             .stable-button-left-wrapper: BorderPane [container to occupy spare height above the jump to left button]
 *                 .stable-button-left: Button [button to jump to left]
 *                     .stable-view-button-arrow: Region [the arrow graphic]
 *         .stable-view-row-numbers: StackPane [ ... ]
 *
 * The table contents inside the VirtualFlow are as follows.  Because tables tend to have a higher limit
 * of number of rows than columns, we have a vertical VirtualFlow, and each column is displayed even if
 * it's off screen (whereas rows are virtualized, so rows off-screen are not displayed).  The architecture
 * of each row is:
 *
 * .stable-view-row: HBox
 *     .stable-view-row-cell: StackPane
 *         ?: ? -- The cell contents can be any node
 *
 * Because we potentially allow the cell contents to be a compound structure, we don't access its properties
 * directly for determining focus, and instead rely on listeners in our code rather than observable properties.
 *
 */

@OnThread(Tag.FXPlatform)
public class StableView
{
    /*
public static final double DEFAULT_COLUMN_WIDTH = 100.0;
private final VirtRowLabels lineNumbers;
private final VirtColHeaders headerItemsContainer;
private final VirtScrollStrTextGrid grid;
private final Label placeholderLabel;
private final StackPane stackPane; // has grid and placeholderLabel as its children
private final DoubleProperty widthEstimate;
private final DoubleProperty heightEstimate;
private final ContentLayout contentLayout;

// An integer counter which tracks which instance of setColumnsAndRows we are on.  Used to
// effectively cancel some queued run-laters if the method is called twice in quick succession.
@OnThread(Tag.FXPlatform)
private AtomicInteger columnAndRowSet = new AtomicInteger(0);
private final BooleanProperty showingRowNumbers = new SimpleBooleanProperty(true);
// Contents can't change, but whole list can:
private ImmutableList<ColumnDetails> columns = ImmutableList.of();
private static final double EDGE_DRAG_TOLERANCE = 8;
private static final double MIN_COLUMN_WIDTH = 30;
// A column is only editable if it is marked editable AND the table is editable:
private boolean tableEditable = true;
private final MessageWhenEmpty messageWhenEmpty;
private final ScrollBar hbar;
private final ScrollBar vbar;

@OnThread(Tag.FXPlatform)
private @Nullable TableOperations operations;
private FXPlatformFunction<ColumnId, ImmutableList<ColumnOperation>> extraColumnOperations = c -> ImmutableList.of();
private final SimpleBooleanProperty nonEmptyProperty = new SimpleBooleanProperty(false);
private final BooleanProperty lineNumberShowing = new SimpleBooleanProperty(true);
private final BorderPane rightVertScroll;


public StableView(MessageWhenEmpty messageWhenEmpty)
{
    this.messageWhenEmpty = messageWhenEmpty;

    hbar = new ScrollBar();
    hbar.setOrientation(Orientation.HORIZONTAL);
    hbar.getStyleClass().add("stable-view-scroll-bar");
    vbar = new ScrollBar();
    vbar.setOrientation(Orientation.VERTICAL);
    vbar.getStyleClass().add("stable-view-scroll-bar");

    grid = new VirtScrollStrTextGrid(new ValueLoadSave()
    {
        @Override
        public void fetchEditorKit(int rowIndex, int colIndex, FXPlatformConsumer<CellPosition> relinquishFocus, EditorKitCallback setEditorKit)
        {
            if (columns != null && colIndex < columns.size())
            {
                columns.get(colIndex).columnHandler.fetchValue(rowIndex, b -> {
                }, relinquishFocus, setEditorKit);
            }
        }
    }, pos -> tableEditable && columns.get(pos.columnIndex).columnHandler.isEditable(), hbar, vbar)
    {

        @Override
        public void columnWidthChanged(int columnIndex, double newWidth)
        {
            super.columnWidthChanged(columnIndex, newWidth);
            if (columns != null)
            {
                ColumnHandler colHandler = columns.get(columnIndex).columnHandler;
                colHandler.columnResized(newWidth);
                updateWidthEstimate();
                StableView.this.columnWidthChanged(columnIndex, newWidth);
            }
        }
    };
    //scrollPane.getStyleClass().add("stable-view-scroll-pane");
    //scrollPane.setHbarPolicy(ScrollBarPolicy.AS_NEEDED);
    //scrollPane.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
    // VirtualFlow seems to get layout issues when empty, so don't
    // make it layout when empty:
    //scrollPane.managedProperty().bind(nonEmptyProperty);
    //scrollPane.visibleProperty().bind(nonEmptyProperty);
    //hbar = Utility.filterClass(scrollPane.getChildrenUnmodifiable().stream(), ScrollBar.class).filter(s -> s.getOrientation() == Orientation.HORIZONTAL).findFirst().get();
    //vbar = Utility.filterClass(scrollPane.getChildrenUnmodifiable().stream(), ScrollBar.class).filter(s -> s.getOrientation() == Orientation.VERTICAL).findFirst().get();
    

    headerItemsContainer = grid.makeColumnHeaders(colIndex -> Utility.mapList(FXUtility.mouse(this).getColumnOperations(columns.get(colIndex).columnId), ColumnOperation::makeMenuItem), col -> columns.get(col).makeHeaderContent());
    final Pane header = new BorderPane(headerItemsContainer.getNode());
    header.getStyleClass().add("stable-view-header");
    lineNumbers = grid.makeLineNumbers(rowIndex -> Utility.mapList(FXUtility.mouse(this).getRowOperationsForSingleRow(rowIndex), RowOperation::makeMenuItem));
    final BorderPane lineNumberWrapper = new BorderPane(lineNumbers.getNode());
    lineNumberWrapper.setPickOnBounds(false);
    lineNumberWrapper.getStyleClass().add("stable-view-side");
    placeholderLabel = new Label(messageWhenEmpty.getDisplayMessageNoColumns());
    placeholderLabel.getStyleClass().add(".stable-view-placeholderLabel");
    BorderPane placeholder = new BorderPane(placeholderLabel, null, null, null, headerItemsContainer.makeAddColumnButton());
    placeholder.visibleProperty().bind(nonEmptyProperty.not());
    placeholderLabel.setWrapText(true);

    Button topButton = makeScrollEndButton();
    topButton.getStyleClass().addAll("stable-view-button", "stable-view-button-top");
    topButton.setOnAction(e -> grid.scrollGroup.requestScrollBy(0.0, -Double.MAX_VALUE));

    Region topLeft = new Region();
    topLeft.getStyleClass().add("stable-view-top-left");
    FXUtility.forcePrefSize(topLeft);
    topLeft.setMaxHeight(Double.MAX_VALUE);
    topLeft.prefWidthProperty().bind(lineNumberWrapper.widthProperty());

    Pane top = new BorderPane(header, null, null, null, topLeft);
    top.getStyleClass().add("stable-view-top");

    Button leftButton = makeScrollEndButton();
    leftButton.getStyleClass().addAll("stable-view-button", "stable-view-button-left");
    leftButton.setOnAction(e -> grid.scrollGroup.requestScrollBy(-Double.MAX_VALUE, 0.0));
    Button rightButton = makeScrollEndButton();
    rightButton.getStyleClass().addAll("stable-view-button", "stable-view-button-right");
    rightButton.setOnAction(e -> grid.scrollGroup.requestScrollBy(-Double.MAX_VALUE, 0.0));

    Button bottomButton = makeScrollEndButton();
    bottomButton.getStyleClass().addAll("stable-view-button", "stable-view-button-bottom");
    bottomButton.setOnAction(e -> grid.scrollGroup.requestScrollBy(0.0, Double.MAX_VALUE));

    rightVertScroll = new BorderPane(vbar, topButton, null, bottomButton, null);
    BorderPane bottomHorizScroll = new BorderPane(hbar, null, rightButton, null, leftButton);
    
    contentLayout = new ContentLayout(grid.getNode(), top, rightVertScroll, bottomHorizScroll, lineNumberWrapper);
    contentLayout.visibleProperty().bind(nonEmptyProperty);
    stackPane = new StackPane(placeholder, contentLayout);
    // TODO figure out grid equivalent
    //headerItemsContainer.layoutXProperty().bind(virtualFlow.breadthOffsetProperty().map(d -> -d));
    placeholderLabel.managedProperty().bind(placeholderLabel.visibleProperty());
    stackPane.getStyleClass().add("stable-view");

    // Need to clip, otherwise scrolled-out part can still show up:
    Rectangle headerClip = new Rectangle();
    headerClip.widthProperty().bind(header.widthProperty());
    headerClip.heightProperty().bind(header.heightProperty());
    header.setClip(headerClip);

    Rectangle sideClip = new Rectangle();
    sideClip.widthProperty().bind(lineNumbers.getNode().widthProperty());
    sideClip.heightProperty().bind(lineNumbers.getNode().heightProperty());
    lineNumberWrapper.setClip(sideClip);

    FXUtility.bindPseudoclass(stackPane, "at-left", grid.atLeftProperty());
    FXUtility.bindPseudoclass(stackPane, "at-right", grid.atRightProperty());
    FXUtility.bindPseudoclass(stackPane, "at-top", grid.atTopProperty());
    FXUtility.bindPseudoclass(stackPane, "at-bottom", grid.atBottomProperty());


    widthEstimate = new SimpleDoubleProperty();
    FXUtility.addChangeListenerPlatformNN(lineNumbers.getNode().widthProperty(), x -> updateWidthEstimate());
    heightEstimate = new SimpleDoubleProperty();
    FXPlatformConsumer<Number> heightListener = x -> heightEstimate.set(grid.totalHeightEstimateProperty().getValue() + headerItemsContainer.getNode().getHeight());
    FXUtility.addChangeListenerPlatformNN(grid.totalHeightEstimateProperty(), heightListener);
    FXUtility.addChangeListenerPlatformNN(headerItemsContainer.getNode().heightProperty(), heightListener);
}

private List<ColumnOperation> getColumnOperations(ColumnId columnId)
{
    // TODO add before/after?
    List<ColumnOperation> r = new ArrayList<>();
    if (operations != null && operations.renameColumn.apply(columnId) != null)
    {
        r.add(new ColumnOperation("stableView.column.rename")
        {
            @Override
            public @OnThread(Tag.Simulation) void execute()
            {
                //TODO
            }
        });
    }

    if (operations != null && operations.deleteColumn.apply(columnId) != null)
    {
        r.add(new ColumnOperation("stableView.column.delete")
        {
            @Override
            public @OnThread(Tag.Simulation) void execute()
            {
                if (operations != null)
                {
                    @Nullable DeleteColumn deleteColumn = operations.deleteColumn.apply(columnId);
                    if (deleteColumn != null)
                        deleteColumn.deleteColumn(columnId);
                }
            }
        });
    }

    if (hideColumnOperation() != null)
    {
        r.add(new ColumnOperation("stableView.column.hide")
        {
            @Override
            public @OnThread(Tag.Simulation) void execute()
            {
                Platform.runLater(() -> {
                    @Nullable FXPlatformConsumer<ColumnId> hideColumnOperation = hideColumnOperation();
                    if (hideColumnOperation != null)
                        hideColumnOperation.consume(columnId);
                });
            }
        });
    }

    // Heavy-handed way to add a divider:
    r.add(new ColumnOperation("stableView.column.hide") // Arbitrary label, we are just a separator
    {
        @Override
        public @OnThread(Tag.Simulation) void execute()
        {
        }

        @Override
        public @OnThread(Tag.FXPlatform) MenuItem makeMenuItem()
        {
            return new SeparatorMenuItem();
        }
    });

    // TODO: quick transforms, e.g. sort-by, filter, summarise
    r.addAll(extraColumnOperations.apply(columnId));

    return r;
}

@Pure
protected @Nullable FXPlatformConsumer<ColumnId> hideColumnOperation()
{
    return null;
}

// Can be overridden by subclasses
protected void columnWidthChanged(int columnIndex, double newWidth)
{
}

private void updateWidthEstimate(@UnknownInitialization(Object.class) StableView this)
{
    if (widthEstimate != null && grid != null && lineNumbers != null)
    {
        widthEstimate.set(grid.sumColumnWidths(0, grid.getNumColumns()) + lineNumbers.getNode().getWidth());
    }
}

private static Button makeScrollEndButton()
{
    Button button = new Button("", makeButtonArrow());
    FXUtility.forcePrefSize(button);
    button.setPrefWidth(ScrollBarSkin.DEFAULT_WIDTH + 2);
    button.setPrefHeight(ScrollBarSkin.DEFAULT_WIDTH + 2);
    return button;
}

public void scrollToTopLeft()
{
    grid.scrollGroup.requestScrollBy(-Double.MAX_VALUE, -Double.MAX_VALUE);
}

private static Node makeButtonArrow()
{
    Region s = new Region();
    s.getStyleClass().add("stable-view-button-arrow");
    return s;
}

public Node getNode()
{
    return stackPane;
}

public void setPlaceholderText(@Localized String text)
{
    placeholderLabel.setText(text);
}

private void appendColumn()
{
    if (operations == null || operations.appendColumn == null)
        return; // Shouldn't happen, but if it does, append no longer valid

    withNewColumnDetails(operations.appendColumn);
}

protected void withNewColumnDetails(AppendColumn appendColumn)
{
    // Default behaviour is to do nothing
}

private void appendRow(int newRowIndex)
{
    if (operations == null || operations.appendRows == null)
        return; // Shouldn't happen, but if it does, append no longer valid

    @NonNull AppendRows append = operations.appendRows;
    Workers.onWorkerThread("Appending row", Priority.SAVE_ENTRY, () -> append.appendRows(1));
}

// See setColumns for description of append
public void clear(@Nullable TableOperations operations)
{
    // Clears rows, too:
    setColumnsAndRows(ImmutableList.of(), operations, null, i -> false);
}

// If append is empty, can't append.  If it's present, can append, and run
// this action after appending.
public void setColumnsAndRows(ImmutableList<ColumnDetails> columns, @Nullable TableOperations operations, @Nullable FXPlatformFunction<ColumnId, ImmutableList<ColumnOperation>> extraColumnActions, SimulationFunction<Integer, Boolean> isRowValid)
{
    final int curColumnAndRowSet = this.columnAndRowSet.incrementAndGet();
    this.operations = operations;
    this.extraColumnOperations = extraColumnActions == null ? (c -> ImmutableList.of()) : extraColumnActions;
    this.columns = columns;

    placeholderLabel.setText(columns.isEmpty() ? messageWhenEmpty.getDisplayMessageNoColumns() : messageWhenEmpty.getDisplayMessageNoRows());

    nonEmptyProperty.set(!columns.isEmpty());

    grid.setData(isRowValid,
        operations != null && operations.appendColumn != null ? FXUtility.mouse(this)::appendColumn : null,
        operations != null && operations.appendRows != null ? FXUtility.mouse(this)::appendRow : null,
        Doubles.toArray(Utility.replicate(columns.size(), DEFAULT_COLUMN_WIDTH)));

    scrollToTopLeft();
}

public DoubleExpression topHeightProperty()
{
    return headerItemsContainer.getNode().heightProperty();
}

// Column Index, Row Index
public ObjectExpression<@Nullable CellSelection> selectionProperty()
{
    return grid.selectionProperty();
}

public int getColumnCount()
{
    return columns.size();
}

//Makes the table editable.  Setting this to true only makes the columns editable if they themselves are marked
//as editable.  But setting it to false makes the columns read-only regardless of their marking.     
public void setEditable(boolean editable)
{
    this.tableEditable = editable;
}

public DoubleExpression widthEstimateProperty()
{
    // Includes line number width:
    return widthEstimate;
}

public ObservableDoubleValue heightEstimateProperty()
{
    // Includes header items height:
    return heightEstimate;
}

public void bindScroll(StableView scrollSrc, ScrollLock scrollLock)
{
    grid.bindScroll(scrollSrc.grid, scrollLock);
}

public void loadColumnWidths(double[] newColWidths)
{
    for (int i = 0; i < newColWidths.length; i++)
    {
        grid.setColumnWidth(i, newColWidths[i]);
    }

}

public double getColumnWidth(int columnIndex)
{
    return grid.getColumnWidth(columnIndex);
}

public void removedAddedRows(int startRowIncl, int removedRowsCount, int addedRowsCount)
{
    for (ColumnDetails column : columns)
    {
        column.columnHandler.removedAddedRows(startRowIncl, removedRowsCount, addedRowsCount);
    }

    if (removedRowsCount > 0)
        grid.removedRows(startRowIncl, removedRowsCount);

    if (addedRowsCount > 0)
        grid.addedRows(startRowIncl, addedRowsCount);
}

public void setRowLabelsVisible(boolean visible)
{
    lineNumberShowing.set(visible);
    contentLayout.requestLayout();
}

public void setVerticalScrollVisible(boolean visible)
{
    rightVertScroll.setVisible(visible);
    contentLayout.requestLayout();
}

private List<RowOperation> getRowOperationsForSingleRow(int rowIndex)
{
    List<RowOperation> r = new ArrayList<>();
    if (operations != null)
    {
        @NonNull TableOperations ops = this.operations;
        if (ops.insertRows != null)
        {
            @NonNull InsertRows insertRows = ops.insertRows;
            r.add(new RowOperation("stableView.row.insertBefore")
            {
                @Override
                public @OnThread(Tag.Simulation) void execute()
                {
                    insertRows.insertRows(rowIndex, 1);
                }
            });
            r.add(new RowOperation("stableView.row.insertAfter")
            {
                @Override
                public @OnThread(Tag.Simulation) void execute()
                {
                    insertRows.insertRows(rowIndex + 1, 1);
                }
            });
        }
        if (ops.deleteRows != null)
        {
            TableOperations.@NonNull DeleteRows deleteRows = ops.deleteRows;
            r.add(new RowOperation("stableView.row.delete")
            {
                @Override
                public @OnThread(Tag.Simulation) void execute()
                {
                    deleteRows.deleteRows(rowIndex, 1);
                }
            });
        }
    }
    return r;
}

public static class ScrollPosition
{
    private final int visRow;
    private final double offset;

    private ScrollPosition(int visRow, double offset)
    {
        this.visRow = visRow;
        this.offset = offset;
    }

}

public ScrollPosition saveScrollPositionFromTop()
{
    return new ScrollPosition(grid.getFirstDisplayRow(), grid.getFirstVisibleRowOffset());
}

public VirtScrollStrTextGrid _test_getGrid()
{
    return grid;
}

// This is similar to a BorderPane, but not quite.
private class ContentLayout extends Region
{
    private final Region dataGrid;
    // Note: colHeaders also includes the top-left item.
    private final Region colHeaders;
    private final Region rightVertScroll;
    private final Region bottomHorizScroll;
    private final Region rowHeaders;

    public ContentLayout(Region dataGrid, Pane colHeaders, BorderPane rightVertScroll, BorderPane bottomHorizScroll, BorderPane rowHeaders)
    {
        this.dataGrid = dataGrid;
        this.colHeaders = colHeaders;
        this.rightVertScroll = rightVertScroll;
        this.bottomHorizScroll = bottomHorizScroll;
        this.rowHeaders = rowHeaders;

        getChildren().addAll(dataGrid, colHeaders, rightVertScroll, bottomHorizScroll, rowHeaders);
    }

    @Override
    protected void layoutChildren()
    {
        double width = getWidth();
        double height = getHeight();

        // Column headers extends all the way across top at
        // its preferred height:
        double colHeaderHeight = colHeaders.prefHeight(width);

        // Right-hand side scroll is at far right, takes room from col header width:
        double rightScrollWidth = rightVertScroll.isVisible() ? rightVertScroll.prefWidth(height - colHeaderHeight) : 0.0;
        double colHeaderWidth = width - rightScrollWidth;
        colHeaders.resizeRelocate(0, 0, colHeaderWidth, colHeaderHeight);
        // Then row headers is all the way down the left beneath
        // that, at its preferred width:
        double rowHeaderWidth = lineNumberShowing.get() ? rowHeaders.prefWidth(height - colHeaderHeight) : 0.0;
        double bottomScrollHeight = bottomHorizScroll.prefHeight(width - rowHeaderWidth - rightScrollWidth);
        double rowHeaderHeight = height - colHeaderHeight - bottomScrollHeight;
        rowHeaders.resizeRelocate(0, colHeaderHeight, rowHeaderWidth, rowHeaderHeight);
        // Then scroll bars are at far sides, leaving
        // space in bottom left, but they also take space from headers and grid:


        rightVertScroll.resizeRelocate(width - rightScrollWidth, colHeaderHeight, rightScrollWidth, height - colHeaderHeight - bottomScrollHeight);
        bottomHorizScroll.resizeRelocate(rowHeaderWidth, height - bottomScrollHeight, width - rowHeaderWidth - rightScrollWidth, bottomScrollHeight);
        dataGrid.resizeRelocate(rowHeaderWidth, colHeaderHeight, width - rowHeaderWidth - rightScrollWidth, height - colHeaderHeight - bottomScrollHeight);
    }
}
*/
}
