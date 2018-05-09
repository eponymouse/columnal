package records.data.datatype;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType.TagType;
import records.data.unit.Unit;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.FormatLexer;
import records.loadsave.OutputBuilder;
import utility.Either;
import utility.Pair;
import utility.Utility;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;

public class TaggedTypeDefinition
{
    public static enum TypeVariableKind { TYPE, UNIT }
    
    private final TypeId name;
    // Order matters here:
    private final ImmutableList<Pair<TypeVariableKind, String>> typeVariables;
    // Version with the type variables unsubstituted:
    // The only free variables should be the ones in the typeVariables list:
    private final ImmutableList<TagType<DataType>> tags;
    
    public TaggedTypeDefinition(TypeId name, ImmutableList<Pair<TypeVariableKind, String>> typeVariables, ImmutableList<TagType<DataType>> tags) throws InternalException
    {
        this.name = name;
        this.typeVariables = typeVariables;
        this.tags = tags;
        // Caller is responsible for checking name, type variables and tags.  Hence any violations here are InternalException, not UserException
        HashSet<String> uniqueTypeVars = new HashSet<>();
        for (Pair<TypeVariableKind, String> typeVariable : typeVariables)
        {
            if (!uniqueTypeVars.add(typeVariable.getSecond()))
            {
                throw new InternalException("Duplicate type variable: \"" + typeVariable + "\"");
            }
        }
        HashSet<String> uniqueTagNames = new HashSet<>();
        for (TagType<DataType> tag : tags)
        {
            if (!uniqueTagNames.add(tag.getName()))
            {
                throw new InternalException("Duplicate type tag: \"" + tag.getName() + "\"");
            }
        }
    }

    public ImmutableList<TagType<DataType>> getTags()
    {
        return tags;
    }

    public DataType instantiate(ImmutableList<Either<Unit, DataType>> typeVariableSubs) throws UserException, InternalException
    {
        if (typeVariableSubs.size() != typeVariables.size())
            throw new UserException("Attempting to use type with " + typeVariables.size() + " variables but trying to substitute " + typeVariableSubs.size());
        
        Map<String, Either<Unit, DataType>> substitutions = new HashMap<>();

        for (int i = 0; i < typeVariables.size(); i++)
        {
            if ((typeVariables.get(i).getFirst() == TypeVariableKind.TYPE) && typeVariableSubs.get(i).isLeft())
                throw new UserException("Expected type variable but found unit variable for variable #" + (i + 1));
            if ((typeVariables.get(i).getFirst() == TypeVariableKind.UNIT) && typeVariableSubs.get(i).isRight())
                throw new UserException("Expected unit variable but found type variable for variable #" + (i + 1));
            
            substitutions.put(typeVariables.get(i).getSecond(), typeVariableSubs.get(i));
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
        for (Pair<TypeVariableKind, String> typeVariable : typeVariables)
        {
            b.t(typeVariable.getFirst() == TypeVariableKind.TYPE ? FormatLexer.TYPEVAR : FormatLexer.UNITVAR, FormatLexer.VOCABULARY)
                .raw(b.quotedIfNecessary(typeVariable.getSecond()));
        }
        b.t(FormatLexer.OPEN_BRACKET, FormatLexer.VOCABULARY);
        boolean first = true;
        for (TagType<DataType> tag : tags)
        {
            if (!first)
                b.t(FormatLexer.TAGOR, FormatLexer.VOCABULARY);
            b.kw(b.quotedIfNecessary(tag.getName()));
            @Nullable DataType inner = tag.getInner();
            if (inner != null)
            {
                b.raw("(");
                inner.save(b);
                b.raw(")");
            }
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

    public ImmutableList<Pair<TypeVariableKind, String>> getTypeArguments()
    {
        return typeVariables;
    }

    @Override
    public String toString()
    {
        return name + " " + Utility.listToString(typeVariables) + " " + Utility.listToString(tags);
    }
}
