package records.gui.expressioneditor;

import javafx.scene.input.DataFormat;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Map;

/**
 * Very basic interface for a saver that can be used to save
 * to the clipboard
 */
public interface ClipboardSaver
{
    public @Nullable Map<DataFormat, Object> finishClipboard();
}
