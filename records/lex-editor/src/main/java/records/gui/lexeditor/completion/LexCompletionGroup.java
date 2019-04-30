package records.gui.lexeditor.completion;

import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import styled.StyledString;

import java.util.OptionalInt;

public class LexCompletionGroup
{
    final ImmutableList<LexCompletion> completions;
    final @Nullable StyledString header;
    // If empty(), means show as many as you can even if others
    // are collapsed.  Top group will generally be empty() and other groups
    // present with 1 or 2.
    final OptionalInt minCollapsed;

    public LexCompletionGroup(ImmutableList<LexCompletion> directCompletions)
    {
        this.completions = directCompletions;
        this.header = null;
        this.minCollapsed = OptionalInt.empty();
    }
    
    public LexCompletionGroup(ImmutableList<LexCompletion> completions, StyledString header, int minCollapsed)
    {
        this.completions = completions;
        this.header = header;
        this.minCollapsed = OptionalInt.of(minCollapsed);
    }
    
    public LexCompletionGroup filterForPos(@CanonicalLocation int caretPos)
    {
        ImmutableList<LexCompletion> filtered = completions.stream().filter(c -> c.startPos <= caretPos && caretPos <= c.lastShowPosIncl).collect(ImmutableList.<LexCompletion>toImmutableList());
        if (header == null || !minCollapsed.isPresent())
            return new LexCompletionGroup(filtered);
        else
            return new LexCompletionGroup(filtered, header, minCollapsed.getAsInt());
    }
}
