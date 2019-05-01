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
    public final @CanonicalLocation int lastShowPosIncl;
    public String content;
    StyledString display;
    public int relativeCaretPos;
    LexSelectionBehaviour selectionBehaviour;
    // HTML file name (e.g. function-abs.html) and optional anchor
    @Nullable Pair<String, @Nullable String> furtherDetailsURL;
    String sideText = "";

    private LexCompletion(@CanonicalLocation int startPos, @CanonicalLocation int lastShowPosIncl, String content, StyledString display, int relativeCaretPos, LexSelectionBehaviour selectionBehaviour, @Nullable Pair<String, @Nullable String> furtherDetailsURL, String sideText)
    {
        this.startPos = startPos;
        this.lastShowPosIncl = lastShowPosIncl;
        this.content = content;
        this.display = display;
        this.relativeCaretPos = relativeCaretPos;
        this.selectionBehaviour = selectionBehaviour;
        this.furtherDetailsURL = furtherDetailsURL;
        this.sideText = sideText;
    }

    public LexCompletion(@CanonicalLocation int startPos, int lengthToShowFor, String content)
    {
        this.startPos = startPos;
        @SuppressWarnings("units")
        @CanonicalLocation int lastShowPosIncl = startPos + lengthToShowFor;
        this.lastShowPosIncl = lastShowPosIncl;
        this.content = content;
        this.display = StyledString.s(content);
        this.relativeCaretPos = content.length();
        this.selectionBehaviour = LexSelectionBehaviour.NO_AUTO_SELECT;
        this.furtherDetailsURL = null;
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

    public LexCompletion withDisplay(StyledString display)
    {
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
    
    public LexCompletion withSideText(String sideText)
    {
        this.sideText = sideText;
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

    public LexCompletion offsetBy(@CanonicalLocation int offsetBy)
    {
        return new LexCompletion(startPos + offsetBy, lastShowPosIncl + offsetBy, content, display, relativeCaretPos, selectionBehaviour, furtherDetailsURL, sideText);
    }
}
