/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.gui.lexeditor.completion;

import annotation.units.DisplayPixels;
import annotation.units.VirtualPixels;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeType;
import javafx.scene.text.Text;
import javafx.util.Duration;
import xyz.columnal.log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.error.InternalException;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.gui.FXUtility;
import xyz.columnal.utility.gui.HelpfulTextFlow;
import xyz.columnal.utility.gui.ResizableRectangle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

// Like a ListView, but with customisations to allow pinned "related" section
@OnThread(Tag.FXPlatform)
final class LexCompletionList extends Region
{
    private static final double PIXELS_PER_SECOND = 350.0;
    public static final double MAX_PENDING_SCROLL = 100.0;
    private final LexCompletionListener triggerCompletion;
    private final ScrollBar scrollBar;

    // The completions change all at once, so a mutable ImmutableList reference:
    private ImmutableList<LexCompletionGroup> curCompletionGroups = ImmutableList.of();
    // Ditto:
    private ImmutableList<DoubleProperty> groupAnimatedTranslate = ImmutableList.of();
    private ParallelTransition groupAnimation = new ParallelTransition();
    
    // Changed when completions change
    private final IdentityHashMap<LexCompletion, Pair<Integer, Integer>> completionIndexes = new IdentityHashMap<>();
    
    // The top Y coordinate of the header (if present, first item if not) of each group in curCompletions.  These coordinates
    // are relative to the pane.  Due to the pinning, it is possible
    // that the next group begins before the previous one ends.
    // These will change as scrolling happens
    private @DisplayPixels double[] groupTopY = new double[0];
    
    // This is the canonical scroll position.  Scroll ranges from 0 up to max(0, sum(group heights) - list display height),
    // where each group height is (number of items + (has header ? 1 : 0)) * ITEM_HEIGHT.
    // This scroll can be affected by keyboard movement, scroll events, or scroll bar use.  We guard
    // against recursive update using the flag.
    private @VirtualPixels double canonicalScrollPosition;
    private boolean inScrollUpdate;
    
    private static enum GroupNodeType { HEADER, FADE }
    
    // This map's values are always inserted into the children of this region.
    // The keys are either completions or headers
    // which are proxied by the group
    private final ObservableMap<Either<Pair<LexCompletionGroup, LexCompletion>, Pair<LexCompletionGroup, GroupNodeType>>, Node> visible = FXCollections.observableHashMap();
    
    // Group index, index within that group
    private @Nullable Pair<Integer, Integer> selectionIndex;
    // Holds a mirror of selectionIndex, in effect:
    private final ObjectProperty<@Nullable LexCompletion> selectedItem = new SimpleObjectProperty<>(null);
    
    private final int ITEM_HEIGHT = 24;
    
