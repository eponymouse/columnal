package records.types;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.InternalException;
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
    //package-visible:
    // One element array to implement mutable reference:
    final @Nullable TypeExp pointer[];

    public MutVar(@Nullable TypeExp pointTo)
    {
        this.pointer = new TypeExp[] {pointTo};
    }

    @Override
    public Either<String, TypeExp> _unify(TypeExp b) throws InternalException
    {
        // If the other item is a MutVar, we just unify ourselves to them:
        if (b instanceof MutVar)
        {
            pointer[0] = b;
            return Either.right(this);
        }
        else
        {
            // Do an occurs check to prevent cyclic types:
            // Note: no need to prune b, as it is already pruned.
            @Nullable TypeExp without = b.withoutMutVar(this);
            if (without == null)
            {
                return Either.left("Cyclic type while attempting to match " + toString() + " with " + b.toString());
            }
            else
            {
                pointer[0] = without;
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
    public TypeExp prune()
    {
        if (pointer[0] != null)
        {
            pointer[0] = pointer[0].prune();
            return pointer[0];
        }
        return this;
    }
}
