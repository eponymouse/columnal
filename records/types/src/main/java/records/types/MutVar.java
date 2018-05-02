package records.types;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import styled.CommonStyles;
import styled.StyledString;
import styled.StyledString.Style;
import utility.Either;

import java.util.HashSet;
import java.util.Set;

/**
 * Named MutVar after the Sheard paper.  Essentially, any time there
 * is an item of uncertain type in the expression tree, e.g.
 * return of an overloaded function/operator, it gets a MutVar
 * assigned as a placeholder, which is free to unify with any one thing.
 * It usually is the glue between some harder constraints, e.g.
 * in the expression a * (b + c), The type of b will be a MutVar,
 * but it will need to unify with number to satisfy the outer multiply,
 * and number or string to satisfy the inner plus.
 */
public class MutVar extends TypeExp
{
    // For Comparable and printing purposes
    private static long nextId = 0;
    private final long id = nextId++;
    
    //package-visible, mutable reference:
    
    // If we point to something, all type-class information is in the pointer destination.
    // If we don't point to something, we have the type-class info:
    Either<TypeClassRequirements, TypeExp> typeClassesOrPointer;

    public MutVar(@Nullable ExpressionBase src, TypeClassRequirements typeClasses)
    {
        super(src);
        this.typeClassesOrPointer = Either.left(typeClasses);
    }

    public MutVar(@Nullable ExpressionBase src)
    {
        this(src, TypeClassRequirements.empty());
    }

    /**
     * You can assume that this is pruned, b is pruned, and b is not a MutVar unless
     * this is also a MutVar.
     */
    @Override
    public Either<StyledString, TypeExp> _unify(TypeExp b) throws InternalException
    {
        // If the other item is a MutVar, we just unify ourselves to them:
        if (this == b)
        {
            return Either.right(this);
        }
        else if (b instanceof MutVar)
        {
            @Nullable StyledString maybeError = b.requireTypeClasses(typeClassesOrPointer.getLeft());
            if (maybeError != null)
                return Either.left(maybeError);
            typeClassesOrPointer = Either.right(b);
            return Either.right(this);
        }
        else
        {
            // Do an occurs check to prevent cyclic types:
            // Note: no need to prune b, as it is already pruned.
            @Nullable TypeExp without = b.withoutMutVar(this);
            if (without == null)
            {
                return Either.left(StyledString.concat(StyledString.s("Cyclic type while attempting to match "), toStyledString(), StyledString.s(" with "), b.toStyledString()));
            }
            else
            {
                // Check that the item we are pointing to is a member of the needed type-classes:
                @Nullable StyledString maybeError = without.requireTypeClasses(typeClassesOrPointer.getLeft());
                if (maybeError != null)
                    return Either.left(maybeError);
                typeClassesOrPointer = Either.right(without);
                return Either.right(this);
            }
        }
    }

    @Override
    public @Nullable TypeExp withoutMutVar(MutVar mutVar)
    {
        return this == mutVar ? null : this;
    }

    @Override
    protected Either<TypeConcretisationError, DataType> _concrete(TypeManager typeManager)
    {
        // Will have been pruned, so error here
        return Either.left(new TypeConcretisationError(StyledString.s("Error: cannot determine type (free variable remaining)"), null));
    }

    @Override
    public @Nullable StyledString requireTypeClasses(TypeClassRequirements typeClasses)
    {
        return typeClassesOrPointer.<@Nullable StyledString>either(t -> {
            typeClassesOrPointer = Either.left(TypeClassRequirements.union(t, typeClasses));
            return null;
        }, p -> p.requireTypeClasses(typeClasses));
    }

    @Override
    public TypeExp prune()
    {
        typeClassesOrPointer = typeClassesOrPointer.either(t -> Either.left(t), p -> Either.right(p.prune()));
        return typeClassesOrPointer.either(t -> this, p -> p);
    }

    @Override
    public StyledString toStyledString()
    {
        String name = "_t" + id;
        return typeClassesOrPointer.either(t -> StyledString.styled(name, CommonStyles.ITALIC),
           pointer -> StyledString.concat(StyledString.s(name + "[="), pointer.toStyledString(), StyledString.s("]"))
                    .withStyle(CommonStyles.ITALIC));
    }
}
