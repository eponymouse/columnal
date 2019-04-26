package records.gui.lexeditor;

import annotation.units.CanonicalLocation;
import com.google.common.collect.ImmutableList;
import records.gui.lexeditor.completion.LexCompletion;

public interface CodeCompletionContext
{
    public ImmutableList<LexCompletion> getCompletionsFor(@CanonicalLocation int caretPos);
}
