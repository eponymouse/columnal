package xyz.columnal.gui.lexeditor.completion;

import annotation.units.CanonicalLocation;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;

public interface InsertListener
{
    /**
     * Replaces text between start and current caret position with text.  If start is null, use caret position.
     */
    @OnThread(Tag.FXPlatform)
    void insert(@Nullable @CanonicalLocation Integer start, String text);
}
