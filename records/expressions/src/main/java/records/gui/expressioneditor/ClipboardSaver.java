package records.gui.expressioneditor;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Very basic interface for a saver that can be used to save
 * to the clipboard
 */
public interface ClipboardSaver
{
    public @Nullable String finishClipboard();
}
