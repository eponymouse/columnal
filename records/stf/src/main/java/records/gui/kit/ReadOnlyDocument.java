package records.gui.kit;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import utility.Pair;

import java.util.Set;
import java.util.stream.Stream;

public class ReadOnlyDocument implements Document
{
    private ImmutableList<Pair<Set<String>, String>> content;

    public ReadOnlyDocument(ImmutableList<Pair<Set<String>, String>> content)
    {
        this.content = content;
    }

    public ReadOnlyDocument(String content)
    {
        this.content = ImmutableList.of(new Pair<>(ImmutableSet.of(), content));
    }

    @Override
    public Stream<Pair<Set<String>, String>> getStyledSpans()
    {
        return content.stream();
    }
}
