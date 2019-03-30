package records.gui.lexeditor;

import annotation.units.SourceLocation;
import com.google.common.collect.ImmutableList;
import records.gui.lexeditor.LexAutoComplete.LexCompletion;

public class ExpressionCompletionContext implements CodeCompletionContext
{
    private final ImmutableList<LexCompletion> completions;

    public ExpressionCompletionContext(ImmutableList<LexCompletion> completions)
    {
        this.completions = completions;
    }

    @Override
    public ImmutableList<LexCompletion> getCompletionsFor(@SourceLocation int caretPos)
    {
        return completions;
    }
}
