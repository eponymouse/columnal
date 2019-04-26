package records.gui.lexeditor.completion;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import javafx.beans.binding.ObjectExpression;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javafx.geometry.Insets;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformConsumer;
import utility.gui.FXUtility;

import java.util.Collection;
import java.util.Set;

// Like a ListView, but with customisations to allow pinned "related" section
@OnThread(Tag.FXPlatform)
class LexCompletionList extends Region
{
    // The completions change all at once, so a mutable ImmutableList reference:
    private ImmutableList<LexCompletion> curCompletions = ImmutableList.of();
    
    // This map's values are always inserted into the children of this region
    private ObservableMap<LexCompletion, ClippedTextFlow> visible = FXCollections.observableHashMap();
    
    private int selectionIndex;
    // Holds a mirror of selectionIndex:
    private final ObjectProperty<@Nullable LexCompletion> selectedItem = new SimpleObjectProperty<>(null);
    // Always positive, in pixels from top of full list:
    private double scrollOffset = 0;
    private final double ITEM_HEIGHT = 24;
    
    public LexCompletionList(FXPlatformConsumer<LexCompletion> triggerCompletion)
    {
        getStyleClass().add("lex-completion-list");
        setFocusTraversable(false);
        visible.addListener((MapChangeListener<LexCompletion, TextFlow>) (MapChangeListener.Change<? extends LexCompletion, ? extends TextFlow> c) -> {
            if (c.wasRemoved())
                getChildren().remove(c.getValueRemoved());
            if (c.wasAdded())
                getChildren().add(c.getValueAdded());
        });
        
        setPrefWidth(300.0);
        setMaxHeight(USE_PREF_SIZE);
        addEventFilter(ScrollEvent.ANY, e -> {
            scrollOffset = Math.max(0, Math.min(scrollOffset - e.getDeltaY(), curCompletions.size() * ITEM_HEIGHT - getHeight()));
            FXUtility.mouse(this).recalculateChildren();
            e.consume();
        });
        setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY)
            {
                int itemIndex = (int) ((e.getY() + scrollOffset) / ITEM_HEIGHT);
                boolean wasAlreadySelected = FXUtility.mouse(this).getSelectedIndex() == itemIndex;
                if (e.getClickCount() >= 1)
                {
                    FXUtility.mouse(this).select(itemIndex);
                }

                // Clicking twice slowly also works this way:
                if ((e.getClickCount() == 2 || wasAlreadySelected) && selectionIndex > 0 && selectionIndex < curCompletions.size())
                    triggerCompletion.consume(curCompletions.get(selectionIndex));
            }
        });
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected double computePrefHeight(double width)
    {
        return Math.min(curCompletions.size(), 16) * ITEM_HEIGHT + 2 /* border */;
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    protected void layoutChildren()
    {
        Insets insets = getInsets();
        double y = insets.getTop() - (scrollOffset % ITEM_HEIGHT);
        double maxY = getHeight() - insets.getTop() - insets.getBottom();
        for (int i = (int)(scrollOffset / ITEM_HEIGHT); i < curCompletions.size() && y < maxY; i++, y += ITEM_HEIGHT)
        {
            ClippedTextFlow flow = visible.get(curCompletions.get(i));
            if (flow != null)
            {
                // Should always be non-null, but need to guard
                double width = getWidth() - insets.getRight() - insets.getLeft();
                flow.resizeRelocate(insets.getLeft(), y, width, ITEM_HEIGHT);
                flow.clip.setWidth(width);
                flow.clip.setY(Math.max(y, insets.getTop()) - y);
                flow.clip.setHeight(Math.min(ITEM_HEIGHT + y, maxY) - y);
            }
        }
    }
    
    private void recalculateChildren()
    {
        Set<LexCompletion> toKeep = Sets.newIdentityHashSet(); 
        double y = (scrollOffset % ITEM_HEIGHT) - ITEM_HEIGHT;
        for (int i = (int)(scrollOffset / ITEM_HEIGHT); i < curCompletions.size() && y < computePrefHeight(-1); i++, y += ITEM_HEIGHT)
        {
            TextFlow item = visible.computeIfAbsent(curCompletions.get(i), this::makeFlow);
            FXUtility.setPseudoclass(item, "selected", selectionIndex == i);
            toKeep.add(curCompletions.get(i));
        }
        visible.entrySet().removeIf(e -> !toKeep.contains(e.getKey()));
        requestLayout();
    }

    private ClippedTextFlow makeFlow(LexCompletion lexCompletion)
    {
        ClippedTextFlow textFlow = new ClippedTextFlow(lexCompletion.display.toGUI());
        textFlow.getStyleClass().add("lex-completion");
        textFlow.setMouseTransparent(true);
        return textFlow;
    }

    public void setCompletions(ImmutableList<LexCompletion> completions)
    {
        this.curCompletions = completions;
        // Keep same item selected, if still present:
        select(curCompletions.indexOf(selectedItem.get()));
    }

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void requestFocus()
    {
        // Can't be focused
    }
    
    public void select(int itemIndex)
    {
        selectionIndex = Math.min(curCompletions.size() - 1, itemIndex);
        selectedItem.setValue(selectionIndex < 0 ? null : curCompletions.get(selectionIndex));

        double minY = Math.max(0, selectionIndex) * ITEM_HEIGHT;
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
    
    public int getSelectedIndex()
    {
        return selectionIndex;
    }

    public ImmutableList<LexCompletion> getItems()
    {
        return curCompletions;
    }

    public ObjectExpression<@Nullable LexCompletion> selectedItemProperty()
    {
        return selectedItem;
    }

    public double getTotalTextLeftPad()
    {
        return getInsets().getLeft() + ((Region)getParent()).getInsets().getLeft() + visible.values().stream().findFirst().map(f -> f.getInsets().getLeft()).orElse(0.0);
    }
    
    @OnThread(Tag.FXPlatform)
    private class ClippedTextFlow extends TextFlow
    {
        private final Rectangle clip = new Rectangle();

        public ClippedTextFlow(Collection<Text> content)
        {
            getChildren().setAll(content);
            setClip(clip);
        }
    }
}
