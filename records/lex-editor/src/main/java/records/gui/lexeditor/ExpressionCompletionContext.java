package records.gui.lexeditor;

import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.lexeditor.TopLevelEditor.DisplayType;
import records.gui.lexeditor.completion.LexCompletion;
import records.gui.lexeditor.completion.LexCompletionGroup;
import styled.StyledString;
import utility.FXPlatformFunction;
import utility.Utility;

import java.util.EnumSet;

public class ExpressionCompletionContext extends CodeCompletionContext
{
    private final FXPlatformFunction<@CanonicalLocation Integer, ImmutableMap<DisplayType, StyledString>> infoAndPromptForPosition;
    
    public ExpressionCompletionContext(ImmutableList<LexCompletionGroup> completions, FXPlatformFunction<@CanonicalLocation Integer, ImmutableMap<DisplayType, StyledString>> infoAndPromptForPosition)
    {
        super(completions);
        this.infoAndPromptForPosition = infoAndPromptForPosition;
    }

    public ExpressionCompletionContext(CodeCompletionContext nestedCompletions, @CanonicalLocation int offsetBy)
    {
        super(nestedCompletions, offsetBy);
        this.infoAndPromptForPosition = n -> ImmutableMap.<DisplayType, StyledString>of();
    }

    @Override
    public ImmutableMap<DisplayType, StyledString> getInfoAndPrompt(@CanonicalLocation int position)
    {
        return infoAndPromptForPosition.apply(position);
    }
}
