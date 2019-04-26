package records.gui.lexeditor.completion;

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
}
