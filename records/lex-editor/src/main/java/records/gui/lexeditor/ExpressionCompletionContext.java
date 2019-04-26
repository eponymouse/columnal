package records.gui.lexeditor;

import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import records.gui.lexeditor.completion.LexCompletion;
import records.gui.lexeditor.completion.LexCompletionGroup;

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
        return completions;
    }
}
