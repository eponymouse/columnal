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

import com.google.common.collect.ImmutableSet;
import de.uni_freiburg.informatik.ultimate.smtinterpol.util.IdentityHashSet;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.typeExp.units.UnitExp;
import xyz.columnal.styled.StyledString;
import xyz.columnal.utility.adt.Either;

import java.util.Objects;

// Note: this is separate to TypeCons because (a) unit is optional, so special treatment needed, and
// (b) the unit is treated specially in multiplication and division.
public class NumTypeExp extends TypeExp
{
    private static final ImmutableSet<String> NATURAL_TYPE_CLASSES = ImmutableSet.of(
        "Equatable", "Comparable", "Readable", "Showable"
    );
    
    public final UnitExp unit;

    public NumTypeExp(@Nullable ExpressionBase src, UnitExp unit)
    {
        super(src);
        this.unit = unit;
    }

    @Override
    public boolean containsMutVar(MutVar mutVar)
    {
        return false;
    }

    @Override
    public Either<TypeError, TypeExp> _unify(TypeExp b) throws InternalException
    {
        if (!(b instanceof NumTypeExp))
            return typeMismatch(b);

        NumTypeExp bn = (NumTypeExp) b;
        
        UnitExp unifiedUnit = unit.unifyWith(bn.unit);
        if (unifiedUnit == null)
            return typeMismatch(b);
        
        return Either.right(new NumTypeExp(src != null ? src : b.src, unifiedUnit));
    }


    @Override
    protected Either<TypeConcretisationError, DataType> _concrete(TypeManager typeManager, boolean substituteDefaultIfPossible)
    {
        @Nullable Unit concreteUnit = this.unit.toConcreteUnit();
        if (concreteUnit == null)
        {
            if (this.unit.isOnlyVars() || substituteDefaultIfPossible)
                return Either.right(DataType.number(new NumberInfo(Unit.SCALAR)));
            else
                return Either.left(new TypeConcretisationError(StyledString.concat(StyledString.s("Ambiguous unit: "), unit.toStyledString()), DataType.NUMBER));
        }
        else
            return Either.right(DataType.number(new NumberInfo(concreteUnit)));
    }

    @Override
    public @Nullable TypeError requireTypeClasses(TypeClassRequirements typeClasses, IdentityHashSet<MutVar> visited)
    {
        return typeClasses.checkIfSatisfiedBy(StyledString.s("Number"), NATURAL_TYPE_CLASSES, this);
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NumTypeExp that = (NumTypeExp) o;
        return Objects.equals(unit, that.unit);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(unit);
    }

    @Override
    public StyledString toStyledString(int maxDepth)
    {
        return StyledString.concat(StyledString.s("Number{"), unit.toStyledString(), StyledString.s("}"));
    }
}
