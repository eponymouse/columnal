package records.gui.lexeditor.completion;

import annotation.units.CanonicalLocation;
import org.checkerframework.checker.nullness.qual.Nullable;

public interface LexCompletionListener extends InsertListener
{
    void complete(LexCompletion lexCompletion);
}
