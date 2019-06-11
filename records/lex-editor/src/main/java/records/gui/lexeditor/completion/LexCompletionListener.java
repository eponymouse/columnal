package records.gui.lexeditor.completion;

import annotation.units.CanonicalLocation;

public interface LexCompletionListener
{
    /**
     * Replaces text between start and current caret position with text
     */
    void insert(@CanonicalLocation int start, String text);

    void complete(LexCompletion lexCompletion);
}
