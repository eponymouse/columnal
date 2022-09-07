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

import javafx.animation.AnimationTimer;
import javafx.animation.Interpolator;
import javafx.beans.property.DoubleProperty;
import xyz.columnal.log.Log;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.Optional;

/**
 * Smooth scrolling class.  Handles smooth scrolling on a single axis
 * 
 * Does not have an idea of absolute scroll position, only relative positions.
 * 
 * Its main job is that when a scroll is triggered, let's say 100 pixels down, 
 * it immediately scrolls the layout down 100 pixels.  Then it applies a translate
 * of -100 so that initially, nothing looks like it has moved.  Then it scrolls
 * the translate property towards zero, so that the scroll is animated without
 * needing to do multiple layout passes.
 * 
 * @param <R> The type returned by the scroller (used for book-keeping such as combining multipl
 *           calls to redo the layout into one)
 */
@OnThread(Tag.FXPlatform)
public class SmoothScroller<R extends @NonNull Object>
{
    // AnimationTimer is run every frame, and so lets us do smooth scrolling:
    private @MonotonicNonNull AnimationTimer scrollTimer;
    // Start time of current animation (scrolling again resets this) and target end time:
    private long scrollStartNanos;
    private long scrollEndNanos;
    // Always heading towards zero.  If it's negative, then the user is scrolling left/up,
    // and thus we are currently that many pixels right/below of where it really should be,
    // and then the animation will animate the content left/up.  If it's positive, invert all that.
    private double scrollOffset;
    // Scroll offset at scrollStartNanos
    private double scrollStartOffset;

    // Not static final, because some tests alter it:
    @OnThread(Tag.Any)
    private long scrollTimeNanos = 200_000_000L;

    // The translateX/translateY of container, depending on which axis we are:
    private final DoubleProperty translateProperty;
    private final ScrollClamp scrollClamp;
    // Reference to scrollLayoutXBy/scrollLayoutYBy, depending on axis:
    private final Scroller<R> scroller;
    private double MAX_SCROLL_OFFSET = 500.0;
    private Interpolator interpolator = Interpolator.EASE_BOTH;

    public static interface ScrollClamp
    {
        // Given an ideal amount of pixels to scroll, clamps it in the case that we are reaching the edge:
        double clampScroll(double amount);
    }
    
    public static interface Scroller<R>
    {
        /**
         * Scrolls layout by scrollBy, but also instructs the renderer to render margins before and/or after
         * the current node for smooth scrolling purposes.  Both may be zero.
         */
        Optional<@NonNull R> scrollLayoutBy(double extraPixelsToShowBefore, double scrollBy, double extraPixelsToShowAfter);
    }
    
    SmoothScroller(DoubleProperty translateProperty, ScrollClamp scrollClamp, Scroller<R> scroller)
    {
        this.translateProperty = translateProperty;
        this.scrollClamp = scrollClamp;
        this.scroller = scroller;
    }

    public Optional<@NonNull R> smoothScroll(double delta)
    {
        if (delta == 0.0)
            return Optional.empty();
        
        if (scrollTimer == null)
        {
            scrollTimer = new AnimationTimer()
            {
                @Override
                public void handle(long now)
                {
                    // If scroll end time in future, and our target scroll is more than 1/8th pixel away:
                    if (scrollEndNanos > now && Math.abs(scrollOffset) > 0.125)
                    {
                        scrollOffset = interpolator.interpolate(scrollStartOffset, 0, (double) (now - scrollStartNanos) / (scrollEndNanos - scrollStartNanos));
                        //Log.debug("Scroll Offset: " + scrollOffset + " from start " + scrollStartOffset + " and dur " + (now - scrollStartNanos) + " of " + (scrollEndNanos - scrollStartNanos));
                        translateProperty.set(scrollOffset);
                    }
                    else
                    {
                        translateProperty.set(0.0);
                        scrollOffset = 0.0;
                        stop();
                    }
                }
            };
        }

        // Reset start and end time:
        long now = System.nanoTime();
        boolean justStarted = scrollEndNanos < now;
        scrollStartNanos = now;
        if (justStarted)
        {
            interpolator = Interpolator.EASE_BOTH;
        }
        else
        {
            // If we're already underway, interpolating again with an ease-in makes
            // a weird bump during scrolling, so we switch to just easing out:
            interpolator = Interpolator.EASE_OUT;
        }
    
        // We subtract from current offset, because we may already be mid-scroll in which
        // case we don't want to jump, just want to add on (we will go faster to cover this
        // because scroll will be same duration but longer):
        double clamped = scrollClamp.clampScroll(delta);
        this.scrollOffset -= clamped;
        // Don't let offset get too large or we will need too many extra rows:
        if (scrollOffset == 0.0 || Math.abs(this.scrollOffset) > MAX_SCROLL_OFFSET)
        {
            // Jump to the destination:
            this.scrollOffset = 0.0;
            translateProperty.set(0.0);
            scrollTimer.stop();
        }
        else
        {
            if (justStarted)
                scrollStartOffset = this.scrollOffset;

            if (clamped != delta)
            {
                double proportionOfRequested = Math.abs(clamped / delta);
                scrollEndNanos = now + (long)((double)scrollTimeNanos * proportionOfRequested);
            }
            else
            {
                scrollEndNanos = now + scrollTimeNanos;
            }
            
            // Start the smooth scrolling animation:
            scrollTimer.start();
        }
        //Log.debug("Scrolling by " + clamped);
        Optional<@NonNull R> result = Optional.empty();
        if (clamped != 0.0 || this.scrollOffset != 0.0)
            result = scroller.scrollLayoutBy(Math.min(-this.scrollOffset, 0), clamped, Math.max(-this.scrollOffset, 0));
        translateProperty.set(this.scrollOffset);
        return result;
    }
    
    @OnThread(Tag.Any)
    public synchronized long _test_getScrollTimeNanos()
    {
        return scrollTimeNanos;
    }

    @OnThread(Tag.Any)
    public synchronized void _test_setScrollTimeNanos(long scrollTimeNanos)
    {
        this.scrollTimeNanos = scrollTimeNanos;
    }
}
