package records.gui.lexeditor;

import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import javafx.scene.Node;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.lexeditor.TopLevelEditor.DisplayType;
import records.gui.lexeditor.completion.LexCompletion;
import records.gui.lexeditor.completion.LexCompletionGroup;
import xyz.columnal.styled.StyledString;
import xyz.columnal.utility.FXPlatformBiFunction;
import xyz.columnal.utility.FXPlatformFunction;
import xyz.columnal.utility.Pair;
import xyz.columnal.utility.Utility;

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
