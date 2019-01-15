package records.gui.flex;

import org.checkerframework.checker.nullness.qual.Nullable;
import styled.StyledString;
import utility.Either;
import utility.Pair;

// An uneditable EditorKit that displays a simple label (e.g. error message)
public class EditorKitSimpleLabel implements EditorKitInterface
{
    private final String content;
    
    public EditorKitSimpleLabel(String content)
    {
        this.content = content;
    }

    @Override
    public boolean isEditable()
    {
        return false;
    }

    @Override
    public void setField(@Nullable FlexibleTextField field)
    {
        if (field != null)
            field.replaceText(content);
    }

    @Override
    public void relinquishFocus()
    {
        // Shouldn't ever have focus
    }

    @Override
    public void focusChanged(String curContent, boolean focused)
    {
        // Nothing to do
    }

    @Override
    public @Nullable <T> EditorKit<T> asEditableKit(Class<T> itemClass)
    {
        // We are not an EditorKit:
        return null;
    }
}
