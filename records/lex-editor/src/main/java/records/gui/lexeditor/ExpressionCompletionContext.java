package records.gui.lexeditor;

import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.lexeditor.completion.LexCompletion;
import records.gui.lexeditor.completion.LexCompletionGroup;
import styled.StyledString;
import utility.Utility;

public class ExpressionCompletionContext extends CodeCompletionContext
{
    private final @Nullable StyledString entryPrompt;
    
    public ExpressionCompletionContext(ImmutableList<LexCompletionGroup> completions, @Nullable StyledString entryPrompt)
    {
        super(completions);
        this.entryPrompt = entryPrompt;
    }

    public ExpressionCompletionContext(CodeCompletionContext nestedCompletions, @CanonicalLocation int offsetBy)
    {
        super(nestedCompletions, offsetBy);
        this.entryPrompt = null;
    }

    @Override
    public @Nullable StyledString getEntryPrompt()
    {
        return entryPrompt;
    }
}
