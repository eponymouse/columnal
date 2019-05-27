package records.gui.lexeditor.completion;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.InternalException;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.FXPlatformConsumer;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

// Like a ListView, but with customisations to allow pinned "related" section
@OnThread(Tag.FXPlatform)
class LexCompletionList extends Region
{
    private final FXPlatformConsumer<LexCompletion> triggerCompletion;
    
    // The completions change all at once, so a mutable ImmutableList reference:
    private ImmutableList<LexCompletionGroup> curCompletionGroups = ImmutableList.of();
    
    // Changed when completions change
    private ImmutableMap<LexCompletion, Pair<Integer, Integer>> completionIndexes = ImmutableMap.of();
    
    // The top Y coordinate of the header (if present, first item if not) of each group in curCompletions.  These coordinates
    // are relative to the pane.  Due to the pinning, it is possible
    // that the next group begins before the previous one ends.
    // These will change as scrolling happens
    private double[] groupTopY = new double[0];
    // The height of each group on the screen in pixels:
    private double[] groupInternalHeight = new double[0];
    // Scroll from top of selected group (or top group if none selected).  Always zero or positive
    private double scrollOffsetWithinSelectedGroup;
    
    // This map's values are always inserted into the children of this region.
    // The keys are either completions or headers
    // which are proxied by the group
    private final ObservableMap<Either<LexCompletion, LexCompletionGroup>, CompletionRow> visible = FXCollections.observableHashMap();
    
    // Group index, index within that group
    private @Nullable Pair<Integer, Integer> selectionIndex;
    // Holds a mirror of selectionIndex, in effect:
    private final ObjectProperty<@Nullable LexCompletion> selectedItem = new SimpleObjectProperty<>(null);
    
    private final int ITEM_HEIGHT = 24;
    
