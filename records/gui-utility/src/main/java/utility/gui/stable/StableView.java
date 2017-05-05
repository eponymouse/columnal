package utility.gui.stable;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.InnerShadow;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.checkerframework.checker.i18n.qual.Localized;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.fxmisc.flowless.Cell;
import org.fxmisc.flowless.VirtualFlow;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.reactfx.value.Val;
import records.error.InternalException;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.SimulationFunction;
import utility.Utility;
import utility.Workers;
import utility.gui.FXUtility;
import utility.gui.GUI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
 */

@OnThread(Tag.FXPlatform)
public class StableView
{
    private final ObservableList<@Nullable Object> items;
    private final VirtualFlow<@Nullable Object, StableRow> virtualFlow;
    private final VirtualFlow<@Nullable Object, LineNumber> lineNumbers;
    private final HBox headerItemsContainer;
    private final VirtualizedScrollPane scrollPane;
    private final Label placeholder;
    private final StackPane stackPane; // has scrollPane and placeholder as its children

    private final BooleanProperty showingRowNumbers = new SimpleBooleanProperty(true);
    private final List<ValueFetcher> columns;
    private final List<DoubleProperty> columnSizes;
    private static final double EDGE_DRAG_TOLERANCE = 8;
    private static final double MIN_COLUMN_WIDTH = 30;
    private final List<HeaderItem> headerItems = new ArrayList<>();
    private final ScrollBar hbar;
    private final ScrollBar vbar;
    private final DropShadow leftDropShadow;
    private final InnerShadow leftInnerShadow;
    private final DropShadow topDropShadow;
    private final InnerShadow topInnerShadow;
    private boolean atTop;
    private boolean atLeft;


