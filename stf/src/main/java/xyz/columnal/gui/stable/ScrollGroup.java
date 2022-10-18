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

package xyz.columnal.gui.stable;

import com.google.common.collect.ImmutableList;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.input.ScrollEvent;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.gui.stable.SmoothScroller.ScrollClamp;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.gui.FXUtility;

import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.stream.Stream;

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
    private final SmoothScroller<ImmutableList<ScrollBindable>> smoothScrollX;
    private final SmoothScroller<ImmutableList<ScrollBindable>> smoothScrollY;
    final DoubleProperty translateXProperty = new SimpleDoubleProperty(0.0);
    final DoubleProperty translateYProperty = new SimpleDoubleProperty(0.0);
    // All the items that depend on us -- ScrollBindable items (like individual grids or scroll bars), and other scroll groups:
    private final IdentityHashMap<ScrollBindable, ScrollLock> directScrollDependents = new IdentityHashMap<>();
    private final IdentityHashMap<ScrollGroup, ScrollLock> dependentGroups = new IdentityHashMap<>();
    private boolean inUpdateClip;
    
    public ScrollGroup(ScrollClamp scrollClampX, ScrollClamp scrollClampY)
    {
        smoothScrollX = new SmoothScroller<>(translateXProperty, scrollClampX, FXUtility.mouse(this)::scrollXBy);
        smoothScrollY = new SmoothScroller<>(translateYProperty, scrollClampY, FXUtility.mouse(this)::scrollYBy);
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
        
        Stream<ScrollBindable> changed = Stream.empty();
        
        if (deltaX != 0.0)
            changed = Stream.concat(changed, smoothScrollX.smoothScroll(deltaX).map(l -> l.stream()).orElse(Stream.empty()));
        
        if (deltaY != 0.0)
            changed = Stream.concat(changed, smoothScrollY.smoothScroll(deltaY).map(l -> l.stream()).orElse(Stream.empty()));
        
        changed.distinct().forEach(s -> s.redoLayoutAfterScroll());
    }

    public void add(ScrollBindable scrollBindable, ScrollLock scrollLock)
    {
        directScrollDependents.put(scrollBindable, scrollLock);
        // TODO we need to set the scroll to right place immediately
    }

    public void add(ScrollGroup scrollGroup, ScrollLock scrollLock)
    {
        dependentGroups.put(scrollGroup, scrollLock);
        scrollGroup.parent = new Pair<>(this, scrollLock);
        if (scrollLock.includesHorizontal())
            scrollGroup.translateXProperty.bind(translateXProperty);
        if (scrollLock.includesVertical())
            scrollGroup.translateYProperty.bind(translateYProperty);
        // TODO we need to set the scroll to right place immediately
    }

    private Optional<ImmutableList<ScrollBindable>> scrollXBy(double extraBefore, double by, double extraAfter)
    {
        Token token = new Token();
        
        ImmutableList.Builder<ScrollBindable> changed = ImmutableList.builder();
        
        directScrollDependents.forEach((member, lock) -> {
            if (lock.includesHorizontal())
            {
                if (member.scrollXLayoutBy(token, extraBefore, by, extraAfter))
                    changed.add(member);
            }
        });
        dependentGroups.forEach((member, lock) -> {
            if (lock.includesHorizontal())
                member.scrollXBy(extraBefore, by, extraAfter).ifPresent(changed::addAll);
        });
        
        return Optional.of(changed.build()); 
    }

    private Optional<ImmutableList<ScrollBindable>> scrollYBy(double extraBefore, double by, double extraAfter)
    {
        Token token = new Token();

        ImmutableList.Builder<ScrollBindable> changed = ImmutableList.builder();

        directScrollDependents.forEach((member, lock) -> {
            if (lock.includesVertical())
            {
                if (member.scrollYLayoutBy(token, extraBefore, by, extraAfter))
                    changed.add(member);
            }
        });
        dependentGroups.forEach((member, lock) -> {
            if (lock.includesVertical())
                member.scrollYBy(extraBefore, by ,extraAfter).ifPresent(changed::addAll);
        });

        return Optional.of(changed.build());
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
    
    @OnThread(Tag.Any)
    public long _test_getScrollTimeNanos()
    {
        return smoothScrollX._test_getScrollTimeNanos();
    }
    
    @OnThread(Tag.Any)
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
