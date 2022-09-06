package records.gui.dtf;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.gui.dtf.Document.TrackedPosition.Bias;
import utility.FXPlatformRunnable;
import utility.Pair;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ReadOnlyDocument extends Document
{
    private final boolean error;
    private ImmutableList<Pair<Set<String>, String>> content;

    /*public ReadOnlyDocument(ImmutableList<Pair<Set<String>, String>> content)
    {
        this.content = content;
    }*/

    public ReadOnlyDocument(String content, boolean error)
    {
        this.content = ImmutableList.of(new Pair<>(ImmutableSet.<String>of(), content));
        this.error = error;
    }

    public ReadOnlyDocument(String content)
    {
        this(content, false);
    }

    @Override
    Stream<Pair<Set<String>, String>> getStyledSpans(boolean focused)
    {
        return content.stream();
    }

    @Override
    void replaceText(int startPosIncl, int endPosExcl, String text)
    {
        // We're read-only, so do nothing
    }

    @Override
    TrackedPosition trackPosition(int pos, Bias bias, @Nullable FXPlatformRunnable onChange)
    {
        // No need to actually track; if content can't change, neither can positions:
        return new TrackedPosition(pos, bias, onChange);
    }

    @Override
    public int getLength()
    {
        return content.stream().mapToInt(p -> p.getSecond().length()).sum();
    }

    @Override
    boolean isEditable()
    {
        return false;
    }

    @Override
    public String getText()
    {
        return content.stream().map(p -> p.getSecond()).collect(Collectors.joining());
    }

    @Override
    boolean hasError()
    {
        return error;
    }

    @Override
    void focusChanged(boolean focused)
    {
    }

    @Override
    public void setAndSave(String content)
    {
        // Not applicable
    }
}