    public LexCompletionList(FXPlatformConsumer<LexCompletion> triggerCompletion)
    {
        this.triggerCompletion = triggerCompletion;
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
            int groupIndex = selectionIndex == null ? 0 : selectionIndex.getFirst();
            double groupMaxScrollY = (curCompletionGroups.get(groupIndex).completions.size() * ITEM_HEIGHT) - groupInternalHeight[groupIndex]; 
            scrollOffsetWithinSelectedGroup = Math.max(0, Math.min(scrollOffsetWithinSelectedGroup - e.getDeltaY(), groupMaxScrollY));
            FXUtility.mouse(this).recalculateChildren();
            e.consume();
        });
    }

    protected void clickOnItem(int clickCount, Pair<Integer, Integer> target)
    {
        boolean wasAlreadySelected = Objects.equals(selectionIndex, target);
        if (clickCount >= 1)
        {
            FXUtility.mouse(this).select(target);
        }

        // Clicking twice slowly also works this way:
        LexCompletion sel = selectedItem.get();
        if ((clickCount == 2 || wasAlreadySelected) && sel != null)
            triggerCompletion.consume(sel);
    }

    protected int calcDisplayRows()
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
        return Math.min(calcDisplayRows(), 16) * ITEM_HEIGHT + 2 /* border */;
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected void layoutChildren()
    {
        Insets insets = getInsets();
        double width = getWidth() - insets.getRight() - insets.getLeft();
        // The bottom Y of the lowest item laid out so far:
        double prevBottom = insets.getTop();

        for (int groupIndex = 0; groupIndex < curCompletionGroups.size(); groupIndex++)
        {
            LexCompletionGroup group = curCompletionGroups.get(groupIndex);
            double y = groupTopY[groupIndex];
            if (group.header != null)
            {
                // Special case for header:
                CompletionRow flow = visible.get(Either.right(group));
                // Should always be non-null, but need to guard
                if (flow != null)
                {
                    flow.resizeRelocate(insets.getLeft(), y, width, ITEM_HEIGHT);
                    flow.clip.setWidth(width);
                    flow.clip.setY(0);
                    flow.clip.setHeight(ITEM_HEIGHT);
                }
                y += ITEM_HEIGHT;
                prevBottom = Math.max(prevBottom, y);
            }
            
            boolean selected = selectionIndex == null ? groupIndex == 0 : selectionIndex.getFirst() == groupIndex;
            y -= (selected ? scrollOffsetWithinSelectedGroup : 0);
            double maxY = groupIndex + 1 < groupTopY.length ? groupTopY[groupIndex + 1] : getHeight() - insets.getTop() - insets.getBottom();
            for (int i = 0; i < group.completions.size() && y < maxY; i++)
            {
                if (y >= groupTopY[groupIndex] - ITEM_HEIGHT)
                {
                    CompletionRow flow = visible.get(Either.left(group.completions.get(i)));
                    // Should always be non-null, but need to guard
                    if (flow != null)
                    {
                        flow.resizeRelocate(insets.getLeft(), y, width, ITEM_HEIGHT);
                        flow.clip.setWidth(width);
                        flow.clip.setY(prevBottom - y);
                        flow.clip.setHeight(Math.min(ITEM_HEIGHT + y, maxY) - y);
                    }
                }
                y += ITEM_HEIGHT;
                prevBottom = Math.max(prevBottom, y);
            }
        }
    }
    
    private void recalculateChildren()
    {
        Set<Either<LexCompletion, LexCompletionGroup>> toKeep = Sets.newHashSet();
        groupTopY = new double[curCompletionGroups.size()];
        groupInternalHeight = new double[curCompletionGroups.size()];
        LexCompletion sel = selectedItem.get();
        // We go up from bottom, presuming that the top group takes left-over space:
        
        double[] groupMinHeight = new double[curCompletionGroups.size()];
        double[] groupIdealHeight = new double[curCompletionGroups.size()];
        for (int groupIndex = 0; groupIndex < curCompletionGroups.size(); groupIndex++)
        {
            LexCompletionGroup g = curCompletionGroups.get(groupIndex);
            int minItems = Math.min(g.completions.size(), g.minCollapsed);
            int header = g.header != null ? 1 : 0;
            groupMinHeight[groupIndex] = ITEM_HEIGHT * (header + minItems);
            groupIdealHeight[groupIndex] = ITEM_HEIGHT * (header + g.completions.size());
        }
        
        // Negative if too much content to show
        double extraSpaceAvailable = computePrefHeight(-1) - getInsets().getTop() - getInsets().getBottom() - Arrays.stream(groupIdealHeight).sum();
        double minExtraSpace = computePrefHeight(-1) - getInsets().getTop() - getInsets().getBottom() - Arrays.stream(groupMinHeight).sum();
        // Start everything at its min height:
        double[] groupActualHeight = Arrays.copyOf(groupMinHeight, groupMinHeight.length);
        
        // We do a loop to assign the extra space, assigning it equally among whoever wants it,
        // with a special case for the first group which takes as much space as it wants 
        // if it is selected or there is no selected:
        if (extraSpaceAvailable < 0 && minExtraSpace > 0)
        {
            double spaceToAssign = minExtraSpace;
            // First, we give all the space that the selected group wants (or top group if none selected):
            int effectiveSel = selectionIndex == null ? 0 : selectionIndex.getFirst();
            if (effectiveSel < curCompletionGroups.size())
            {
                double extraDesired = groupIdealHeight[effectiveSel] - groupMinHeight[effectiveSel];
                double actualExtra = Math.min(extraDesired, spaceToAssign);
                groupActualHeight[effectiveSel] += actualExtra;
                spaceToAssign -= actualExtra;
            }
            
            // Must use groupActualHeight not groupMinHeight because groupActualHeight[effectiveSel] may have just changed:
            // Annoying, there's no utility method to sort int[] or IntStream by a custom comparatator, hence this contortion:
            int[] groupIndexesSortedByWantedExtraSpaceAsc = IntStream.range(0, curCompletionGroups.size()).
                mapToObj(i -> i).sorted(Comparator.comparing(i -> groupIdealHeight[i] - groupActualHeight[i])).mapToInt(i -> i).toArray();
            
            // Find the first index that actually needs extra space:
            for (int i = 0; i < groupIndexesSortedByWantedExtraSpaceAsc.length; i++)
            {
                int groupIndex = groupIndexesSortedByWantedExtraSpaceAsc[i];
                double wanted = groupIdealHeight[groupIndex] - groupActualHeight[groupIndex];
                if (wanted > 0)
                {
                    // Try and give us and all future items the same space:
                    int remainingGroupsInclUs = groupIndexesSortedByWantedExtraSpaceAsc.length - i;
                    double desiredAcrossAll = remainingGroupsInclUs * wanted;
                    
                    if (desiredAcrossAll < spaceToAssign)
                    {
                        // Everyone can have it:
                        for (int j = i; j < groupIndexesSortedByWantedExtraSpaceAsc.length; j++)
                        {
                            groupActualHeight[groupIndexesSortedByWantedExtraSpaceAsc[j]] += wanted;
                        }
                        spaceToAssign -= desiredAcrossAll;
                    }
                    else
                    {
                        // We'll have to split what remains:
                        for (int j = i; j < groupIndexesSortedByWantedExtraSpaceAsc.length; j++)
                        {
                            groupActualHeight[groupIndexesSortedByWantedExtraSpaceAsc[j]] += spaceToAssign / remainingGroupsInclUs;
                        }
                        spaceToAssign = 0;
                        // No point continuing now space is gone:
                        break;
                    }
                }
            }
        }

        double y = getInsets().getTop();
        for (int groupIndex = 0; groupIndex < curCompletionGroups.size(); groupIndex++)
        {
            LexCompletionGroup group = curCompletionGroups.get(groupIndex);
            double height = groupActualHeight[groupIndex];            
            groupTopY[groupIndex] = y;
            groupInternalHeight[groupIndex] = height - (group.header != null ? ITEM_HEIGHT : 0);
            double maxY = y + height;
            
            if (group.header != null)
            {
                CompletionRow headerRow = visible.computeIfAbsent(Either.right(group), this::makeFlow);
                FXUtility.setPseudoclass(headerRow, "selected", false);
                toKeep.add(Either.right(group));
                y += ITEM_HEIGHT;
            }

            if (groupIndex == (selectionIndex == null ? 0 : selectionIndex.getFirst()))
                y -= scrollOffsetWithinSelectedGroup;
            for (int i = 0; i < group.completions.size() && y < maxY; i++, y += ITEM_HEIGHT)
            {
                if (y >= -ITEM_HEIGHT)
                {
                    Either<LexCompletion, LexCompletionGroup> row = i == -1 ? Either.right(group) : Either.left(group.completions.get(i));
                    CompletionRow item = visible.computeIfAbsent(row, this::makeFlow);
                    FXUtility.setPseudoclass(item, "selected", sel != null && sel.equals(row.<@Nullable LexCompletion>either(c -> c, g -> null)));
                    toKeep.add(row);
                }
            }
            
            y = maxY;
        }
        
        visible.entrySet().removeIf(e -> !toKeep.contains(e.getKey()));
        requestLayout();
    }

    private CompletionRow makeFlow(Either<LexCompletion, LexCompletionGroup> lexCompletion)
    {
        CompletionRow row = new CompletionRow(lexCompletion.either(c -> c.display.toGUI(), g -> (g.header == null ? StyledString.s("") : g.header).toGUI()), lexCompletion.either(c -> c.sideText, g -> ""));
        if (lexCompletion.isRight())
            row.getStyleClass().add("lex-completion-header");
        row.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY)
            {
                lexCompletion.ifLeft(item -> {
                    Pair<Integer, Integer> index = completionIndexes.get(item);
                    // Shouldn't be null, but have to guard:
                    if (index != null)
                        clickOnItem(e.getClickCount(), index);
                });
            }
        });
        return row;
    }

    public void setCompletions(ImmutableList<LexCompletionGroup> completions)
    {
        this.curCompletionGroups = completions;
        ImmutableMap.Builder<LexCompletion, Pair<Integer, Integer>> indexes = ImmutableMap.builder();
        for (int i = 0; i < curCompletionGroups.size(); i++)
        {
            for (int j = 0; j < curCompletionGroups.get(i).completions.size(); j++)
            {
                indexes.put(curCompletionGroups.get(i).completions.get(j), new Pair<>(i, j));
            }
        }
        try
        {
            this.completionIndexes = indexes.build();
        }
        catch (Exception e)
        {
            // We shouldn't have duplicate completions, but don't let such an exception propagate:
            this.completionIndexes = ImmutableMap.of();
            try
            {
                throw new InternalException("Duplicate completions", e);
            }
            catch (InternalException internal)
            {
                Log.log(internal);
            }
        }
        // Need to update internal heights as select() will try to keep in view:
        recalculateChildren();
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

        // Need to recalculate children as size of group may change as different groups get selected:
        recalculateChildren();
        
        // Then fix our scroll offsets:
        if (target == null)
        {
            scrollOffsetWithinSelectedGroup = 0;
        }
        else
        {
            // Ensure selection is visible.
            
            // All calculations are within group, excl header:
            double selectedTopY =  target.getSecond() * ITEM_HEIGHT;
            double selectedBottomY = selectedTopY + ITEM_HEIGHT;
            double totalGroupHeight = curCompletionGroups.get(target.getFirst()).completions.size() * ITEM_HEIGHT;
            
            if (selectedTopY < scrollOffsetWithinSelectedGroup)
                scrollOffsetWithinSelectedGroup = Math.min(totalGroupHeight - groupInternalHeight[target.getFirst()], selectedTopY);
            else if (selectedBottomY > scrollOffsetWithinSelectedGroup + groupInternalHeight[target.getFirst()])
                scrollOffsetWithinSelectedGroup = Math.min(totalGroupHeight - groupInternalHeight[target.getFirst()], selectedBottomY - groupInternalHeight[target.getFirst()]);
        }
        // Update graphics after scroll change:
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
            else
            {
                OptionalInt prevNonEmpty = Utility.findLastIndex(curCompletionGroups.subList(0, selectionIndex.getFirst()), g -> !g.completions.isEmpty());
                if (prevNonEmpty.isPresent())
                    select(new Pair<>(prevNonEmpty.getAsInt(), curCompletionGroups.get(prevNonEmpty.getAsInt()).completions.size() - 1));
                else
                    select(null);
            }
        }
    }

    public void down()
    {
        if (selectionIndex == null)
        {
            OptionalInt firstNonEmpty = Utility.findFirstIndex(curCompletionGroups, g -> !g.completions.isEmpty());
            if (firstNonEmpty.isPresent())
                select(new Pair<>(firstNonEmpty.getAsInt(), 0));
        }
        else
        {
            if (selectionIndex.getSecond() < curCompletionGroups.get(selectionIndex.getFirst()).completions.size() - 1)
                select(selectionIndex.mapSecond(n -> n + 1));
            else if (selectionIndex.getFirst() < curCompletionGroups.size() - 1 && !curCompletionGroups.get(selectionIndex.getFirst() + 1).completions.isEmpty())
                select(new Pair<>(selectionIndex.getFirst() + 1, 0));
        }
    }

    public void pageUp()
    {
        if (selectionIndex != null)
        {
            OptionalInt prevNonEmpty = Utility.findLastIndex(curCompletionGroups.subList(0, selectionIndex.getFirst()), g -> !g.completions.isEmpty());
            if (prevNonEmpty.isPresent())
            {
                select(new Pair<>(prevNonEmpty.getAsInt(), 0));
            }
            else
            {
                select(null);
            }
        }
    }

    public void pageDown()
    {
        if (selectionIndex == null)
        {
            OptionalInt firstNonEmpty = Utility.findFirstIndex(curCompletionGroups, g -> !g.completions.isEmpty());
            if (firstNonEmpty.isPresent())
                select(new Pair<>(firstNonEmpty.getAsInt(), 0));
        }
        else
        {
            int next = selectionIndex.getFirst() + 1;
            OptionalInt nextNonEmpty = Utility.findFirstIndex(curCompletionGroups.subList(next, curCompletionGroups.size()), g -> !g.completions.isEmpty());
            if (nextNonEmpty.isPresent())
                select(new Pair<>(nextNonEmpty.getAsInt() + next, 0));
            // else do nothing
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
            mainText.setMouseTransparent(true);
            this.sideText = new Label(sideText);
            this.sideText.getStyleClass().add("side-text");
            this.sideText.setMouseTransparent(true);
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
