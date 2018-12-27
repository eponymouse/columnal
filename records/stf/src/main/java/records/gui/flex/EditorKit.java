package records.gui.flex;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.fxmisc.richtext.model.SimpleEditableStyledDocument;
import org.fxmisc.richtext.model.StyledDocument;
import org.fxmisc.richtext.model.StyledText;

import java.util.Collection;

public class EditorKit<T>
{
    private final Recogniser<T> recogniser;

    public EditorKit(Recogniser<T> recogniser)
    {
        this.recogniser = recogniser;
    }

    public boolean isEditable()
    {
        return true;
    }

    public void setField(@Nullable FlexibleTextField flexibleTextField)
    {
        
    }

    public StyledDocument<Collection<String>, StyledText<Collection<String>>, Collection<String>> getLatestDocument()
    {
        return new SimpleEditableStyledDocument<>(ImmutableList.of(), ImmutableList.of());
    }
}
