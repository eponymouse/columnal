package records.gui.flex;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.fxmisc.richtext.StyleClassedTextArea;

public class FlexibleTextField extends StyleClassedTextArea
{
    private @MonotonicNonNull EditorKit<?> editorKit;
    
    public FlexibleTextField()
    {
        getStyleClass().add("flexible-text-field");
    }

    public <T> void resetContent(EditorKit<T> editorKit)
    {
        if (this.editorKit != null)
        {
            this.editorKit.setField(null);
        }

        this.editorKit = editorKit;
        this.editorKit.setField(this);
        replace(editorKit.getLatestDocument());

        setEditable(this.editorKit.isEditable());
    }

    public @Nullable EditorKit<?> getEditorKit()
    {
        return editorKit;
    }
}
