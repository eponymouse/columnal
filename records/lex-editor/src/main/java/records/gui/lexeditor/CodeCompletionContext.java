package records.gui.lexeditor;

import annotation.units.SourceLocation;
import com.google.common.collect.ImmutableList;
import records.gui.lexeditor.LexAutoComplete.LexCompletion;

public interface CodeCompletionContext
{
    public ImmutableList<LexCompletion> getCompletionsFor(@SourceLocation int caretPos);
}
