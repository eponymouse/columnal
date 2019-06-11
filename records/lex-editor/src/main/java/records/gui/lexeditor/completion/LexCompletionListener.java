package records.gui.lexeditor.completion;

public interface LexCompletionListener
{
    void insert(String text);

    void complete(LexCompletion lexCompletion);
}
