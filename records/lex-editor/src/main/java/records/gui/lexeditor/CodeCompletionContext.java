package records.gui.lexeditor;

import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import records.gui.lexeditor.completion.LexCompletion;
import records.gui.lexeditor.completion.LexCompletionGroup;
import utility.Utility;

public class CodeCompletionContext
{
    protected final ImmutableList<LexCompletionGroup> completions;

    public CodeCompletionContext(ImmutableList<LexCompletionGroup> completions)
    {
        this.completions = completions;
    }

    public CodeCompletionContext(CodeCompletionContext nestedCompletions, @CanonicalLocation int offsetBy)
    {
        this(Utility.mapListI(nestedCompletions.completions, cc -> cc.offsetBy(offsetBy)));
    }

    public ImmutableList<LexCompletionGroup> getCompletionsFor(@CanonicalLocation int caretPos)
    {
        return completions.stream().flatMap(g -> Utility.streamNullable(g.filterForPos(caretPos))).collect(ImmutableList.<LexCompletionGroup>toImmutableList());
    }
}
