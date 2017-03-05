package utility;

import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
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

    @Pure
    public @Nullable @Value Object getInner()
    {
        return innerItem;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TaggedValue that = (TaggedValue) o;

        if (tagIndex != that.tagIndex) return false;
        return innerItem != null ? innerItem.equals(that.innerItem) : that.innerItem == null;
    }

    @Override
    public int hashCode()
    {
        int result = tagIndex;
        result = 31 * result + (innerItem != null ? innerItem.hashCode() : 0);
        return result;
    }

    @Override
    public String toString()
    {
        return "TaggedValue{" +
            tagIndex +
            ": " + innerItem +
            '}';
    }
}
