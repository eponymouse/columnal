package records.gui.lexeditor;

import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.lexeditor.TopLevelEditor.DisplayType;
import records.gui.lexeditor.completion.LexCompletion;
import records.gui.lexeditor.completion.LexCompletionGroup;
import styled.StyledString;
import utility.Utility;

import java.util.EnumSet;

public class ExpressionCompletionContext extends CodeCompletionContext
{
    private final ImmutableMap<DisplayType, StyledString> infoAndPrompt;
    
    public ExpressionCompletionContext(ImmutableList<LexCompletionGroup> completions, ImmutableMap<DisplayType, StyledString> infoAndPrompt)
    {
        super(completions);
        this.infoAndPrompt = infoAndPrompt;
    }

    public ExpressionCompletionContext(CodeCompletionContext nestedCompletions, @CanonicalLocation int offsetBy)
    {
        super(nestedCompletions, offsetBy);
        this.infoAndPrompt = ImmutableMap.of();
    }

    @Override
    public ImmutableMap<DisplayType, StyledString> getInfoAndPrompt()
    {
        return infoAndPrompt;
    }
}
