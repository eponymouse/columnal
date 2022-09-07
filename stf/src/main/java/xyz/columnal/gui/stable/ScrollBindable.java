package xyz.columnal.gui.stable;

import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.gui.stable.ScrollGroup.Token;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Pair;

// An item which can have its scrolling bound to VirtScrollStrTextGrid
@OnThread(Tag.FXPlatform)
public interface ScrollBindable
{
    // extra pixels values are always positive.  scrollBy can be negative or positive
    // Return true if you need layout doing afterwards
    public boolean scrollXLayoutBy(Token token, double extraPixelsToShowBefore, double scrollBy, double extraPixelsToShowAfter);
    public boolean scrollYLayoutBy(Token token, double extraPixelsToShowBefore, double scrollBy, double extraPixelsToShowAfter);

    @OnThread(Tag.FXPlatform)
    public void redoLayoutAfterScroll();
    
    @OnThread(Tag.FXPlatform)
    public void updateClip();
}
