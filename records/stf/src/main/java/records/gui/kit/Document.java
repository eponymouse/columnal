package records.gui.kit;

import com.google.common.collect.ImmutableList;
import javafx.scene.Node;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.kit.Document.TrackedPosition.Bias;
import utility.FXPlatformRunnable;
import utility.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public abstract class Document
{
    private final List<DocumentListener> listeners = new ArrayList<>();

    public static class TrackedPosition
    {
        public static enum Bias {BACKWARD, FORWARD};
        
        private final Bias bias;
        private final @Nullable FXPlatformRunnable onChange;
        private int position;

        TrackedPosition(int position, Bias bias, @Nullable FXPlatformRunnable onChange)
        {
            this.bias = bias;
            this.position = position;
            this.onChange = onChange;
        }

        public Bias getBias()
        {
            return bias;
        }

        public int getPosition()
        {
            return position;
        }

        public void moveBy(int amount)
        {
            position += amount;
            if (onChange != null)
                onChange.run();
        }

        public void moveTo(int newPos)
        {
            this.position = newPos;
            if (onChange != null)
                onChange.run();
        }
    }
    
    // Package-protected
    abstract Stream<Pair<Set<String>, String>> getStyledSpans();

    // Package-protected
    abstract void replaceText(int startPosIncl, int endPosExcl, String text);

    // Package-protected
    abstract TrackedPosition trackPosition(int pos, Bias bias, @Nullable FXPlatformRunnable onChange);

    public abstract int getLength();

    abstract boolean isEditable();

    abstract String getText();

    abstract boolean hasError();

    // Bit weird in Document, but it's easiest that way
    void defocus()
    {
    }
    
    static interface DocumentListener
    {
        public void documentChanged();
    }
    
    final void addListener(DocumentListener listener)
    {
        listeners.add(listener);
    }
    
    final void removeListener(DocumentListener listener)
    {
        listeners.removeAll(ImmutableList.of(listener));
    }
    
    protected void notifyListeners()
    {
        for (DocumentListener listener : listeners)
        {
            listener.documentChanged();
        }
    }
}
