package records.gui.lexeditor.completion;

import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import styled.StyledString;
import utility.Utility;

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
    
    public @Nullable LexCompletionGroup filterForPos(@CanonicalLocation int caretPos)
    {
        ImmutableList<LexCompletion> filtered = completions.stream().filter(c -> c.showFor(caretPos)).collect(ImmutableList.<LexCompletion>toImmutableList());
        if (filtered.isEmpty())
            return null;
        if (header == null || !minCollapsed.isPresent())
            return new LexCompletionGroup(filtered);
        else
            return new LexCompletionGroup(filtered, header, minCollapsed.getAsInt());
    }

    public LexCompletionGroup offsetBy(@CanonicalLocation int offsetBy)
    {
        ImmutableList<LexCompletion> offset = Utility.mapListI(completions, c -> c.offsetBy(offsetBy));

        if (header == null || !minCollapsed.isPresent())
            return new LexCompletionGroup(offset);
        else
            return new LexCompletionGroup(offset, header, minCollapsed.getAsInt());
    }
}
