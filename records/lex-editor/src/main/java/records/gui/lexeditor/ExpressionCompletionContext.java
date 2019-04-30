package records.gui.lexeditor;

import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import records.gui.lexeditor.completion.LexCompletion;
import records.gui.lexeditor.completion.LexCompletionGroup;
import utility.Utility;

public class ExpressionCompletionContext implements CodeCompletionContext
{
    private final ImmutableList<LexCompletionGroup> completions;

    public ExpressionCompletionContext(ImmutableList<LexCompletionGroup> completions)
    {
        this.completions = completions;
    }

    @Override
    public ImmutableList<LexCompletionGroup> getCompletionsFor(@CanonicalLocation int caretPos)
    {
        return Utility.mapListI(completions, g -> g.filterForPos(caretPos));
    }
}
