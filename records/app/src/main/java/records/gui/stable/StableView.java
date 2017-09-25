package records.gui.stable;

import javafx.application.Platform;
import javafx.beans.binding.DoubleExpression;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.fxmisc.flowless.Cell;
import org.fxmisc.flowless.VirtualFlow;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.wellbehaved.event.EventPattern;
import org.fxmisc.wellbehaved.event.InputMap;
import org.fxmisc.wellbehaved.event.Nodes;
import records.data.TableOperations;
import records.data.TableOperations.AppendRows;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.FXPlatformRunnable;
import utility.Pair;
import utility.SimulationFunction;
import utility.Utility;
import utility.Workers;
import utility.Workers.Priority;
import utility.gui.FXUtility;
import utility.gui.GUI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A customised equivalent of TableView
 *
 * The internal node architecture is a bit complicated.
 *
 * .stable-view: StackPane
 *     .stable-view-placeholder: Label
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
    protected final ObservableList<@Nullable Void> items;
    private final VirtualFlow<@Nullable Void, StableRow> virtualFlow;
    private final VirtualFlow<@Nullable Void, LineNumber> lineNumbers;
    private final HBox headerItemsContainer;
    private final VirtualizedScrollPane scrollPane;
    private final Label placeholder;
    private final StackPane stackPane; // has scrollPane and placeholder as its children

    // An integer counter which tracks which instance of setColumnsAndRows we are on.  Used to
    // effectively cancel some queued run-laters if the method is called twice in quick succession.
    @OnThread(Tag.FXPlatform)
    private AtomicInteger columnAndRowSet = new AtomicInteger(0);
    private final BooleanProperty showingRowNumbers = new SimpleBooleanProperty(true);
    private final List<ColumnHandler> columns;
    private final List<DoubleProperty> columnSizes;
    private static final double EDGE_DRAG_TOLERANCE = 8;
    private static final double MIN_COLUMN_WIDTH = 30;
    private final List<HeaderItem> headerItems = new ArrayList<>();
    // A column is only editable if it is marked editable AND the table is editable:
    private boolean tableEditable = true;
    private final ScrollBar hbar;
    private final ScrollBar vbar;
    private final DropShadow leftDropShadow;
    private final DropShadow topDropShadow;
    private final DropShadow topLeftDropShadow;
    // Table will start in top left, so these should start as true:
    private boolean atTop = true;
    private boolean atLeft = true;
    private final ObjectProperty<Pair<Integer, Double>> topShowingCellProperty = new SimpleObjectProperty<>(new Pair<>(0, 0.0));

    private final ObjectProperty<@Nullable Pair<Integer, Integer>> focusedCell = new SimpleObjectProperty<>(null);
    private @Nullable TableOperations operations;
    private final SimpleBooleanProperty nonEmptyProperty = new SimpleBooleanProperty(false);


    public StableView()
    {
        // We could make a dummy list which keeps track of size, but doesn't
        // actually bother storing the nulls:
        items = FXCollections.observableArrayList();
        headerItemsContainer = new HBox();
        final Pane header = new Pane(headerItemsContainer);
        header.getStyleClass().add("stable-view-header");
        virtualFlow = VirtualFlow.<@Nullable Void, StableRow>createVertical(items, this::makeCell);
        scrollPane = new VirtualizedScrollPane<VirtualFlow<@Nullable Void, StableRow>>(virtualFlow);
        scrollPane.getStyleClass().add("stable-view-scroll-pane");
        scrollPane.setHbarPolicy(ScrollBarPolicy.AS_NEEDED);
        scrollPane.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
        // VirtualFlow seems to get layout issues when empty, so don't
        // make it layout when empty:
        scrollPane.managedProperty().bind(nonEmptyProperty);
        scrollPane.visibleProperty().bind(nonEmptyProperty);
        hbar = Utility.filterClass(scrollPane.getChildrenUnmodifiable().stream(), ScrollBar.class).filter(s -> s.getOrientation() == Orientation.HORIZONTAL).findFirst().get();
        vbar = Utility.filterClass(scrollPane.getChildrenUnmodifiable().stream(), ScrollBar.class).filter(s -> s.getOrientation() == Orientation.VERTICAL).findFirst().get();
        lineNumbers = VirtualFlow.createVertical(items, x -> new LineNumber());
        // Need to prevent independent scrolling on the line numbers.
        // Instead we forward the scroll to the whole table:
        lineNumbers.addEventFilter(ScrollEvent.SCROLL, e -> {
            FXUtility.mouse(this).forwardedScrollEvent(e);
            e.consume();
        });
        final BorderPane lineNumberWrapper = new BorderPane(lineNumbers);
        lineNumberWrapper.setPickOnBounds(false);
        lineNumberWrapper.getStyleClass().add("stable-view-side");
        lineNumberWrapper.visibleProperty().bind(nonEmptyProperty);
        placeholder = new Label("<Empty>");
        placeholder.getStyleClass().add(".stable-view-placeholder");
        placeholder.visibleProperty().bind(nonEmptyProperty.not());
        
        Button topButton = new Button("", makeButtonArrow());
        topButton.getStyleClass().addAll("stable-view-button", "stable-view-button-top");
        topButton.setOnAction(e -> virtualFlow.scrollYToPixel(0));
        FXUtility.forcePrefSize(topButton);
        topButton.prefWidthProperty().bind(vbar.widthProperty());
        topButton.prefHeightProperty().bind(topButton.prefWidthProperty());
        BorderPane.setAlignment(topButton, Pos.BOTTOM_RIGHT);
        topButton.visibleProperty().bind(vbar.visibleProperty());
        Region topLeft = new Region();
        topLeft.getStyleClass().add("stable-view-top-left");
        FXUtility.forcePrefSize(topLeft);
        topLeft.setMaxHeight(Double.MAX_VALUE);
        topLeft.prefWidthProperty().bind(lineNumberWrapper.widthProperty());
        topLeft.visibleProperty().bind(nonEmptyProperty);
        Pane topButtonWrapper = GUI.wrap(topButton, "stable-button-top-wrapper");
        topButtonWrapper.visibleProperty().bind(vbar.visibleProperty());

        Pane top = new BorderPane(header, null, topButtonWrapper, null, topLeft);
        top.getStyleClass().add("stable-view-top");

        Button leftButton = new Button("", makeButtonArrow());
        leftButton.getStyleClass().addAll("stable-view-button", "stable-view-button-left");
        leftButton.setOnAction(e -> virtualFlow.scrollXToPixel(0));
        leftButton.prefHeightProperty().bind(hbar.heightProperty());
        leftButton.prefWidthProperty().bind(leftButton.prefHeightProperty());
        FXUtility.forcePrefSize(leftButton);
        BorderPane.setAlignment(leftButton, Pos.BOTTOM_RIGHT);
        leftButton.visibleProperty().bind(nonEmptyProperty);
        Pane leftButtonWrapper = GUI.wrap(leftButton, "stable-button-left-wrapper");
        leftButtonWrapper.visibleProperty().bind(hbar.visibleProperty());
        Pane left = new BorderPane(lineNumberWrapper, null, null, leftButtonWrapper, null);
        left.setPickOnBounds(false);
        left.getStyleClass().add("stable-view-left");

        Button bottomButton = new Button("", makeButtonArrow());
        bottomButton.getStyleClass().addAll("stable-view-button", "stable-view-button-bottom");
        bottomButton.setOnAction(e -> virtualFlow.scrollYToPixel(Double.MAX_VALUE));
        FXUtility.forcePrefSize(bottomButton);
        bottomButton.prefWidthProperty().bind(vbar.widthProperty());
        bottomButton.prefHeightProperty().bind(bottomButton.prefWidthProperty());
        StackPane.setAlignment(bottomButton, Pos.BOTTOM_RIGHT);
        bottomButton.visibleProperty().bind(vbar.visibleProperty());
        
        stackPane = new StackPane(placeholder, new BorderPane(scrollPane, top, null, null, left), bottomButton);
        headerItemsContainer.layoutXProperty().bind(virtualFlow.breadthOffsetProperty().map(d -> -d));
        placeholder.managedProperty().bind(placeholder.visibleProperty());
        stackPane.getStyleClass().add("stable-view");
        columns = new ArrayList<>();
        columnSizes = new ArrayList<>();

        Rectangle headerClip = new Rectangle();
        headerClip.widthProperty().bind(header.widthProperty());
        headerClip.heightProperty().bind(header.heightProperty().add(10.0));
        header.setClip(headerClip);

        Rectangle sideClip = new Rectangle();
        sideClip.widthProperty().bind(lineNumbers.widthProperty().add(10.0));
        sideClip.heightProperty().bind(lineNumbers.heightProperty());
        lineNumberWrapper.setClip(sideClip);

        // CSS doesn't let us have different width to height, which we need to prevent
        // visible curling in at the edges:
        topDropShadow = new DropShadow();
        topDropShadow.setBlurType(BlurType.GAUSSIAN);
        topDropShadow.setColor(Color.hsb(0, 0, 0.5, 0.7));
        topDropShadow.setOffsetY(2);
        topDropShadow.setHeight(8);
        topDropShadow.setWidth(0);
        topDropShadow.setSpread(0.4);
        leftDropShadow = new DropShadow();
        leftDropShadow.setBlurType(BlurType.GAUSSIAN);
        leftDropShadow.setColor(Color.hsb(0, 0, 0.5, 0.7));
        leftDropShadow.setHeight(0);
        leftDropShadow.setWidth(8);
        leftDropShadow.setOffsetX(2);
        leftDropShadow.setSpread(0.4);
        // Copy of topDropShadow, but with input of leftDropShadow:
        topLeftDropShadow = new DropShadow();
        topLeftDropShadow.setBlurType(BlurType.GAUSSIAN);
        topLeftDropShadow.setColor(Color.hsb(0, 0, 0.5, 0.7));
        topLeftDropShadow.setOffsetX(2);
        topLeftDropShadow.setOffsetY(2);
        topLeftDropShadow.setHeight(8);
        topLeftDropShadow.setWidth(8);
        topLeftDropShadow.setSpread(0.4);
        
        
        FXUtility.listen(virtualFlow.visibleCells(), c -> {
            if (!c.getList().isEmpty())
            {
                StableRow firstVisible = c.getList().get(0);
                int firstVisibleRowIndex = firstVisible.getCurRowIndex();
                StableRow lastVisible = c.getList().get(c.getList().size() - 1);
                int lastVisibleRowIndex = lastVisible.getCurRowIndex();
                // These values are negative, so negate to deal with them as positive:
                double topY = -virtualFlow.cellToViewport(firstVisible, 0, 0).getY();
                double bottomY = -virtualFlow.cellToViewport(lastVisible, 0, lastVisible.getNode().getHeight() - 4).getY();
                //FXUtility.setPseudoclass(header, "pinned", topY >= 5 || firstVisibleRowIndex > 0);
                atTop = topY < 5 && firstVisibleRowIndex == 0;
                updateShadows(header, lineNumberWrapper, topLeft);
                FXUtility.setPseudoclass(stackPane, "at-top", topY < 1 && firstVisibleRowIndex == 0);
                FXUtility.setPseudoclass(stackPane, "at-bottom", lastVisibleRowIndex == items.size() - 1 && bottomY < virtualFlow.getHeight());
                lineNumbers.showAtOffset(firstVisibleRowIndex, topY);
                topShowingCellProperty.set(new Pair<>(firstVisibleRowIndex, topY));
                // TODO call listener to update visible cells (DisplayCache needs this)
            }
        });

        
        FXUtility.addChangeListenerPlatformNN(virtualFlow.breadthOffsetProperty(), d -> {
            //FXUtility.setPseudoclass(lineNumbers, "pinned", d >= 5);
            atLeft = d < 5;
            updateShadows(header, lineNumberWrapper, topLeft);
            FXUtility.setPseudoclass(stackPane, "at-left", d < 1);
            FXUtility.setPseudoclass(stackPane, "at-right", d >= headerItemsContainer.getWidth() - virtualFlow.getWidth() - 3);
        });
    }

    public void scrollToTopLeft()
    {
        virtualFlow.scrollYToPixel(0);
        virtualFlow.scrollXToPixel(0);
    }

    @RequiresNonNull({"topDropShadow", "leftDropShadow", "topLeftDropShadow"})
    private void updateShadows(@UnknownInitialization(Object.class) StableView this, Node top, Node left, Node topLeft)
    {
        if (!atTop)
        {
            top.setEffect(topDropShadow);
            topLeft.setEffect(atLeft ? topDropShadow : topLeftDropShadow);
        }
        else
        {
            top.setEffect(null);
            topLeft.setEffect(atLeft ? null : leftDropShadow);
        }

        left.setEffect(atLeft ? null : leftDropShadow);
    }

    private static Node makeButtonArrow()
    {
        Region s = new Region();
        s.getStyleClass().add("stable-view-button-arrow");
        return s;
    }

    private StableRow makeCell(@UnknownInitialization(Object.class) StableView this, @Nullable Object data)
    {
        return new StableRow();
    }

    public Node getNode()
    {
        return stackPane;
    }

    public void setPlaceholderText(@Localized String text)
    {
        placeholder.setText(text);
    }

    /**
     * A pair with the row index and Y-offset of the top showing cell,
     * listen to this if you want to act on scroll.
     * @return
     */
    public ObjectExpression<Pair<Integer, Double>> topShowingCell()
    {
        return topShowingCellProperty;
    }

    @EnsuresNonNullIf(expression = {"operations", "operations.appendRows"}, result=true)
    private boolean isAppendRow(int index)
    {
        return operations != null && operations.appendRows != null && index == items.size() - 1;
    }

    private OptionalInt getLastContentRowIndex()
    {
        int last = items.size() - 1;
        if (isAppendRow(last))
            last -= 1;
        if (last >= 0)
            return OptionalInt.of(last);
        else
            return OptionalInt.empty();
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
        setColumnsAndRows(Collections.emptyList(), operations, i -> false);
    }

    // If append is empty, can't append.  If it's present, can append, and run
    // this action after appending.
    public void setColumnsAndRows(List<Pair<String, ColumnHandler>> columns, @Nullable TableOperations operations, SimulationFunction<Integer, Boolean> isRowValid)
    {
        final int curColumnAndRowSet = this.columnAndRowSet.incrementAndGet();
        this.operations = operations;
        // Important to clear the items, as we need to make new cells
        // which will have the updated number of columns
        items.clear();
        this.columns.clear();
        this.columns.addAll(Utility.mapList(columns, p -> p.getSecond()));
        this.columnSizes.clear();
        for (int i = 0; i < columns.size(); i++)
        {
            columnSizes.add(new SimpleDoubleProperty(100.0));
            ColumnHandler colHandler = columns.get(i).getSecond();
            FXUtility.addChangeListenerPlatformNN(columnSizes.get(i), d -> colHandler.columnResized(d.doubleValue()));
        }

        headerItemsContainer.getChildren().clear();
        headerItems.clear();
        for (int i = 0; i < columns.size(); i++)
        {
            Pair<String, ColumnHandler> column = columns.get(i);
            HeaderItem headerItem = new HeaderItem(column.getFirst(), i);
            headerItemsContainer.getChildren().add(headerItem);
            headerItems.add(headerItem);
        }

        nonEmptyProperty.set(!columns.isEmpty());

        scrollToTopLeft();

        boolean addAppendRow = operations != null && operations.appendRows != null;
        Workers.onWorkerThread("Calculating table rows", Priority.FETCH, () -> {
            int toAdd = 0;
            try
            {
                outer:
                for (int i = 0; i < 10; i++)
                {
                    // Cancel if a latet setColumnsAndRows call has occurred:
                    if (curColumnAndRowSet != this.columnAndRowSet.get())
                        return;

                    for (int j = 0; j < 10; j++)
                    {
                        if (isRowValid.apply(i * 10 + j))
                        {
                            toAdd++;
                        }
                        else
                        {
                            break outer;
                        }
                    }
                    int toAddFinal = toAdd;
                    Platform.runLater(() ->
                    {
                        // Cancel if a latet setColumnsAndRows call has occurred:
                        if (curColumnAndRowSet != this.columnAndRowSet.get())
                            return;

                        for (int k = 0; k < toAddFinal; k++)
                        {
                            items.add(null);
                        }
                    });
                    toAdd = 0;
                }
            }
            catch (InternalException | UserException e)
            {
                Utility.log(e);
                // TODO display somewhere?
            }
            // Add final row for the "+" buttons
            if (addAppendRow)
                toAdd += 1;
            int toAddFinal = toAdd;
            Platform.runLater(() -> {
                // Cancel if a latet setColumnsAndRows call has occurred:
                if (curColumnAndRowSet != this.columnAndRowSet.get())
                    return;

                for (int k = 0; k < toAddFinal; k++)
                {
                    items.add(null);
                }
            });
        });

        //TODO store it for fetching more
    }

    public DoubleExpression topHeightProperty()
    {
        return headerItemsContainer.heightProperty();
    }

    // Column Index, Row Index
    public ObjectExpression<@Nullable Pair<Integer, Integer>> focusedCellProperty()
    {
        return focusedCell;
    }

    public int getColumnCount()
    {
        return columns.size();
    }

    public void scrollBy(double x, double y)
    {
        virtualFlow.scrollXBy(x);
        virtualFlow.scrollYBy(y);
    }

    public void forwardedScrollEvent(ScrollEvent se)
    {
        scrollBy(-se.getDeltaX(), -se.getDeltaY());
    }

    /**
     * Makes the table editable.  Setting this to true only makes the columns editable if they themselves are marked
     * as editable.  But setting it to false makes the columns read-only regardless of their marking.
     */
    public void setEditable(boolean editable)
    {
        this.tableEditable = editable;
    }

    private class HeaderItem extends Label
    {
        private final int itemIndex;
        private double offsetFromEdge;
        private boolean draggingLeftEdge;
        private boolean dragging = false;

        public HeaderItem(String name, int itemIndex)
        {
            super(name);
            this.itemIndex = itemIndex;
            this.getStyleClass().add("stable-view-header-item");
            this.setMinWidth(Region.USE_PREF_SIZE);
            this.setMaxWidth(Region.USE_PREF_SIZE);
            this.prefWidthProperty().bind(columnSizes.get(itemIndex));
            this.setOnMouseMoved(e -> {
                boolean nearEdge = e.getX() < EDGE_DRAG_TOLERANCE || e.getX() >= this.getWidth() - EDGE_DRAG_TOLERANCE;
                this.setCursor(dragging || nearEdge ? Cursor.H_RESIZE : null);
            });
            this.setOnMousePressed(e -> {
                if (this.getCursor() != null)
                {
                    // We always prefer to drag the right edge, as if
                    // you squish all the columns, it gives you a way out of dragging
                    // all right edges (whereas leftmost edge cannot be dragged):
                    if (e.getX() >= this.getWidth() - EDGE_DRAG_TOLERANCE)
                    {
                        dragging = true;
                        draggingLeftEdge = false;
                        // Positive offset:
                        offsetFromEdge = getWidth() - e.getX();
                    }
                    else if (itemIndex > 0 && e.getX() < EDGE_DRAG_TOLERANCE)
                    {
                        dragging = true;
                        draggingLeftEdge = true;
                        offsetFromEdge = e.getX();
                    }
                }
                e.consume();
            });
            this.setOnMouseDragged(e -> {
                // Should be true:
                if (dragging)
                {
                    if (draggingLeftEdge)
                    {
                        // Have to adjust size of column to our left
                        headerItems.get(itemIndex - 1).setRightEdgeToSceneX(e.getSceneX() - offsetFromEdge);
                    }
                    else
                    {
                        columnSizes.get(itemIndex).set(Math.max(MIN_COLUMN_WIDTH, e.getX() + offsetFromEdge));
                    }
                }
                e.consume();
            });
            this.setOnMouseReleased(e -> {
                dragging = false;
                e.consume();
            });
        }

        private void setRightEdgeToSceneX(double sceneX)
        {
            columnSizes.get(itemIndex).set(Math.max(MIN_COLUMN_WIDTH, sceneX - localToScene(getBoundsInLocal()).getMinX()));
        }
    }

    @OnThread(Tag.FXPlatform)
    private class StableRow implements Cell<@Nullable Void, Region>
    {
        private final HBox hBox = new HBox();
        private final ArrayList<Pane> cells = new ArrayList<>();
        private final ArrayList<@Nullable InputMap<?>> cellItemInputMaps = new ArrayList<>();
        private int curRowIndex = -1;
        private final List<Button> appendButtons = new ArrayList<>();

        public StableRow()
        {
            hBox.getStyleClass().add("stable-view-row");
            for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++)
            {
                final Pane pane = new StackPane();
                pane.getStyleClass().add("stable-view-row-cell");
                if (columnIndex == 0)
                {
                    FXUtility.onceNotNull(pane.sceneProperty(), s -> {pane.requestFocus();});
                }
                int columnIndexFinal = columnIndex;
                FXUtility.addChangeListenerPlatformNN(pane.focusedProperty(), focus -> {
                    FXUtility.setPseudoclass(pane, "focused-cell", focus);
                    markFocusCross(columnIndexFinal, curRowIndex, focus);
                    focusedCell.set(focus ? new Pair<>(columnIndexFinal, curRowIndex) : null);
                });
                FXUtility.forcePrefSize(pane);
                pane.prefWidthProperty().bind(columnSizes.get(columnIndex));
                pane.addEventFilter(MouseEvent.ANY, e -> {
                    if (e.getEventType() != MouseEvent.MOUSE_CLICKED)
                    {
                        boolean editing = curRowIndex >= 0 && columns.get(columnIndexFinal).editHasFocus(curRowIndex);
                        if (!editing && !pane.isFocused() && !isAppendRow(curRowIndex))
                        {
                            // Hide events from inner panel if we're not yet editing:
                            e.consume();
                        }
                    }
                });
                pane.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
                    if (e.getEventType() == MouseEvent.MOUSE_CLICKED)
                        System.err.println("Detected click in filter");
                    boolean editing = curRowIndex >= 0 && columns.get(columnIndexFinal).editHasFocus(curRowIndex);

                    if (e.getEventType() == MouseEvent.MOUSE_CLICKED)
                        System.err.println("Editing: " + editing + " pane focus: " + pane.isFocused());
                    if (editing || isAppendRow(curRowIndex))
                    {
                        return; // Not for us to take mouse click
                    }
                    else if (!pane.isFocused() && e.getClickCount() == 1 && (e.getButton() == MouseButton.PRIMARY || e.getButton() == MouseButton.SECONDARY) && curRowIndex >= 0)
                    {
                        pane.requestFocus();
                        e.consume();
                    }
                });
                Nodes.addInputMap(pane, InputMap.sequence(
                    InputMap.<Event, KeyEvent>consume(EventPattern.keyPressed(KeyCode.END), e -> {
                        int lastContentRowIndex = getLastContentRowIndex().orElse(-1);
                        if (lastContentRowIndex < 0)
                            return;
                        virtualFlow.show(lastContentRowIndex);
                        Optional<StableRow> lastRow = virtualFlow.getCellIfVisible(lastContentRowIndex);
                        if (!lastRow.isPresent())
                        {
                            virtualFlow.layout();
                            lastRow = virtualFlow.getCellIfVisible(lastContentRowIndex);
                        }

                        if (lastRow.isPresent())
                        {
                            lastRow.get().cells.get(columnIndexFinal).requestFocus();
                        }
                        e.consume();
                    }),
                    InputMap.<Event, KeyEvent>consume(EventPattern.keyPressed(KeyCode.ENTER), e -> {
                        columns.get(columnIndexFinal).edit(curRowIndex, null);
                        e.consume();
                    })
                ));
                cells.add(pane);
                cellItemInputMaps.add(null);
            }
            hBox.getChildren().setAll(cells);
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public boolean isReusable()
        {
            // TODO set this to true, but in that case we also need to handle number of columns changing
            // under our feet.
            return false;
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public void updateIndex(int rowIndex)
        {
            if (rowIndex != curRowIndex || appendButtons.isEmpty() != !isAppendRow(rowIndex))
            {
                appendButtons.clear();
                curRowIndex = rowIndex;

                for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++)
                {
                    if (isAppendRow(curRowIndex))
                    {
                        Button button = GUI.button("stableView.append", () -> appendRow(rowIndex), "stable-view-row-append-button");
                        cells.get(columnIndex).getChildren().setAll(button);
                        appendButtons.add(button);
                        FXUtility.addChangeListenerPlatformNN(button.hoverProperty(), hover -> {
                            for (Button other : appendButtons)
                            {
                                if (other != button)
                                {
                                    FXUtility.setPseudoclass(other, "hover", hover);
                                }
                            }
                        });
                    }
                    else
                    {
                        ColumnHandler column = columns.get(columnIndex);
                        int columnIndexFinal = columnIndex;
                        StableRow firstVisibleRow = virtualFlow.visibleCells().get(0);
                        StableRow lastVisibleRow = virtualFlow.visibleCells().get(virtualFlow.visibleCells().size() - 1);
                        column.fetchValue(rowIndex, focus -> cellContentFocusChanged(columnIndexFinal, focus), () -> cells.get(columnIndexFinal).requestFocus(), cellContent ->
                        {
                            Pane cell = cells.get(columnIndexFinal);
                            cell.getChildren().setAll(cellContent);

                            cellContent.addEventFilter(ScrollEvent.SCROLL, e -> {
                                FXUtility.mouse(StableView.this).forwardedScrollEvent(e);
                                e.consume();
                            });
                        }, firstVisibleRow.curRowIndex, lastVisibleRow.curRowIndex);

                        // Swap old input map (if any) for new (if any)
                        if (cellItemInputMaps.get(columnIndex) != null)
                            Nodes.removeInputMap(cells.get(columnIndex), cellItemInputMaps.get(columnIndex));
                        @Nullable InputMap<?> inputMap = column.getInputMapForParent(rowIndex);
                        cellItemInputMaps.set(columnIndex, inputMap);
                        if (inputMap != null)
                            Nodes.addInputMap(cells.get(columnIndex), inputMap);
                    }
                }
            }
        }

        private void cellContentFocusChanged(int columnIndex, boolean focused)
        {
            FXUtility.setPseudoclass(cells.get(columnIndex), "editing-cell", focused);
        }

        // You have to override this to avoid the UnsupportedOperationException
        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public void updateItem(@Nullable Void item)
        {
            // Everything is actually done in updateIndex
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public Region getNode()
        {
            return hBox;
        }

        public int getCurRowIndex()
        {
            return curRowIndex;
        }
    }

    /**
     * Marks the whole row and column lightly to indicate the real focused cell.
     */
    private void markFocusCross(int columnIndex, int rowIndex, boolean gotFocus)
    {
        for (StableRow stableRow : virtualFlow.visibleCells())
        {
            boolean correctRow = stableRow.curRowIndex == rowIndex;
            for (int cellCol = 0; cellCol < stableRow.cells.size(); cellCol++)
            {
                boolean correctColumn = cellCol == columnIndex;
                // XOR; don't want to set it for actually focused cell:
                if (correctRow != correctColumn)
                {
                    FXUtility.setPseudoclass(stableRow.cells.get(cellCol), "focused-row-col", gotFocus);
                }
            }
        }
    }

    @OnThread(Tag.FXPlatform)
    public static interface ColumnHandler
    {
        // Called to fetch a value.  Once available, receiver should be called.
        // Until then it will be blank.  You can call receiver multiple times though,
        // so you can just call it with a placeholder before returning.
        public void fetchValue(int rowIndex, FXPlatformConsumer<Boolean> focusListener, FXPlatformRunnable relinquishFocus, FXPlatformConsumer<Region> setCellContent, int firstVisibleRowIndexIncl, int lastVisibleRowIndexIncl);

        // Called when the column gets resized (graphically).  Width is in pixels
        public void columnResized(double width);

        // Should return an InputMap, if any, to put on the parent node of the display.
        // Useful if you want to be able to press keys directly without beginning editing
        public @Nullable InputMap<?> getInputMapForParent(int rowIndex);

        // Called when the user initiates an error, either by double-clicking
        // (in which case the point is passed) or by pressing enter (in which case
        // point is null).
        // Will only be called if isEditable returns true
        public void edit(int rowIndex, @Nullable Point2D scenePoint);

        // Can this column be edited?
        public boolean isEditable();

        // Is this column value currently being edited?
        public boolean editHasFocus(int rowIndex);
    }

    @OnThread(Tag.FXPlatform)
    private class LineNumber implements Cell<@Nullable Void, Node>
    {
        private final Label label = new Label();
        private final Pane labelWrapper = new StackPane(label);
        private int curRowIndex;

        public LineNumber()
        {
            FXUtility.forcePrefSize(labelWrapper);
            labelWrapper.getStyleClass().add("stable-view-row-number");
            labelWrapper.setOnContextMenuRequested(e -> {
                ContextMenu menu = new ContextMenu();
                menu.getItems().addAll(Utility.mapList(getRowOperationsForSingleRow(curRowIndex), RowOperation::makeMenuItem));
                menu.show(labelWrapper, e.getScreenX(), e.getScreenY());
            });
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public boolean isReusable()
        {
            return true;
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public void updateIndex(int index)
        {
            curRowIndex = index;
            label.setText(isAppendRow(index) ? "" : Integer.toString(index));
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public Node getNode()
        {
            return labelWrapper;
        }

        // You have to override this to avoid the UnsupportedOperationException
        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public void updateItem(@Nullable Void item)
        {
            // Everything is actually done in updateIndex
        }
    }

    private List<RowOperation> getRowOperationsForSingleRow(int rowIndex)
    {
        List<RowOperation> r = new ArrayList<>();
        if (operations != null)
        {
            if (operations.deleteRows != null)
            {
                TableOperations.@NonNull DeleteRows deleteRows = operations.deleteRows;
                r.add(new RowOperation()
                {
                    @Override
                    public @OnThread(Tag.FXPlatform) @LocalizableKey String getNameKey()
                    {
                        return "stableView.row.delete";
                    }

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

    public void resizeColumn(int columnIndex, double size)
    {
        columnSizes.get(columnIndex).set(size);
    }
}
