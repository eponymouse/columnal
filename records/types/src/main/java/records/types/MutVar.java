package records.types;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import styled.StyledString;
import styled.StyledString.Style;
import utility.Either;

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
    
    //package-visible:
    // mutable reference:
    @Nullable TypeExp pointer;

    public MutVar(@Nullable ExpressionBase src)
    {
        super(src);
        this.pointer = null;
    }

    @Override
    public Either<StyledString, TypeExp> _unify(TypeExp b) throws InternalException
    {
        // If the other item is a MutVar, we just unify ourselves to them:
        if (b instanceof MutVar)
        {
            pointer = b;
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
                pointer = without;
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
    public TypeExp prune()
    {
        if (pointer != null)
        {
            pointer = pointer.prune();
            return pointer;
        }
        return this;
    }

    @Override
    public StyledString toStyledString()
    {
        String name = "_t" + id;
        if (pointer == null)
            return StyledString.styled(name, Style.italic());
        else
            return StyledString.italicise(StyledString.concat(StyledString.s(name + "[="), pointer.toStyledString(), StyledString.s("]")));
    }
}
