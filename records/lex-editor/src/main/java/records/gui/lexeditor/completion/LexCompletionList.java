package records.gui.lexeditor.completion;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableBiMap.Builder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.checkerframework.checker.nullness.qual.Nullable;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.gui.FXUtility;

import java.util.AbstractList;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

// Like a ListView, but with customisations to allow pinned "related" section
@OnThread(Tag.FXPlatform)
class LexCompletionList extends Region
{
    // The completions change all at once, so a mutable ImmutableList reference:
    private ImmutableList<LexCompletionGroup> curCompletionGroups = ImmutableList.of();
    
    // Changed when completions change
    private ImmutableBiMap<LexCompletion, Pair<Integer, Integer>> completionIndexes = ImmutableBiMap.of();
    
    private final AbstractList<Either<LexCompletion, LexCompletionGroup>> allDisplayRows;
    
    // The top Y coordinate of the header (if present, first item if not) of each group in curCompletions.  These coordinates
    // are relative to the pane.  Due to the pinning, it is possible
    // that the next group begins before the previous one ends.
    // These may also change as scrolling happens
    private double[] groupTopY = new double[0];
    // Scroll from top, if nothing was pinned.  Always zero or positive
    private double scrollOffset;
    
    // This map's values are always inserted into the children of this region.
    // The keys are either completions or headers
    // which are proxied by the group
    private final ObservableMap<Either<LexCompletion, LexCompletionGroup>, CompletionRow> visible = FXCollections.observableHashMap();
    
    // Group index, index within that group
    private @Nullable Pair<Integer, Integer> selectionIndex;
    // Holds a mirror of selectionIndex, in effect:
    private final ObjectProperty<@Nullable LexCompletion> selectedItem = new SimpleObjectProperty<>(null);
    
    private final double ITEM_HEIGHT = 24;
    