    public StableView()
    {
        items = FXCollections.observableArrayList();
        headerItemsContainer = new HBox();
        final Pane header = new Pane(headerItemsContainer);
        header.getStyleClass().add("stable-view-header");
        virtualFlow = VirtualFlow.<@Nullable Object, StableRow>createVertical(items, this::makeCell);
        scrollPane = new VirtualizedScrollPane<VirtualFlow<@Nullable Object, StableRow>>(virtualFlow);
        scrollPane.getStyleClass().add("stable-view-scroll-pane");
        scrollPane.setHbarPolicy(ScrollBarPolicy.AS_NEEDED);
        scrollPane.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
        hbar = Utility.filterClass(scrollPane.getChildrenUnmodifiable().stream(), ScrollBar.class).filter(s -> s.getOrientation() == Orientation.HORIZONTAL).findFirst().get();
        vbar = Utility.filterClass(scrollPane.getChildrenUnmodifiable().stream(), ScrollBar.class).filter(s -> s.getOrientation() == Orientation.VERTICAL).findFirst().get();
        lineNumbers = VirtualFlow.createVertical(items, x -> new LineNumber());
        final BorderPane lineNumberWrapper = new BorderPane(lineNumbers);
        lineNumberWrapper.getStyleClass().add("stable-view-side");
        placeholder = new Label("<Empty>");
        placeholder.getStyleClass().add(".stable-view-placeholder");
        
        Button topButton = new Button("", makeButtonArrow());
        topButton.getStyleClass().add("stable-view-button-top");
        topButton.setOnAction(e -> virtualFlow.scrollYToPixel(0));
        FXUtility.forcePrefSize(topButton);
        topButton.prefWidthProperty().bind(vbar.widthProperty());
        topButton.prefHeightProperty().bind(topButton.prefWidthProperty());
        BorderPane.setAlignment(topButton, Pos.BOTTOM_RIGHT);
        Region topLeft = new Region();
        topLeft.getStyleClass().add("stable-view-top-left");
        FXUtility.forcePrefSize(topLeft);
        topLeft.setMaxHeight(Double.MAX_VALUE);
        topLeft.prefWidthProperty().bind(lineNumberWrapper.widthProperty());
        Pane top = new BorderPane(header, null, GUI.wrap(topButton, "stable-button-top-wrapper"), null, topLeft);
        top.getStyleClass().add("stable-view-top");

        Button leftButton = new Button("", makeButtonArrow());
        leftButton.getStyleClass().add("stable-view-button-left");
        leftButton.setOnAction(e -> virtualFlow.scrollXToPixel(0));
        leftButton.prefHeightProperty().bind(hbar.heightProperty());
        leftButton.prefWidthProperty().bind(leftButton.prefHeightProperty());
        FXUtility.forcePrefSize(leftButton);
        BorderPane.setAlignment(leftButton, Pos.BOTTOM_RIGHT);
        Pane left = new BorderPane(lineNumberWrapper, null, null, GUI.wrap(leftButton, "stable-button-left-wrapper"), null);
        left.getStyleClass().add("stable-view-left");
        
        stackPane = new StackPane(placeholder, new BorderPane(scrollPane, top, null, null, left));
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
        topDropShadow.setWidth(12);
        topDropShadow.setSpread(0.4);
        leftInnerShadow = new InnerShadow();
        leftInnerShadow.setBlurType(BlurType.GAUSSIAN);
        leftInnerShadow.setColor(Color.hsb(0, 0, 0.5, 0.7));
        leftInnerShadow.setChoke(0.4);
        leftInnerShadow.setOffsetY(2);
        leftInnerShadow.setHeight(8);
        leftInnerShadow.setWidth(0);
        leftDropShadow = new DropShadow();
        leftDropShadow.setBlurType(BlurType.GAUSSIAN);
        leftDropShadow.setColor(Color.hsb(0, 0, 0.5, 0.7));
        leftDropShadow.setHeight(12);
        leftDropShadow.setWidth(8);
        leftDropShadow.setOffsetX(2);
        leftDropShadow.setSpread(0.4);
        topInnerShadow = new InnerShadow();
        topInnerShadow.setBlurType(BlurType.GAUSSIAN);
        topInnerShadow.setColor(Color.hsb(0, 0, 0.5, 0.7));
        topInnerShadow.setChoke(0.4);
        topInnerShadow.setOffsetX(2);
        topInnerShadow.setHeight(0);
        topInnerShadow.setWidth(8);

        
        FXUtility.listen(virtualFlow.visibleCells(), c -> {
            if (!c.getList().isEmpty())
            {
                int rowIndex = c.getList().get(0).getCurRowIndex();
                double y = virtualFlow.cellToViewport(c.getList().get(0), 0, 0).getY();
                //FXUtility.setPseudoclass(header, "pinned", y >= 5 || rowIndex > 0);
                atTop = y < 5 && rowIndex == 0;
                updateShadows(header, lineNumberWrapper);
                FXUtility.setPseudoclass(stackPane, "at-top", y == 0 && rowIndex == 0);
                lineNumbers.showAtOffset(rowIndex, y);
            }
        });

        
        FXUtility.addChangeListenerPlatformNN(virtualFlow.breadthOffsetProperty(), d -> {
            //FXUtility.setPseudoclass(lineNumbers, "pinned", d >= 5);
            System.err.println("Breadth: " + d);
            atLeft = d < 5;
            updateShadows(header, lineNumberWrapper);
        });
    }

