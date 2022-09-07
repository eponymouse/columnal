package xyz.columnal.gui.lexeditor.completion;

import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.styled.StyledString;
import xyz.columnal.utility.Utility;

import java.util.OptionalInt;

public class LexCompletionGroup
{
    final ImmutableList<LexCompletion> completions;
    final @Nullable StyledString header;
    // How many items to show as a minimum if the group is collapsed
    final int minCollapsed;

    public LexCompletionGroup(ImmutableList<LexCompletion> completions, @Nullable StyledString header, int minCollapsed)
    {
        this.completions = completions;
        this.header = header;
        this.minCollapsed = minCollapsed;
    }
    
    public @Nullable LexCompletionGroup filterForPos(@CanonicalLocation int caretPos)
    {
        ImmutableList<LexCompletion> filtered = completions.stream().filter(c -> c.showFor(caretPos)).collect(ImmutableList.<LexCompletion>toImmutableList());
        if (filtered.isEmpty())
            return null;
        return new LexCompletionGroup(filtered, header, minCollapsed);
    }

    public LexCompletionGroup offsetBy(@CanonicalLocation int offsetBy)
    {
        ImmutableList<LexCompletion> offset = Utility.mapListI(completions, c -> c.offsetBy(offsetBy));

        return new LexCompletionGroup(offset, header, minCollapsed);
    }
}
