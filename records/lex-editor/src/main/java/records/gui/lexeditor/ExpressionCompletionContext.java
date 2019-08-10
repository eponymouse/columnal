package records.gui.lexeditor;

import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.scene.Node;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.lexeditor.TopLevelEditor.DisplayType;
import records.gui.lexeditor.completion.LexCompletion;
import records.gui.lexeditor.completion.LexCompletionGroup;
import styled.StyledString;
import utility.FXPlatformBiFunction;
import utility.FXPlatformFunction;
import utility.Pair;
import utility.Utility;

import java.util.EnumSet;

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