    @RequiresNonNull({"topDropShadow", "topInnerShadow", "leftDropShadow", "leftInnerShadow"})
    private void updateShadows(@UnknownInitialization(Object.class) StableView this, Node top, Node left)
    {
        if (!atTop)
        {
            top.setEffect(topDropShadow);
            topDropShadow.setInput(atLeft ? null : topInnerShadow);
        }
        else
        {
            top.setEffect(atLeft ? null : topInnerShadow);
        }

        if (!atLeft)
        {
            left.setEffect(leftDropShadow);
            leftDropShadow.setInput(atTop ? null : leftInnerShadow);
        }
        else
        {
            left.setEffect(atTop ? null : leftInnerShadow);
        }
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

    public void clear()
    {
        // Clears rows, too:
        setColumns(Collections.emptyList());
    }

    public void setColumns(List<Pair<String, ValueFetcher>> columns)
    {
        items.clear();
        this.columns.clear();
        this.columns.addAll(Utility.mapList(columns, Pair::getSecond));
        while (columnSizes.size() > columns.size())
        {
            columnSizes.remove(columnSizes.size() - 1);
        }
        while (columnSizes.size() < columns.size())
        {
            columnSizes.add(new SimpleDoubleProperty(100.0));
        }

        headerItemsContainer.getChildren().clear();
        for (int i = 0; i < columns.size(); i++)
        {
            Pair<String, ValueFetcher> column = columns.get(i);
            HeaderItem headerItem = new HeaderItem(column.getFirst(), i);
            headerItemsContainer.getChildren().add(headerItem);
            headerItems.add(headerItem);
        }

        placeholder.setVisible(columns.isEmpty());
    }

    public void setRows(SimulationFunction<Integer, Boolean> isRowValid)
    {
        Workers.onWorkerThread("Calculating table rows", () -> {
            int toAdd = 0;
            try
            {
                outer:
                for (int i = 0; i < 10; i++)
                {
                    toAdd = 0;
                    for (int j = 0; j < 10; j++)
                    {
                        if (isRowValid.apply(i * 10 + j))
                        {
                            toAdd++;
                        } else
                            break outer;
                    }
                    int toAddFinal = toAdd;
                    Platform.runLater(() ->
                    {
                        for (int k = 0; k < toAddFinal; k++)
                        {
                            items.add(null);
                        }
                    });
                }
            }
            catch (InternalException | UserException e)
            {
                Utility.log(e);
                // TODO display somewhere?
            }
            int toAddFinal = toAdd;
            Platform.runLater(() -> {
                for (int k = 0; k < toAddFinal; k++)
                {
                    items.add(null);
                }
            });
        });

        //TODO store it for fetching more
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
    private class StableRow implements Cell<@Nullable Object, Node>
    {
        private final HBox hBox = new HBox();
        private int curRowIndex = -1;

        public StableRow()
        {
        }

        private void bindChildSize(int itemIndex)
        {
            Region n = (Region)hBox.getChildren().get(itemIndex);
            n.setMinWidth(Region.USE_PREF_SIZE);
            n.setMaxWidth(Region.USE_PREF_SIZE);
            n.prefWidthProperty().bind(columnSizes.get(itemIndex));
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public boolean isReusable()
        {
            return true;
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public void updateIndex(int rowIndex)
        {
            if (rowIndex != curRowIndex)
            {
                curRowIndex = rowIndex;
                hBox.getChildren().clear();
                for (ValueFetcher column : columns)
                {
                    // This will be column + 1, if we are displaying line numbers:
                    int columnIndex = hBox.getChildren().size();
                    hBox.getChildren().add(new Label(""));
                    bindChildSize(columnIndex);
                    column.fetchValue(rowIndex, (x, n) -> {
                        hBox.getChildren().set(columnIndex, n);
                        bindChildSize(columnIndex);
                    });
                }
            }
        }

        // You have to override this to avoid the UnsupportedOperationException
        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public void updateItem(@Nullable Object item)
        {
            // Everything is actually done in updateIndex
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public Node getNode()
        {
            return hBox;
        }

        public int getCurRowIndex()
        {
            return curRowIndex;
        }
    }

    @OnThread(Tag.FXPlatform)
    public static interface ValueReceiver
    {
        public void setValue(int rowIndex, Region value);
    }

    @OnThread(Tag.FXPlatform)
    public static interface ValueFetcher
    {
        // Called to fetch a value.  Once available, received should be called.
        // Until then it will be blank.  You can call receiver multiple times though,
        // so you can just call it with a placeholder before returning.
        public void fetchValue(int rowIndex, ValueReceiver receiver);
    }

    @OnThread(Tag.FXPlatform)
    private class LineNumber implements Cell<@Nullable Object, Node>
    {
        private final Label label = new Label();

        public LineNumber()
        {
            label.getStyleClass().add("stable-view-row-number");
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
            label.setText(Integer.toString(index));
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public Node getNode()
        {
            return label;
        }

        // You have to override this to avoid the UnsupportedOperationException
        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        public void updateItem(@Nullable Object item)
        {
            // Everything is actually done in updateIndex
        }
    }
}