    public LexCompletionList(LexCompletionListener triggerCompletion)
    {
        this.scrollBar = new ScrollBar();
        scrollBar.setOrientation(Orientation.VERTICAL);
        this.triggerCompletion = triggerCompletion;
        getStyleClass().add("lex-completion-list");
        setFocusTraversable(false);
        visible.addListener((MapChangeListener<Either<Pair<LexCompletionGroup, LexCompletion>, Pair<LexCompletionGroup, GroupNodeType>>, Node>) (MapChangeListener.Change<? extends Either<Pair<LexCompletionGroup, LexCompletion>, Pair<LexCompletionGroup, GroupNodeType>>, ? extends Node> c) -> {
            if (c.wasRemoved())
                getChildren().remove(c.getValueRemoved());
            if (c.wasAdded())
                getChildren().add(c.getValueAdded());
            sortChildren();
        });
        
        setPrefWidth(300.0);
        setMaxHeight(USE_PREF_SIZE);
        addEventFilter(ScrollEvent.ANY, e -> {
            updateScroll(canonicalScrollPosition - e.getDeltaY() * VirtualPixels.ONE, true);
            e.consume();
        });
        FXUtility.addChangeListenerPlatformNN(heightProperty(), h -> {
            recalculateChildren(false);
        });
        FXUtility.addChangeListenerPlatformNN(scrollBar.valueProperty(), d -> updateScroll(d.doubleValue() * VirtualPixels.ONE, false));
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);
        // This is lazy, but saves messing with nested pane and clip; redraw border on top of everything:
        Rectangle borderRect = new Rectangle();
        borderRect.setStroke(Color.GREY);
        borderRect.widthProperty().bind(widthProperty());
        borderRect.heightProperty().bind(heightProperty());
        borderRect.setStrokeWidth(1.0);
        borderRect.setStrokeType(StrokeType.INSIDE);
        borderRect.setFill(null);
        getChildren().addAll(scrollBar, borderRect);
    }

    public void updateScroll(@VirtualPixels double toY, boolean animate)
    {
        if (!inScrollUpdate)
        {
            inScrollUpdate = true;
            @VirtualPixels double maxScroll = (curCompletionGroups.stream().mapToDouble(g -> ITEM_HEIGHT * (g.completions.size() + (g.header != null ? 1 : 0))).sum() - getHeightMinusInsets()) * VirtualPixels.ONE;
            @SuppressWarnings("units")
            @VirtualPixels double clamped = Math.max(0, Math.min(toY, maxScroll));
            canonicalScrollPosition = clamped;
            FXUtility.mouse(this).recalculateChildren(animate);
            scrollBar.setMax(maxScroll);
            scrollBar.setValue(canonicalScrollPosition);
            inScrollUpdate = false;
        }
    }

    private void sortChildren()
    {
        // First we cache the positions:
        int pos = 0;
        for (LexCompletionGroup g : curCompletionGroups)
        {
            Node node = Utility.get(visible, Either.<Pair<LexCompletionGroup, LexCompletion>, Pair<LexCompletionGroup, GroupNodeType>>right(new Pair<LexCompletionGroup, GroupNodeType>(g, GroupNodeType.HEADER)));
            if (node != null && node instanceof CompletionRow)
                ((CompletionRow)node).cachedPosition = pos++;
            for (LexCompletion completion : g.completions)
            {
                node = Utility.get(visible, Either.<Pair<LexCompletionGroup, LexCompletion>, Pair<LexCompletionGroup, GroupNodeType>>left(new Pair<>(g, completion)));
                if (node != null && node instanceof CompletionRow)
                    ((CompletionRow)node).cachedPosition = pos++;
            }
        }
        // Then we make a duplicate collection and sort it:
        // We can't sort directly, see https://stackoverflow.com/questions/18667297/javafx-changing-order-of-children-in-a-flowpane
        ArrayList<Node> dupe = new ArrayList<>(getChildren());
        
        Collections.sort(dupe, Comparator.<Node, Integer>comparing((Node n) -> {
            // Should all be CompletionRow, except the scroll bar and fades which go last:
            if (n instanceof CompletionRow)
                return ((CompletionRow) n).cachedPosition;
            else
                return Integer.MAX_VALUE;
        }));
        
        // And copy back in:
        getChildren().setAll(dupe);
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
            triggerCompletion.complete(sel);
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
    
    private @VirtualPixels double calcGroupHeight(int groupIndex)
    {
        LexCompletionGroup group = curCompletionGroups.get(groupIndex);
        int rows = group.completions.size() + (group.header != null ? 1 : 0);
        return rows * ITEM_HEIGHT * VirtualPixels.ONE;
    }

    private @DisplayPixels double calcMinGroupHeight(int groupIndex)
    {
        LexCompletionGroup group = curCompletionGroups.get(groupIndex);
        int rows = group.completions.isEmpty() ? 0 : (Math.min(group.minCollapsed, group.completions.size()) + (group.header != null ? 1 : 0));
        return rows * ITEM_HEIGHT * DisplayPixels.ONE;
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected double computePrefHeight(double width)
    {
        return Math.min(calcDisplayRows() * ITEM_HEIGHT + 2 /* border */, 400.0);
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected void layoutChildren()
    {
        Insets insets = getInsets();
        double scrollWidth = scrollBar.prefWidth(-1);
        double width = getWidth() - insets.getRight() - insets.getLeft() - scrollWidth;
        // The bottom Y of the lowest item laid out so far:
        double prevBottom = insets.getTop();

        for (int groupIndex = 0; groupIndex < curCompletionGroups.size(); groupIndex++)
        {
            LexCompletionGroup group = curCompletionGroups.get(groupIndex);
            @DisplayPixels double y = groupTopY[groupIndex];
            if (group.header != null)
            {
                // Special case for header:
                Node flow = Utility.get(visible, Either.<Pair<LexCompletionGroup, LexCompletion>, Pair<LexCompletionGroup, GroupNodeType>>right(new Pair<LexCompletionGroup, GroupNodeType>(group, GroupNodeType.HEADER)));
                // Should always be non-null, but need to guard
                if (flow != null)
                {
                    flow.resizeRelocate(insets.getLeft(), y, width, ITEM_HEIGHT);
                }
                y += ITEM_HEIGHT * DisplayPixels.ONE;
                prevBottom = Math.max(prevBottom, y);
            }

            boolean selected = selectionIndex == null ? groupIndex == 0 : selectionIndex.getFirst() == groupIndex;
            @DisplayPixels double maxY = groupIndex + 1 < groupTopY.length ? groupTopY[groupIndex + 1] : getHeightMinusInsets() * DisplayPixels.ONE;
            for (int i = 0; i < group.completions.size() && y < maxY + ITEM_HEIGHT + MAX_PENDING_SCROLL; i++)
            {
                if (y >= groupTopY[groupIndex] - ITEM_HEIGHT - MAX_PENDING_SCROLL)
                {
                    Node flow = Utility.get(visible, Either.<Pair<LexCompletionGroup, LexCompletion>, Pair<LexCompletionGroup, GroupNodeType>>left(new Pair<>(group, group.completions.get(i))));
                    // Should always be non-null, but need to guard
                    if (flow != null)
                    {
                        flow.resizeRelocate(insets.getLeft(), y, width, ITEM_HEIGHT);
                    }
                }
                y += ITEM_HEIGHT * DisplayPixels.ONE;
            }

            if (true) // for checkstyle
            {
                Node fade = Utility.get(visible, Either.<Pair<LexCompletionGroup, LexCompletion>, Pair<LexCompletionGroup, GroupNodeType>>right(new Pair<LexCompletionGroup, GroupNodeType>(group, GroupNodeType.FADE)));
                // Should always be non-null, but need to guard
                if (fade != null)
                {
                    if (groupTopY[groupIndex] + calcGroupHeight(groupIndex) > maxY + 2)
                    {
                        fade.setVisible(true);
                        // Put fade at bottom:
                        fade.resizeRelocate(insets.getLeft(), maxY - ITEM_HEIGHT, width, ITEM_HEIGHT);
                    }
                    else
                        fade.setVisible(false);
                }
            }
            
            prevBottom = Math.max(prevBottom, maxY);
        }
        scrollBar.resizeRelocate(getWidth() - insets.getRight() - scrollWidth, insets.getTop(), scrollWidth, getHeightMinusInsets());
    }

    private double getHeightMinusInsets()
    {
        return getHeight() - getInsets().getTop() - getInsets().getBottom();
    }

    private void recalculateChildren(boolean attemptAnimate)
    {
        Set<Either<Pair<LexCompletionGroup, LexCompletion>, Pair<LexCompletionGroup, GroupNodeType>>> toKeep = Sets.newHashSet();
        double[] oldTopY = groupTopY;
        groupTopY = new double[curCompletionGroups.size()];
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
        
        @VirtualPixels double internalY = VirtualPixels.ZERO;
        for (int groupIndex = 0; groupIndex < curCompletionGroups.size(); groupIndex++)
        {
            LexCompletionGroup group = curCompletionGroups.get(groupIndex);
            @VirtualPixels double internalGroupEnd = internalY + calcGroupHeight(groupIndex);
            double height = Math.max(groupMinHeight[groupIndex], internalGroupEnd - internalY);
            double minHeightInclLater = IntStream.range(groupIndex, curCompletionGroups.size()).mapToDouble(i -> groupMinHeight[i]).sum();
            groupTopY[groupIndex] = Math.min(getInsets().getTop() + internalY - canonicalScrollPosition, getHeight() - getInsets().getBottom() - minHeightInclLater) * DisplayPixels.ONE;

            @DisplayPixels double y = groupTopY[groupIndex];
            @DisplayPixels double maxY = y + height * DisplayPixels.ONE;
            
            if (group.header != null)
            {
                Either<Pair<LexCompletionGroup, LexCompletion>, Pair<LexCompletionGroup, GroupNodeType>> key = Either.right(new Pair<>(group, GroupNodeType.HEADER));
                Node headerRow = visible.computeIfAbsent(key, this::makeFlow);
                headerRow.translateYProperty().bind(groupAnimatedTranslate.get(groupIndex));
                FXUtility.setPseudoclass(headerRow, "selected", false);
                toKeep.add(key);
                y += ITEM_HEIGHT * DisplayPixels.ONE;
            }
            
            if (true) // for checkstyle
            {
                Either<Pair<LexCompletionGroup, LexCompletion>, Pair<LexCompletionGroup, GroupNodeType>> key = Either.right(new Pair<>(group, GroupNodeType.FADE));
                Node fade = visible.computeIfAbsent(key, k -> {
                    Rectangle r = new ResizableRectangle();
                    r.setMouseTransparent(true);
                    r.getStyleClass().add("fade-overlay");
                    return r;
                });
                if (groupIndex + 1 < groupAnimatedTranslate.size())
                    fade.translateYProperty().bind(groupAnimatedTranslate.get(groupIndex + 1));
                toKeep.add(key);
            }

            for (int i = 0; i < group.completions.size() && y < maxY + ITEM_HEIGHT + MAX_PENDING_SCROLL; i++, y += ITEM_HEIGHT * DisplayPixels.ONE)
            {
                if (y >= groupTopY[groupIndex] - ITEM_HEIGHT - MAX_PENDING_SCROLL && y <= getHeightMinusInsets() + ITEM_HEIGHT + MAX_PENDING_SCROLL)
                {
                    Either<Pair<LexCompletionGroup, LexCompletion>, Pair<LexCompletionGroup, GroupNodeType>> key = Either.left(new Pair<>(group, group.completions.get(i)));
                    Node item = visible.computeIfAbsent(key, this::makeFlow);
                    item.translateYProperty().bind(groupAnimatedTranslate.get(groupIndex));
                    // Note -- deliberate use of == to compare for reference equality, as completions can be equal according to .equals() by appearing in normal and related sections:
                    FXUtility.setPseudoclass(item, "selected", sel != null && sel == key.<@Nullable LexCompletion>either(c -> c.getSecond(), g -> null));
                    toKeep.add(key);
                }
            }
            
            internalY = internalGroupEnd;
        }
        
        visible.entrySet().removeIf(e -> !Utility.contains(toKeep, e.getKey()));
        requestLayout();

        // Stop all current animations:
        groupAnimation.stop();
        if (attemptAnimate && oldTopY.length == groupTopY.length
            // Stop animating if the distance is so far it's going to take too long:    
            && IntStream.range(0, oldTopY.length).allMatch(i -> Math.abs(groupAnimatedTranslate.get(i).doubleValue() + oldTopY[i] - groupTopY[i]) < MAX_PENDING_SCROLL))
        {
            // Animate the group's transitions
            for (int i = 0; i < groupAnimatedTranslate.size(); i++)
            {
                DoubleProperty prop = groupAnimatedTranslate.get(i);
                prop.set(prop.get() + (oldTopY[i] - groupTopY[i]));
            }
            groupAnimation = new ParallelTransition(groupAnimatedTranslate.stream().map(prop -> {
                Timeline t = new Timeline(new KeyFrame(Duration.seconds(Math.abs(prop.get()) / PIXELS_PER_SECOND), new KeyValue(prop, 0.0, Interpolator.LINEAR)));
                t.setCycleCount(1);
                return t;
            }).toArray(Animation[]::new));
            groupAnimation.playFromStart();
        }
        else
        {
            for (DoubleProperty value : groupAnimatedTranslate)
            {
                value.set(0.0);
            }
        }
    }

    private Node makeFlow(Either<Pair<LexCompletionGroup, LexCompletion>, Pair<LexCompletionGroup, GroupNodeType>> lexCompletion)
    {
        CompletionRow row = new CompletionRow(lexCompletion.either(c -> c.getSecond().display.toGUI(), g -> (g.getFirst().header == null ? StyledString.s("") : g.getFirst().header).toGUI()), lexCompletion.either(c -> c.getSecond().sideText, g -> ""));
        if (lexCompletion.isRight())
            row.getStyleClass().add("lex-completion-header");
        row.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY)
            {
                lexCompletion.ifLeft(item -> {
                    Pair<Integer, Integer> index = Utility.get(completionIndexes, item.getSecond());
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
        boolean headersMatch = completions.size() == curCompletionGroups.size();
        for (int i = 0; i < curCompletionGroups.size() && headersMatch; i++)
        {
            if (!Objects.equals(curCompletionGroups.get(i).header, completions.get(i).header))
                headersMatch = false;
        }
        
        this.curCompletionGroups = completions;
        
        this.completionIndexes.clear();
        try
        {
            for (int i = 0; i < curCompletionGroups.size(); i++)
            {
                for (int j = 0; j < curCompletionGroups.get(i).completions.size(); j++)
                {
                    if (completionIndexes.put(curCompletionGroups.get(i).completions.get(j), new Pair<>(i, j)) != null)
                    {
                        throw new InternalException("Duplicate entry: " + curCompletionGroups.get(i));
                    }
                }
            }
        }
        catch (InternalException e)
        {
            // We shouldn't have duplicate completions, but don't let such an exception propagate:
            Log.log(e);
        }
        
        if (!headersMatch)
        {
            groupAnimation.stop();
            groupAnimatedTranslate.forEach(d -> d.setValue(0.0));
            groupAnimatedTranslate = Utility.replicateM(curCompletionGroups.size(), SimpleDoubleProperty::new);
        }
        
        // Need to update internal heights as select() will try to keep in view:
        recalculateChildren(headersMatch);
        // Keep same item selected, if still present:
        @Nullable LexCompletion sel = selectedItem.get();
        @Nullable Pair<Integer, Integer> target = sel == null ? null : completionIndexes.get(sel);
        select(target);
        // TODO should we try to maintain scroll position -- but how to calculate this? 
        updateScroll(VirtualPixels.ZERO, false);
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

        // Scroll to show item if one is selected:
        if (target != null)
        {
            // This is in terms of internal virtual height:
            @VirtualPixels double selectedTopY = (IntStream.range(0, target.getFirst()).mapToDouble(this::calcGroupHeight).sum()
                + (ITEM_HEIGHT * (target.getSecond() + (curCompletionGroups.get(target.getFirst()).header != null ? 1 : 0)))) * VirtualPixels.ONE;
            @VirtualPixels double selectedBottomY = selectedTopY + ITEM_HEIGHT * VirtualPixels.ONE;
            // This is in terms of display pixels:
            @DisplayPixels double nextY = target.getFirst() + 1 < groupTopY.length ? groupTopY[target.getFirst() + 1] : getHeightMinusInsets() * DisplayPixels.ONE;
            
            // Need to calculate the last visible pixel (in internal virtual height) before
            // the min height of the groups below it.  The last pixel if no groups are below is
            // canonicalScrollPosition + getHeightMinusInsets().
            double viewableToBottomOfGroup = (getHeightMinusInsets() - IntStream.range(target.getFirst() + 1, curCompletionGroups.size()).mapToDouble(this::calcMinGroupHeight).sum());
            @VirtualPixels double lastVisibleYInGroup = canonicalScrollPosition + viewableToBottomOfGroup * VirtualPixels.ONE;
            
            if (selectedBottomY > lastVisibleYInGroup)
            { 
                updateScroll(selectedBottomY - viewableToBottomOfGroup * VirtualPixels.ONE, false);
            }
            else if (selectedTopY < canonicalScrollPosition)
            {
                updateScroll(selectedTopY, false);
            }
            else
            {
                recalculateChildren(false);
            }
        }
        else
        {
            recalculateChildren(false);
        }
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
    private final class CompletionRow extends Region
    {
        private final HelpfulTextFlow mainText;
        private final Label sideText;
        private final Rectangle clip = new Rectangle();
        
        private int cachedPosition;

        public CompletionRow(Collection<Text> content, String sideText)
        {
            setFocusTraversable(false);
            mainText = new HelpfulTextFlow();
            mainText.getStyleClass().add("lex-completion-text-flow");
            mainText.getChildren().setAll(content);
            mainText.setFocusTraversable(false);
            mainText.setMouseTransparent(true);
            this.sideText = new Label(sideText);
            this.sideText.getStyleClass().add("side-text");
            this.sideText.setMouseTransparent(true);
            clip.widthProperty().bind(widthProperty());
            clip.setY(0);
            clip.setHeight(ITEM_HEIGHT);
            setClip(clip);
            // mainText goes in front of side text as it is more important:
            getChildren().setAll(this.sideText, mainText);
            getStyleClass().add("lex-completion");
        }

        @Override
        @OnThread(value = Tag.FXPlatform, ignoreParent = true)
        protected void layoutChildren()
        {
            mainText.resizeRelocate(getInsets().getLeft(), 0, getWidth() - getInsets().getLeft() - getInsets().getRight(), getHeight());
            try
            {
                // TODO figure out equivalent
                //mainText.getInternalTextLayout().setWrapWidth(0);
            }
            catch (Exception e)
            {
                Log.log(e);
            }
            double sideWidth = sideText.prefWidth(-1);
            double sideHeight = sideText.prefHeight(sideWidth);
            sideText.resizeRelocate(getWidth() - sideWidth - getInsets().getRight(), (getHeight() - sideHeight) / 2.0, sideWidth, sideHeight);
        }
    }
}
