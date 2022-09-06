package records.gui.lexeditor;

import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import javafx.scene.Node;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.lexeditor.TopLevelEditor.DisplayType;
import records.gui.lexeditor.completion.LexCompletion;
import records.gui.lexeditor.completion.LexCompletionGroup;
import xyz.columnal.styled.StyledString;
import xyz.columnal.utility.Pair;
import xyz.columnal.utility.Utility;

import java.util.EnumMap;

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