    public LexCompletionList(FXPlatformConsumer<LexCompletion> triggerCompletion)
    {
        this.allDisplayRows = new AbstractList<Either<LexCompletion, LexCompletionGroup>>()
        {
            @Override
            public Either<LexCompletion, LexCompletionGroup> get(int index)
            {
                for (LexCompletionGroup group : curCompletionGroups)
                {
                    if (group.header != null)
                    {
                        index -= 1;
                        if (index < 0)
                            return Either.right(group);
                    }
                    if (index < group.completions.size())
                        return Either.left(group.completions.get(index));
                    index -= group.completions.size();
                }
                throw new IndexOutOfBoundsException("Invalid index");
            }

            @Override
            public int size()
            {
                int total = 0;
                for (LexCompletionGroup group : curCompletionGroups)
                {
                    if (group.header != null)
                        total++;
                    total += group.completions.size();
                }
                return total;
            }
        };
        
        getStyleClass().add("lex-completion-list");
        setFocusTraversable(false);
        visible.addListener((MapChangeListener<Either<LexCompletion, LexCompletionGroup>, Node>) (MapChangeListener.Change<? extends Either<LexCompletion, LexCompletionGroup>, ? extends Node> c) -> {
            if (c.wasRemoved())
                getChildren().remove(c.getValueRemoved());
            if (c.wasAdded())
                getChildren().add(c.getValueAdded());
        });
        
        setPrefWidth(300.0);
        setMaxHeight(USE_PREF_SIZE);
        addEventFilter(ScrollEvent.ANY, e -> {
            scrollOffset = Math.max(0, Math.min(scrollOffset - e.getDeltaY(), allDisplayRows.size() * ITEM_HEIGHT - getHeight()));
            FXUtility.mouse(this).recalculateChildren();
            e.consume();
        });
        setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY)
            {
                Pair<Integer, Integer> target = FXUtility.mouse(this).getItemAt(e.getY());
                boolean wasAlreadySelected = Objects.equals(selectionIndex, target);
                if (e.getClickCount() >= 1)
                {
                    FXUtility.mouse(this).select(target);
                }

                // Clicking twice slowly also works this way:
                LexCompletion sel = selectedItem.get();
                if ((e.getClickCount() == 2 || wasAlreadySelected) && sel != null)
                    triggerCompletion.consume(sel);
            }
        });
    }

    private @Nullable Pair<Integer, Integer> getItemAt(double y)
    {
        for (int g = 0; g < groupTopY.length; g++)
        {
            if (y < groupTopY[g])
                return null;
            LexCompletionGroup group = curCompletionGroups.get(g);
            int header = 0;
            if (group.header != null)
            {
                if (y < groupTopY[g] + ITEM_HEIGHT)
                    return null;
                header = 1;
            }
            for (int i = 0; i < group.completions.size(); i++)
            {
                if (y < groupTopY[g] + ITEM_HEIGHT * (i + header) + ITEM_HEIGHT)
                    return new Pair<>(g, i);
            }
        }
        return null;
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected double computePrefHeight(double width)
    {
        return Math.min(allDisplayRows.size(), 16) * ITEM_HEIGHT + 2 /* border */;
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected void layoutChildren()
    {
        Insets insets = getInsets();
        double width = getWidth() - insets.getRight() - insets.getLeft();
        double y = insets.getTop() - (scrollOffset % ITEM_HEIGHT);
        double maxY = getHeight() - insets.getTop() - insets.getBottom();
        int rowSize = allDisplayRows.size();
        for (int i = (int)(scrollOffset / ITEM_HEIGHT); i < rowSize && y < maxY; i++, y += ITEM_HEIGHT)
        {
            CompletionRow flow = visible.get(allDisplayRows.get(i));
            // Should always be non-null, but need to guard
            if (flow != null)
            {
                flow.resizeRelocate(insets.getLeft(), y, width, ITEM_HEIGHT);
                flow.clip.setWidth(width);
                flow.clip.setY(Math.max(y, insets.getTop()) - y);
                flow.clip.setHeight(Math.min(ITEM_HEIGHT + y, maxY) - y);
            }
        }
    }
    
    private void recalculateChildren()
    {
        Set<Either<LexCompletion, LexCompletionGroup>> toKeep = Sets.newHashSet();
        groupTopY = new double[curCompletionGroups.size()];
        for (int i = 0; i < groupTopY.length; i++)
        {
            groupTopY[i] = Double.MAX_VALUE;
        }
        LexCompletion sel = selectedItem.get();
        double y = -(scrollOffset % ITEM_HEIGHT);
        int rowSize = allDisplayRows.size();
        for (int i = (int)(scrollOffset / ITEM_HEIGHT); i < rowSize && y < computePrefHeight(-1); i++, y += ITEM_HEIGHT)
        {
            Either<LexCompletion, LexCompletionGroup> row = allDisplayRows.get(i);
            double thisY = y;
            row.ifLeft(c -> {
                Pair<Integer, Integer> indexes = completionIndexes.get(c);
                if (indexes != null)
                    groupTopY[indexes.getFirst()] = Math.min(groupTopY[indexes.getFirst()], thisY - (indexes.getSecond() * ITEM_HEIGHT));
            });
            
            CompletionRow item = visible.computeIfAbsent(row, this::makeFlow);
            FXUtility.setPseudoclass(item, "selected", sel != null && sel.equals(row.<@Nullable LexCompletion>either(c -> c, g -> null)));
            toKeep.add(row);
        }
        visible.entrySet().removeIf(e -> !toKeep.contains(e.getKey()));
        requestLayout();
    }

    private CompletionRow makeFlow(Either<LexCompletion, LexCompletionGroup> lexCompletion)
    {
        CompletionRow row = new CompletionRow(lexCompletion.either(c -> c.display.toGUI(), g -> (g.header == null ? StyledString.s("") : g.header).toGUI()), lexCompletion.either(c -> c.sideText, g -> ""));
        if (lexCompletion.isRight())
            row.getStyleClass().add("lex-completion-header");
        row.setMouseTransparent(true);
        return row;
    }

    public void setCompletions(ImmutableList<LexCompletionGroup> completions)
    {
        this.curCompletionGroups = completions;
        Builder<LexCompletion, Pair<Integer, Integer>> indexes = ImmutableBiMap.builder();
        for (int i = 0; i < curCompletionGroups.size(); i++)
        {
            for (int j = 0; j < curCompletionGroups.get(i).completions.size(); j++)
            {
                indexes.put(curCompletionGroups.get(i).completions.get(j), new Pair<>(i, j));
            }
        }
        this.completionIndexes = indexes.build();
        // Keep same item selected, if still present:
        @Nullable LexCompletion sel = selectedItem.get();
        @Nullable Pair<Integer, Integer> target = sel == null ? null : completionIndexes.get(sel);
        select(target);
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void requestFocus()
    {
        // Can't be focused
    }
    
    public void select(@Nullable Pair<Integer, Integer> target)
    {
        selectionIndex = target;
        selectedItem.setValue(target == null ? null : curCompletionGroups.get(target.getFirst()).completions.get(target.getSecond()));

        double minY = Math.max(0, allDisplayRows.indexOf(target == null ? null : Either.left(completionIndexes.inverse().get(target)))) * ITEM_HEIGHT;
        double maxY = minY + ITEM_HEIGHT;
        if (minY < scrollOffset)
            scrollOffset = minY;
        else if (getHeight() >= ITEM_HEIGHT && maxY > scrollOffset + getHeight())
            scrollOffset = maxY - getHeight();
        
        // Update graphics:
        recalculateChildren();
    }
    
    public @Nullable LexCompletion getSelectedItem()
    {
        return selectedItem.get();
    }
    
    public void up()
    {
        if (selectionIndex != null)
        {
            if (selectionIndex.getSecond() > 0)
                select(selectionIndex.mapSecond(n -> n - 1));
            else if (selectionIndex.getFirst() > 0)
                select(new Pair<>(selectionIndex.getFirst() - 1, curCompletionGroups.get(selectionIndex.getFirst() - 1).completions.size() - 1));
            else
                select(null);
        }
    }

    public void down()
    {
        if (selectionIndex == null)
        {
            if (!curCompletionGroups.isEmpty() && !curCompletionGroups.get(0).completions.isEmpty())
                select(new Pair<>(0, 0));
        }
        else
        {
            if (selectionIndex.getSecond() < curCompletionGroups.get(selectionIndex.getFirst()).completions.size() - 1)
                select(selectionIndex.mapSecond(n -> n + 1));
            else if (selectionIndex.getFirst() < curCompletionGroups.size() - 1 && !curCompletionGroups.get(selectionIndex.getFirst() + 1).completions.isEmpty())
                select(new Pair<>(selectionIndex.getFirst() + 1, 0));
        }
    }
    
    public Stream<LexCompletion> getItems()
    {
        return curCompletionGroups.stream().flatMap(c -> c.completions.stream());
    }

    public ObjectExpression<@Nullable LexCompletion> selectedItemProperty()
    {
        return selectedItem;
    }

    /**
     * The distance from the left of the list view to the left of the window.
     */
    public double getTotalTextLeftPad()
    {
        double listInset = getInsets().getLeft();
        double parentInset = ((Region) getParent()).getInsets().getLeft();
        // Bit hacky to hard-code, but sometimes the items haven't updated via CSS:
        double itemInset = 2; // visible.values().stream().findFirst().map(f -> f.getInsets().getLeft()).orElse(0.0);
        return listInset + parentInset + itemInset;
    }
    
    @OnThread(Tag.FXPlatform)
    private class CompletionRow extends Region
    {
        private final TextFlow mainText;
        private final Label sideText;
        private final Rectangle clip = new Rectangle();

        public CompletionRow(Collection<Text> content, String sideText)
        {
            setFocusTraversable(false);
            mainText = new TextFlow();
            mainText.getStyleClass().add("lex-completion-text-flow");
            mainText.getChildren().setAll(content);
            mainText.setFocusTraversable(false);
            this.sideText = new Label(sideText);
            this.sideText.getStyleClass().add("side-text");
            setClip(clip);
            getChildren().setAll(mainText, this.sideText);
            getStyleClass().add("lex-completion");
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        protected void layoutChildren()
        {
            mainText.resizeRelocate(getInsets().getLeft(), 0, getWidth() - getInsets().getLeft() - getInsets().getRight(), getHeight());
            double sideWidth = sideText.prefWidth(-1);
            double sideHeight = sideText.prefHeight(sideWidth);
            sideText.resizeRelocate(getWidth() - sideWidth - getInsets().getRight(), (getHeight() - sideHeight) / 2.0, sideWidth, sideHeight);
        }
    }
}
