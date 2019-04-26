package records.gui.lexeditor;

import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import records.gui.lexeditor.completion.LexCompletion;
import records.gui.lexeditor.completion.LexCompletionGroup;

public interface CodeCompletionContext
{
    public ImmutableList<LexCompletionGroup> getCompletionsFor(@CanonicalLocation int caretPos);
}
