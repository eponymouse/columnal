package records.data;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 10/01/2017.
 */
@OnThread(Tag.Any)
public @Value class TaggedValue
{
    private final int tagIndex;
    private @Nullable @Value Object innerItem;

    public @Value TaggedValue(int tagIndex, @Nullable @Value Object innerItem)
    {
        this.tagIndex = tagIndex;
        this.innerItem = innerItem;
    }

    public int getTagIndex()
    {
        return tagIndex;
    }

    public @Nullable @Value Object getInner()
    {
        return innerItem;
    }
}
