package records.gui.stable;

import javafx.animation.AnimationTimer;
import javafx.animation.Interpolator;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformFunction;
import utility.FXPlatformRunnable;

// Smooth scrolling class.  Handles smooth scrolling on a single axis:
@OnThread(Tag.FXPlatform)
class SmoothScroller
{
    // AnimationTimer is run every frame, and so lets us do smooth scrolling:
    private @MonotonicNonNull AnimationTimer scroller;
    // Start time of current animation (scrolling again resets this) and target end time:
    private long scrollStartNanos;
    private long scrollEndNanos;
    // Always heading towards zero.  If it's negative, then the user is scrolling left/up,
    // and thus we are currently that many pixels right/below of where it really should be,
    // and then the animation will animate the content left/up.  If it's positive, invert all that.
    private double scrollOffset;
    // Scroll offset at scrollStartNanos
    private double scrollStartOffset;
    private static final long SCROLL_TIME_NANOS = 300_000_000L;

    // The translateX/translateY of container, depending on which axis we are:
    private final DoubleProperty translateProperty;
    // extraRows/extraCols, depending on which axis we are:
    private final IntegerProperty extraRowCols;
    // Reference to scrollLayoutXBy/scrollLayoutYBy, depending on axis:
    private final FXPlatformFunction<Double, Double> scrollLayoutBy;
    // Given a scroll offset, works out how many extra rows/cols we need:
    private final FXPlatformFunction<Double, Integer> calcExtraRowCols;
    private final FXPlatformRunnable updateClip;
    private final int maxSmoothScrollItems;

    SmoothScroller(DoubleProperty translateProperty, int maxSmoothScrollItems, IntegerProperty extraRowCols, FXPlatformFunction<Double, Double> scrollLayoutBy, FXPlatformFunction<Double, Integer> calcExtraRowCols, FXPlatformRunnable updateClip)
    {
        this.translateProperty = translateProperty;
        this.extraRowCols = extraRowCols;
        this.scrollLayoutBy = scrollLayoutBy;
        this.calcExtraRowCols = calcExtraRowCols;
        this.updateClip = updateClip;
        this.maxSmoothScrollItems = maxSmoothScrollItems;
    }

    public void smoothScroll(double delta)
    {
        if (scroller == null)
        {
            scroller = new AnimationTimer()
            {
                @Override
                public void handle(long now)
                {
                    // If scroll end time in future, and our target scroll is more than 1/8th pixel away:
                    if (scrollEndNanos > now && Math.abs(scrollOffset) > 0.125)
                    {
                        scrollOffset = Interpolator.EASE_BOTH.interpolate(scrollStartOffset, 0, (double) (now - scrollStartNanos) / (scrollEndNanos - scrollStartNanos));
                        translateProperty.set(scrollOffset);
                    }
                    else
                    {
                        translateProperty.set(0.0);
                        scrollOffset = 0.0;
                        extraRowCols.set(0);
                        stop();
                    }
                    updateClip.run();
                }
            };
        }

        // Reset start and end time:
        long now = System.nanoTime();
        boolean justStarted = scrollEndNanos < now;
        if (justStarted)
            scrollStartNanos = now;
        scrollEndNanos = now + SCROLL_TIME_NANOS;

        if (delta != 0.0)
        {
            // We subtract from current offset, because we may already be mid-scroll in which
            // case we don't want to jump, just want to add on (we will go faster to cover this
            // because scroll will be same duration but longer):
            scrollOffset += scrollLayoutBy.apply(delta);
            int extra = calcExtraRowCols.apply(scrollOffset);
            // Don't let offset get too large or we will need too many extra rows:
            if (Math.abs(extra) > maxSmoothScrollItems)
            {
                // Jump to the destination:
                scrollOffset = 0;
                extraRowCols.set(0);
            }
            else
            {
                extraRowCols.set(extra);
            }
            if (justStarted)
                scrollStartOffset = scrollOffset;
            translateProperty.set(scrollOffset);
        }

        // Start the smooth scrolling animation:
        if (scrollOffset != 0.0)
            scroller.start();
    }
}