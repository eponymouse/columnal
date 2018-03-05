package records.gui.stable;

import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import javafx.beans.binding.IntegerExpression;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.input.ScrollEvent;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.stable.SmoothScroller.ScrollClamp;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformBiFunction;
import utility.FXPlatformFunction;
import utility.Pair;
import utility.gui.FXUtility;

import java.util.IdentityHashMap;

/**
 * A scroll group is for the following notion.  A grid has a scroll position, both horizontally, and vertically.
 * It also has two scroll bars, which have a horizontal or vertical position respectively.  Added to this,
 * tables can lock together so that they scroll in unison.  So you have a morass of grids and scroll bars, all of
 * which somehow scroll together (at least in one of the dimensions).  How can we manage this?
 *
 * The answer is that all scroll events are treated by each item as a scroll request, and forwarded up to the
 * scroll group.  Every morass that is somehow scroll-locked together belongs to one scroll group.  Once the
 * scroll group decides where to scroll to, it makes calls back down to all the member items
 */
@OnThread(Tag.FXPlatform)
public class ScrollGroup
{
    private @Nullable Pair<ScrollGroup, ScrollLock> parent;
    private final SmoothScroller smoothScrollX;
    private final SmoothScroller smoothScrollY;
    final DoubleProperty translateXProperty = new SimpleDoubleProperty(0.0);
    final DoubleProperty translateYProperty = new SimpleDoubleProperty(0.0);
    // All the items that depend on us -- ScrollBindable items (like individual grids or scroll bars), and other scroll groups:
    private final IdentityHashMap<ScrollBindable, ScrollLock> directScrollDependents = new IdentityHashMap<>();
    private final IdentityHashMap<ScrollGroup, ScrollLock> dependentGroups = new IdentityHashMap<>();
    private boolean inUpdateClip;
    
    public ScrollGroup(ScrollClamp scrollClampX, ScrollClamp scrollClampY)
    {
        smoothScrollX = new SmoothScroller(translateXProperty, scrollClampX, FXUtility.mouse(this)::scrollXBy);
        smoothScrollY = new SmoothScroller(translateYProperty, scrollClampY, FXUtility.mouse(this)::scrollYBy);
    }

    public void requestScroll(ScrollEvent scrollEvent)
    {
        requestScrollBy(scrollEvent.getDeltaX(), scrollEvent.getDeltaY());
    }

    public void requestScrollBy(double deltaX, double deltaY)
    {
        // If we're not the root group, forward up the chain:
        if (parent != null)
        {
            @NonNull Pair<ScrollGroup, ScrollLock> parentFinal = parent;
            // Forward what's applicable up to parent:
            parentFinal.getFirst().requestScrollBy(parentFinal.getSecond().includesHorizontal() ? deltaX : 0.0, parentFinal.getSecond().includesVertical() ? deltaY : 0.0);
            
            if (parentFinal.getSecond().includesHorizontal())
                deltaX = 0.0;
            if (parentFinal.getSecond().includesVertical())
                deltaY = 0.0;
            
            // Do the rest ourselves, if any:
        }
        
        if (deltaX != 0.0)
            smoothScrollX.smoothScroll(deltaX);
        
        if (deltaY != 0.0)
            smoothScrollY.smoothScroll(deltaY);
    }

    public void add(ScrollBindable scrollBindable, ScrollLock scrollLock)
    {
        directScrollDependents.put(scrollBindable, scrollLock);
        // TODO we need to set the scroll to right place immediately
    }

    void add(ScrollGroup scrollGroup, ScrollLock scrollLock)
    {
        dependentGroups.put(scrollGroup, scrollLock);
        scrollGroup.parent = new Pair<>(this, scrollLock);
        // TODO we need to set the scroll to right place immediately
    }

    private void scrollXBy(double extraBefore, double by, double extraAfter)
    {
        Token token = new Token();
        
        directScrollDependents.forEach((member, lock) -> {
            if (lock.includesHorizontal())
                member.scrollXLayoutBy(token, extraBefore, by ,extraAfter);
        });
        dependentGroups.forEach((member, lock) -> {
            if (lock.includesHorizontal())
                member.scrollXBy(extraBefore, by ,extraAfter);
        });
    }

    private void scrollYBy(double extraBefore, double by, double extraAfter)
    {
        Token token = new Token();

        directScrollDependents.forEach((member, lock) -> {
            if (lock.includesVertical())
                member.scrollYLayoutBy(token, extraBefore, by ,extraAfter);
        });
        dependentGroups.forEach((member, lock) -> {
            if (lock.includesVertical())
                member.scrollYBy(extraBefore, by ,extraAfter);
        });
    }

    public void updateClip()
    {
        if (parent != null)
        {
            parent.getFirst().updateClip();
        }
        // Members may call back this same method, so need to avoid an infinite loop:
        if (!inUpdateClip)
        {
            inUpdateClip = true;
            for (ScrollBindable scrollBindable : directScrollDependents.keySet())
            {
                scrollBindable.updateClip();
            }
            for (ScrollGroup scrollGroup : dependentGroups.keySet())
            {
                scrollGroup.updateClip();
            }
            inUpdateClip = false;
        }
    }

    public long _test_getScrollTimeNanos()
    {
        return smoothScrollX._test_getScrollTimeNanos();
    }
    
    public void _test_setScrollTimeNanos(long nanos)
    {
        smoothScrollX._test_setScrollTimeNanos(nanos);
        smoothScrollY._test_setScrollTimeNanos(nanos);
    }

    public class Token
    {
        private Token() {}
    }

    public DoubleProperty translateXProperty()
    {
        return translateXProperty;
    }

    public DoubleProperty translateYProperty()
    {
        return translateYProperty;
    }

    public static enum ScrollLock
    {
        HORIZONTAL, VERTICAL, BOTH;

        public boolean includesVertical()
        {
            return this == VERTICAL || this == BOTH;
        }

        public boolean includesHorizontal()
        {
            return this == HORIZONTAL || this == BOTH;
        }
    }
}
