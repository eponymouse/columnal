package records.gui.stable;

import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;

// An item which can have its scrolling bound to VirtScrollStrTextGrid
//package-visible
interface ScrollBindable
{
    @OnThread(Tag.FXPlatform)
    public void showAtOffset(@Nullable Pair<Integer, Double> rowAndPixelOffset, @Nullable Pair<Integer, Double> colAndPixelOffset);

    @OnThread(Tag.FXPlatform)
    public void updateClip();

    @OnThread(Tag.FXPlatform)
    public void columnWidthChanged(int columnIndex, double newWidth);

    @OnThread(Tag.FXPlatform)
    public void columnsChanged();
}
