package records.gui.lexeditor;

import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import records.gui.lexeditor.completion.LexCompletion;
import records.gui.lexeditor.completion.LexCompletionGroup;
import utility.Utility;

public class ExpressionCompletionContext extends CodeCompletionContext
{
    public ExpressionCompletionContext(ImmutableList<LexCompletionGroup> completions)
    {
        super(completions);
    }

    public ExpressionCompletionContext(CodeCompletionContext nestedCompletions, @CanonicalLocation int offsetBy)
    {
        super(nestedCompletions, offsetBy);
    }
}
