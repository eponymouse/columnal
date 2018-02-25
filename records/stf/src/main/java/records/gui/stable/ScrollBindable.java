package records.gui.stable;

import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

// An item which can have its scrolling bound to VirtScrollStrTextGrid
public interface ScrollBindable
{
    @OnThread(Tag.FXPlatform)
    public void showAtOffset(@Nullable Pair<@AbsRowIndex Integer, Double> rowAndPixelOffset, @Nullable Pair<@AbsColIndex Integer, Double> colAndPixelOffset);

    @OnThread(Tag.FXPlatform)
    public void updateClip();
}
