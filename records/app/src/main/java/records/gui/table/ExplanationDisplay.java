package records.gui.table;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;
import log.Log;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.CellPosition;
import records.data.ColumnStorage;
import records.data.TableId;
import records.error.InternalException;
import records.error.UserException;
import records.gui.grid.VirtualGridSupplier;
import records.gui.grid.VirtualGridSupplier.ItemState;
import records.gui.grid.VirtualGridSupplier.ViewOrder;
import records.gui.grid.VirtualGridSupplier.VisibleBounds;
import records.gui.grid.VirtualGridSupplierFloating.FloatingItem;
import records.transformations.expression.Expression;
import records.transformations.expression.explanation.Explanation;
import records.transformations.expression.explanation.ExplanationLocation;
import styled.StyledString;
import styled.StyledString.Style;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.FXPlatformRunnable;
import utility.SimulationSupplier;
import utility.Utility;
import utility.Workers;
import utility.Workers.Priority;
import utility.gui.FXUtility;
import utility.gui.ScrollPaneFill;
import utility.gui.SmallDeleteButton;

import java.util.*;
import java.util.function.Supplier;

@OnThread(Tag.FXPlatform)
public class ExplanationDisplay extends FloatingItem<ExplanationDisplay.ExplanationPane>
{
    @OnThread(Tag.Any)
    private final Explanation explanation;
    private final CellPosition attachedTo;
    private final TableId srcTableId;
    private final FXPlatformConsumer<ExplanationLocation> jumpTo;
    private final FXPlatformConsumer<ExplanationDisplay> close;
    private final FXPlatformRunnable relayout;

    protected ExplanationDisplay(TableId srcTableId, CellPosition attachedTo, Explanation explanation, FXPlatformConsumer<ExplanationLocation> jumpTo, FXPlatformConsumer<ExplanationDisplay> close, FXPlatformRunnable relayout)
    {
        super(ViewOrder.POPUP);
        this.attachedTo = attachedTo;
        this.explanation = explanation;
        this.srcTableId = srcTableId;
        this.jumpTo = jumpTo;
        this.close = close;
        this.relayout = relayout;
    }

    @Override
    protected Optional<BoundingBox> calculatePosition(VisibleBounds visibleBounds)
    {
        ExplanationPane node = getNode();
        double width = Math.min(300.0, node == null ? 0.0 : node.prefWidth(-1));
        double height = node == null ? 0 : node.prefHeight(width);
                
        double middle = (visibleBounds.getXCoord(attachedTo.columnIndex) + visibleBounds.getXCoordAfter(attachedTo.columnIndex)) / 2.0;
        double left = middle - width / 2.0;
        double right = middle + width / 2.0;
        double top = visibleBounds.getYCoordAfter(attachedTo.rowIndex) + 10;
        double bottom = top + height;
        return Optional.of(new BoundingBox(left, top, right - left, bottom - top));
    }

    @Override
    protected ExplanationPane makeCell(VisibleBounds visibleBounds)
    {
        return new ExplanationPane(() -> {
            ImmutableList.Builder<StyledString> lines = ImmutableList.builder();
            HashMap<Explanation, Boolean> alreadyExplained = new HashMap<>();
            Multimap<Expression, HighlightExpression> highlights = ArrayListMultimap.create();
            makeString(explanation, alreadyExplained, highlights, lines, false);
            return lines.build().stream().collect(StyledString.joining("\n\u21aa "));
        });
    }

    // Returns any locations which were not shown in skipped children
    @OnThread(Tag.Simulation)
    private ImmutableList<ExplanationLocation> makeString(Explanation explanation, HashMap<Explanation, Boolean> alreadyExplained, Multimap<Expression, HighlightExpression> expressionStyles, ImmutableList.Builder<StyledString> lines, boolean skipIfTrivial) throws UserException, InternalException
    {
        if (explanation.isValue() && alreadyExplained.containsKey(explanation))
            return ImmutableList.of();
        
        ImmutableList.Builder<ExplanationLocation> skippedLocationsBuilder = ImmutableList.builder(); 
        // Go from lowest child upwards:
        for (Explanation child : explanation.getDirectSubExplanations())
        {
            skippedLocationsBuilder.addAll(makeString(child, alreadyExplained, expressionStyles, lines, explanation.excludeChildrenIfTrivial()));
        }

        ImmutableList<ExplanationLocation> skippedLocations = skippedLocationsBuilder.build();
        @SuppressWarnings("keyfor")
        Set<Explanation> alreadyDescribed = alreadyExplained.keySet();
        @Nullable StyledString description = explanation.describe(alreadyDescribed, this::hyperlinkLocation, (s, e) -> s.withStyle(new HighlightExpression(expressionStyles, e, () -> alreadyExplained.entrySet().stream().filter(x -> x.getKey().isDescribing(e)).map(x -> x.getValue()).reduce((a, b) -> a || b).orElse(false))), skippedLocations, skipIfTrivial);
        alreadyExplained.put(explanation, description != null);
        if (description != null)
        {
            lines.add(description);
            return ImmutableList.of();
        }
        else
        {
            return Utility.concatI(skippedLocations, explanation.getDirectlyUsedLocations());
        }
        
    }

