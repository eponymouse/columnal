package records.gui.stable;

import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.stable.ScrollGroup.Token;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

// An item which can have its scrolling bound to VirtScrollStrTextGrid
@OnThread(Tag.FXPlatform)
public interface ScrollBindable
{
    // extra pixels values are always positive.  scrollBy can be negative or positive
    public void scrollXLayoutBy(Token token, double extraPixelsToShowBefore, double scrollBy, double extraPixelsToShowAfter);
    public void scrollYLayoutBy(Token token, double extraPixelsToShowBefore, double scrollBy, double extraPixelsToShowAfter);
        
    @OnThread(Tag.FXPlatform)
    public void updateClip();
}
