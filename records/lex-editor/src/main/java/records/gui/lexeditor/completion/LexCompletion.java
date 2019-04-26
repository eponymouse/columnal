package records.gui.lexeditor.completion;

import annotation.units.CanonicalLocation;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.lexeditor.completion.LexAutoComplete.LexSelectionBehaviour;
import styled.StyledString;
import utility.Pair;

import java.util.Objects;

/**
 * Note -- the with methods modify this class in-place.  
 * This shouldn't be an issue as the methods are only
 * used in a builder style.
 */
public class LexCompletion
{
    public final @CanonicalLocation int startPos;
    public String content;
    public StyledString display;
    public int relativeCaretPos;
    public LexSelectionBehaviour selectionBehaviour;
    // HTML file name (e.g. function-abs.html) and optional anchor
    public @Nullable Pair<String, @Nullable String> furtherDetailsURL;

    private LexCompletion(@CanonicalLocation int startPos, String content, int relativeCaretPos, LexSelectionBehaviour selectionBehaviour, @Nullable Pair<String, @Nullable String> furtherDetailsURL)
    {
        this.startPos = startPos;
        this.content = content;
        this.display = StyledString.s(content);
        this.relativeCaretPos = relativeCaretPos;
        this.selectionBehaviour = selectionBehaviour;
        this.furtherDetailsURL = furtherDetailsURL;
    }

    public LexCompletion(@CanonicalLocation int startPos, String content)
    {
        this(startPos, content, content.length(), LexSelectionBehaviour.NO_AUTO_SELECT, null);
    }
    
    public LexCompletion withReplacement(String newContent)
    {
        this.content = newContent;
        this.display = StyledString.s(newContent);
        return this;
    }

    public LexCompletion withReplacement(String newContent, StyledString display)
    {
        this.content = newContent;
        this.display = display;
        return this;
    }
    
    public LexCompletion withCaretPosAfterCompletion(int pos)
    {
        this.relativeCaretPos = pos;
        return this;
    }
    
    public LexCompletion withSelectionBehaviour(LexSelectionBehaviour selectionBehaviour)
    {
        this.selectionBehaviour = selectionBehaviour;
        return this;
    }
    
    public LexCompletion withFurtherDetailsURL(@Nullable String url)
    {
        this.furtherDetailsURL = url == null ? null : new Pair<>(url, null);
        return this;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LexCompletion that = (LexCompletion) o;
        return startPos == that.startPos &&
                relativeCaretPos == that.relativeCaretPos &&
                content.equals(that.content) &&
                display.equals(that.display) &&
                selectionBehaviour == that.selectionBehaviour &&
                Objects.equals(furtherDetailsURL, that.furtherDetailsURL);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(startPos, content, display, relativeCaretPos, selectionBehaviour, furtherDetailsURL);
    }
}
