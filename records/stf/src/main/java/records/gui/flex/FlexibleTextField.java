package records.gui.flex;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.fxmisc.richtext.StyleClassedTextArea;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.gui.FXUtility;

@OnThread(Tag.FXPlatform)
public class FlexibleTextField extends StyleClassedTextArea
{
    private @MonotonicNonNull EditorKit<?> editorKit;
    
    public FlexibleTextField()
    {
        super(false);
        getStyleClass().add("flexible-text-field");
        setPrefHeight(FXUtility.measureNotoSansHeight());

        FXUtility.addChangeListenerPlatformNN(textProperty(), text -> {
            if (editorKit != null)
                editorKit.fieldChanged(text, isFocused());
        });
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
