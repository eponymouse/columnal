package xyz.columnal.utility;

import annotation.qual.ImmediateValue;
import annotation.qual.Value;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 10/01/2017.
 */
@OnThread(Tag.Any)
public final @Value class TaggedValue
{
    private final int tagIndex;
    // The tagIndex is the canonical value, the tagName is for info only:
    private final String tagName;
    private final @Nullable @Value Object innerItem;

    @SuppressWarnings("valuetype")
    public @Value TaggedValue(int tagIndex, @Nullable @Value Object innerItem, TaggedTypeDefinitionBase taggedTypeDefinition)
    {
        this.tagIndex = tagIndex;
        this.innerItem = innerItem;
        this.tagName = taggedTypeDefinition.getTagName(tagIndex);
    }
    
    @SuppressWarnings("valuetype")
    public static @ImmediateValue TaggedValue immediate(int tagIndex, @Nullable @ImmediateValue Object innerItem, TaggedTypeDefinitionBase taggedTypeDefinition)
    {
        return new TaggedValue(tagIndex, innerItem, taggedTypeDefinition);
    }    

    public @Pure int getTagIndex()
    {
        return tagIndex;
    }

    @Pure
    public @Nullable @Value Object getInner()
    {
        return innerItem;
    }

    public @Pure String getTagName()
    {
        return tagName;
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
    
    public static interface TaggedTypeDefinitionBase
    {
        public String getTagName(int tagIndex);
    }
}
