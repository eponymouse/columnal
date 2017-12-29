package records.data.datatype;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType.TagType;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.FormatLexer;
import records.loadsave.OutputBuilder;
import utility.Utility;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class TaggedTypeDefinition
{
    private final TypeId name;
    // Order matters here:
    private final ImmutableList<String> typeVariables;
    // Version with the type variables unsubstituted:
    // The only free variables should be the ones in the typeVariables list:
    private final ImmutableList<TagType<DataType>> tags;

    public TaggedTypeDefinition(TypeId name, ImmutableList<String> typeVariables, ImmutableList<TagType<DataType>> tags)
    {
        this.name = name;
        this.typeVariables = typeVariables;
        this.tags = tags;
    }

    public ImmutableList<TagType<DataType>> getTags()
    {
        return tags;
    }

    public DataType instantiate(ImmutableList<DataType> typeVariableSubs) throws UserException, InternalException
    {
        if (typeVariableSubs.size() != typeVariables.size())
            throw new UserException("Attempting to use type with " + typeVariables.size() + " variables but trying to substitute " + typeVariableSubs.size());
        
        Map<String, DataType> substitutions = new HashMap<>();

        for (int i = 0; i < typeVariables.size(); i++)
        {
            substitutions.put(typeVariables.get(i), typeVariableSubs.get(i));
        }

        ImmutableList<TagType<DataType>> substitutedTags = Utility.mapListExI(tags, tag -> {
            if (tag.getInner() == null)
                return tag;
            @NonNull DataType inner = tag.getInner();
            return new TagType<>(tag.getName(), inner.substitute(substitutions));
        });
        
        return DataType.tagged(name, typeVariableSubs, substitutedTags);
    }

    public TypeId getTaggedTypeName()
    {
        return name;
    }

    public void save(OutputBuilder b) throws InternalException
    {
        b.t(FormatLexer.TAGGED, FormatLexer.VOCABULARY);
        // TODO save type variables
        b.t(FormatLexer.OPEN_BRACKET, FormatLexer.VOCABULARY);
        boolean first = true;
        for (TagType<DataType> tag : tags)
        {
            if (!first)
                b.t(FormatLexer.TAGOR, FormatLexer.VOCABULARY);
            b.kw(b.quotedIfNecessary(tag.getName()) + (tag.getInner() != null ? ":" : ""));
            if (tag.getInner() != null)
                tag.getInner().save(b, false);
            first = false;
        }
        b.t(FormatLexer.CLOSE_BRACKET, FormatLexer.VOCABULARY);
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaggedTypeDefinition that = (TaggedTypeDefinition) o;
        return Objects.equals(name, that.name) &&
            Objects.equals(typeVariables, that.typeVariables) &&
            Objects.equals(tags, that.tags);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(name, typeVariables, tags);
    }

    public ImmutableList<String> getTypeArguments()
    {
        return typeVariables;
    }
}