    private StyledString hyperlinkLocation(ExplanationLocation explanationLocation)
    {
        String content = "";
        if (!explanationLocation.tableId.equals(srcTableId))
        {
            content += explanationLocation.tableId.getRaw() + ":";
        }
        content += explanationLocation.columnId.getRaw();
        // +1 to turn back into user index:
        if (explanationLocation.rowIndex.isPresent())
            content += " (row " + (explanationLocation.rowIndex.get() + 1) + ")";
        
        Clickable click = new Clickable("click.to.view")
        {
            @Override
            protected @OnThread(Tag.FXPlatform) void onClick(MouseButton mouseButton, Point2D screenPoint)
            {
                jumpTo.consume(explanationLocation);
            }
        };
        
        return StyledString.s(content).withStyle(click);
    }

    @Override
    public VirtualGridSupplier.@Nullable ItemState getItemState(CellPosition cellPosition, Point2D screenPos)
    {
        Node node = getNode();
        if (node != null)
        {
            Bounds screenBounds = node.localToScreen(node.getBoundsInLocal());
            return screenBounds.contains(screenPos) ? ItemState.DIRECTLY_CLICKABLE : null;
        }
        return null;
    }

    @Override
    public void keyboardActivate(CellPosition cellPosition)
    {
        // No keyboard activation via cells
    }
    
    @OnThread(Tag.FXPlatform)
    class ExplanationPane extends StackPane
    {
        private final TextFlow textFlow;
        
        public ExplanationPane(SimulationSupplier<StyledString> loadContent)
        {
            textFlow = new TextFlow(new Text("Loading..."));
            Workers.onWorkerThread("Loading explanation", Priority.FETCH, () -> {
                StyledString content;
                try
                {
                    content = loadContent.get();
                }
                catch (InternalException | UserException e)
                {
                    Log.log(e);
                    content = StyledString.concat(StyledString.s("Error: "), e.getStyledMessage());
                }
                StyledString contentFinal = content;
                Platform.runLater(() -> {
                    textFlow.getChildren().setAll(contentFinal.toGUI());
                    FXUtility.runAfterDelay(Duration.millis(300), relayout);
                });
            });
            textFlow.getStyleClass().add("explanation-flow");
            setMargin(textFlow, new Insets(8));

            SmallDeleteButton deleteButton = new SmallDeleteButton();
            deleteButton.setOnAction(() -> close());
            setMargin(deleteButton, new Insets(10));
            setAlignment(deleteButton, Pos.TOP_RIGHT);
            
            setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.MIDDLE)
                {
                    close();
                    e.consume();
                }
            });
            
            getChildren().addAll(textFlow, deleteButton);
            getStyleClass().add("explanation-pane");
        }
    }
    
    private void close()
    {
        close.consume(this);
    }

    private final class HighlightExpression extends Style<HighlightExpression>
    {
        private final Multimap<Expression, HighlightExpression> allExpressionStyles;
        private final SimpleBooleanProperty highlight = new SimpleBooleanProperty(false);
        private final Expression expression;
        private final Supplier<Boolean> isExplainedDirectly;
        // Inner items that are children of this expression:
        private final HashSet<HighlightExpression> inner = new HashSet<>();
        // The opposite of inner; our parents (from nearest to furthest up in tree)
        // of this expression in the AST.
        private final LinkedHashSet<HighlightExpression> parents = new LinkedHashSet<>();

        @OnThread(Tag.Simulation)
        public HighlightExpression(Multimap<Expression, HighlightExpression> allExpressionStyles, Expression e, Supplier<Boolean> isExplainedDirectly)
        {
            super(HighlightExpression.class);
            this.expression = e;
            // Note -- the map may change after we are called but before the user hovers,
            // so it's important we keep a reference to the original, rather than copying:
            this.allExpressionStyles = allExpressionStyles;
            // This is also not valid to call until the end:
            this.isExplainedDirectly = isExplainedDirectly;
            allExpressionStyles.put(e, this);
        }

        @Override
        protected @OnThread(Tag.FXPlatform) void style(Text t)
        {
            t.getStyleClass().add("explained-expression");
            FXUtility.addChangeListenerPlatformNN(t.hoverProperty(), hovering -> {
                if (isExplainedDirectly.get())
                {
                    calculateHover(hovering);
                }
                // If we are not explained, our parent expression might be:
                else
                {
                    for (HighlightExpression parent : parents)
                    {
                        if (parent.isExplainedDirectly.get())
                        {
                            parent.calculateHover(hovering);
                            break;
                        }
                    }
                }
            });
            FXUtility.addChangeListenerPlatformNN(highlight, h -> {
                FXUtility.setPseudoclass(t, "highlight", h);
            });
        }

        @OnThread(Tag.FXPlatform)
        private void calculateHover(boolean hovering)
        {
            Collection<HighlightExpression> allAppearances = allExpressionStyles.get(expression);
            allAppearances.forEach(h -> {
                h.setHover(hovering && allAppearances.size() > 1);
            });
        }

        @OnThread(Tag.FXPlatform)
        private void setHover(boolean hovering)
        {
            highlight.set(hovering);
            inner.forEach(in -> in.setHover(hovering));
        }

        @Override
        protected HighlightExpression combine(HighlightExpression with)
        {
            // Keep innermost, which is the first to be applied:
            with.inner.add(this);
            parents.add(with);
            return this;
        }

        @Override
        protected boolean equalsStyle(HighlightExpression item)
        {
            return this == item;
        }
    }
}