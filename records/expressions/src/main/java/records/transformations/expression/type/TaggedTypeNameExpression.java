package records.transformations.expression.type;

import com.google.common.collect.ImmutableList;
import log.Log;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.TableAndColumnRenames;
import records.data.datatype.DataType;
import records.data.datatype.TaggedTypeDefinition;
import records.data.datatype.TypeId;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.TypeEntry;
import records.gui.expressioneditor.TypeEntry.TypeValue;
import records.loadsave.OutputBuilder;
import records.transformations.expression.BracketedStatus;
import styled.StyledString;

import java.util.Objects;
import java.util.stream.Stream;

public class TaggedTypeNameExpression extends TypeExpression
{
    private final TypeId typeName;

    public TaggedTypeNameExpression(TypeId typeName)
    {
        this.typeName = typeName;
    }

    @Override
    public Stream<SingleLoader<TypeExpression, TypeParent>> loadAsConsecutive(BracketedStatus bracketedStatus)
    {
        return Stream.of(p -> new TypeEntry(p, new TypeValue(typeName.getRaw())));
    }

    @Override
    public StyledString toStyledString()
    {
        return StyledString.s(typeName.getRaw());
    }

    @Override
    public String save(TableAndColumnRenames renames)
    {
        return "@tagged " + OutputBuilder.quotedIfNecessary(typeName.getRaw());
    }

    @Override
    public @Nullable DataType toDataType(TypeManager typeManager)
    {
        TaggedTypeDefinition def = typeManager.getKnownTaggedTypes().get(typeName);
        // Shouldn't happen, but if it does, we're no longer valid:
        if (def == null)
            return null;
        if (def.getTypeArguments().isEmpty())
        {
            try
            {
                return def.instantiate(ImmutableList.of(), typeManager);
            }
            catch (InternalException | UserException e)
            {
                // TODO show the user an error
                Log.log(e);
                return null;
            }
        }
        // If needs type arguments then by itself, not a valid type.
        // We rely on the type-application operator to spot us before calling us.
        return null;
    }

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    public TypeId getTypeName()
    {
        return typeName;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaggedTypeNameExpression that = (TaggedTypeNameExpression) o;
        return Objects.equals(typeName, that.typeName);
    }

    @Override
    public int hashCode()
    {

        return Objects.hash(typeName);
    }
}
