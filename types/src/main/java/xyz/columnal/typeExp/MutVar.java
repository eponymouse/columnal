/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.typeExp;

import com.google.common.collect.ImmutableList;
import de.uni_freiburg.informatik.ultimate.smtinterpol.util.IdentityHashSet;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.styled.CommonStyles;
import xyz.columnal.styled.StyledString;
import xyz.columnal.utility.adt.Either;

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
    public Either<TypeError, TypeExp> _unify(TypeExp b) throws InternalException
    {
        // If the other item is a MutVar, we just unify ourselves to them:
        if (this == b)
        {
            return Either.right(this);
        }
        else if (b instanceof MutVar)
        {
            @Nullable TypeError maybeError = b.requireTypeClasses(typeClassesOrPointer.getLeft("Internal variable should be TCR when pruned"));
            if (maybeError != null)
                return Either.left(maybeError);
            typeClassesOrPointer = Either.right(b);
            return Either.right(this);
        }
        else
        {
            // Do an occurs check to prevent cyclic types:
            // Note: no need to prune b, as it is already pruned.
            boolean cycle = b.containsMutVar(this);
            if (cycle)
            {
                return Either.left(new TypeError(StyledString.concat(StyledString.s("Cyclic type while attempting to match "), toStyledString(), StyledString.s(" with "), b.toStyledString()), ImmutableList.of(this, b)));
            }
            else
            {
                // Check that the item we are pointing to is a member of the needed type-classes:
                @Nullable TypeError maybeError = b.requireTypeClasses(typeClassesOrPointer.getLeft("Internal variable should be TCR when pruned"));
                if (maybeError != null)
                    return Either.left(maybeError);
                typeClassesOrPointer = Either.right(b);
                return Either.right(this);
            }
        }
    }

    @Override
    public boolean containsMutVar(MutVar mutVar)
    {
        // It feels like we should recurse into the pointer.
        // But if there is a cycle with
        // another MutVar, we would risk infinite recursion,
        // and Sheard's paper doesn't seem to recurse.
        return this == mutVar;
    }

    @Override
    protected Either<TypeConcretisationError, DataType> _concrete(TypeManager typeManager, boolean substituteDefaultIfPossible) throws InternalException, UserException
    {
        if (substituteDefaultIfPossible)
            return Either.right(typeManager.getVoidType().instantiate(ImmutableList.of(), typeManager));
        
        // Will have been pruned, so error here
        return Either.left(new TypeConcretisationError(StyledString.s("Ambiguous type"), null));
    }

    @Override
    public @Nullable TypeError requireTypeClasses(TypeClassRequirements typeClasses, IdentityHashSet<MutVar> visited)
    {
        if (visited.contains(this))
            return new TypeError(StyledString.s("Cyclic type found"), ImmutableList.of(this));
        
        return typeClassesOrPointer.<@Nullable TypeError>either(t -> {
            typeClassesOrPointer = Either.left(TypeClassRequirements.union(t, typeClasses));
            return null;
        }, p -> {
            IdentityHashSet<MutVar> visitedPlusUs = new IdentityHashSet<>();
            visitedPlusUs.addAll(visited);
            visitedPlusUs.add(this);
            return p.requireTypeClasses(typeClasses, visitedPlusUs);
        });
    }

    @Override
    public TypeExp prune()
    {
        typeClassesOrPointer = typeClassesOrPointer.map(p -> p.prune());
        return typeClassesOrPointer.either(t -> this, p -> p);
    }

    @Override
    public StyledString toStyledString(int maxDepth)
    {
        String name = "_t" + id;
        return typeClassesOrPointer.either(t -> StyledString.styled(name, CommonStyles.ITALIC),
           pointer -> maxDepth <= 0 ? StyledString.s("...") : StyledString.concat(StyledString.s(name + "[="), pointer.toStyledString(maxDepth - 1), StyledString.s("]"))
                    .withStyle(CommonStyles.ITALIC));
    }
}
