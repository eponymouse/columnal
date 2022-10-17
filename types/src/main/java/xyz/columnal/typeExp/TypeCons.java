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

import annotation.identifier.qual.ExpressionIdentifier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import de.uni_freiburg.informatik.ultimate.smtinterpol.util.IdentityHashSet;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.DateTimeInfo;
import xyz.columnal.data.datatype.DataType.DateTimeInfo.DateTimeType;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.typeExp.units.UnitExp;
import xyz.columnal.styled.StyledString;
import xyz.columnal.utility.adt.Either;

import java.util.List;
import java.util.Objects;

public class TypeCons extends TypeExp
{
    public final @ExpressionIdentifier String name;
    // Can be size 0+:
    public final ImmutableList<Either<UnitExp, TypeExp>> operands;
    
    // If operands is empty, this is the actual set of type-classes.  If operands is non-empty,
    // this is the list of type-classes which can be derived as long as all of the inner types
    // satisfy them (bit of a hack, but it will do for now)
    private final ImmutableSet<String> typeClasses;

    // For primitive types
    public TypeCons(@Nullable ExpressionBase src, @ExpressionIdentifier String name, ImmutableSet<String> typeClasses)
    {
        super(src);
        this.name = name;
        this.operands = ImmutableList.of();
        this.typeClasses = typeClasses;
    }
    
    // For tagged types
    public TypeCons(@Nullable ExpressionBase src, @ExpressionIdentifier String name, ImmutableList<Either<UnitExp, TypeExp>> operands, ImmutableSet<String> derivableTypeClasses)
    {
        super(src);
        this.name = name;
        this.operands = operands;
        this.typeClasses = derivableTypeClasses;
    }

    @Override
    public Either<TypeError, TypeExp> _unify(TypeExp b) throws InternalException
    {
        if (!(b instanceof TypeCons))
            return typeMismatch(b);
        
        TypeCons bt = (TypeCons) b;
        if (!name.equals(bt.name))
            return typeMismatch(b);
        
        // This probably shouldn't happen in our editor, as it suggests
        // an incoherent expression:
        if (operands.size() != bt.operands.size())
            return typeMismatch(b);

        ImmutableList.Builder<Either<UnitExp, TypeExp>> unifiedOperands = ImmutableList.builder();
        for (int i = 0; i < operands.size(); i++)
        {
            Either<UnitExp, TypeExp> us = operands.get(i);
            Either<UnitExp, TypeExp> them = bt.operands.get(i);
            
            if (us.isLeft() && them.isLeft())
            {
                @Nullable UnitExp unified = us.getLeft("Impossible").unifyWith(them.getLeft("Impossible"));
                if (unified == null)
                    return Either.left(new TypeError(StyledString.s("Cannot match units " + us.getLeft("Impossible") + " with " + them.getLeft("Impossible")), ImmutableList.of(this, b)));
                unifiedOperands.add(Either.left(unified));
            }
            else if (us.isRight() && them.isRight())
            {
                Either<TypeError, TypeExp> sub = us.getRight("Impossible").unifyWith(them.getRight("Impossible"));
                if (sub.isLeft())
                    return sub;
                unifiedOperands.add(Either.right(sub.getRight("Impossible")));
            }
            else
            {
                return Either.left(new TypeError(StyledString.s("Cannot match units with a type"), ImmutableList.of(this, b)));
            }
        }
        return Either.right(new TypeCons(src != null ? src : b.src, name, unifiedOperands.build(), ImmutableSet.copyOf(Sets.<String>intersection(typeClasses, ((TypeCons) b).typeClasses))));
    }

    @Override
    public boolean containsMutVar(MutVar mutVar)
    {
        return operands.stream().anyMatch(operand -> operand.either(u -> false, t -> t.containsMutVar(mutVar)));
    }

    @Override
    protected Either<TypeConcretisationError, DataType> _concrete(TypeManager typeManager, boolean substituteDefaultIfPossible) throws InternalException, UserException
    {
        switch (name)
        {
            case CONS_TEXT:
                return Either.right(DataType.TEXT);
            case CONS_BOOLEAN:
                return Either.right(DataType.BOOLEAN);
            case CONS_LIST:
                if (operands.get(0).isLeft())
                    throw new UserException("List must be of a type, not a unit");
                else
                    return operands.get(0).getRight("Impossible").toConcreteType(typeManager, substituteDefaultIfPossible).map(t -> DataType.array(t));
            case CONS_FUNCTION:
                if (operands.stream().anyMatch(Either::isLeft))
                    throw new UserException("Function cannot take or return a unit");
                Either<TypeConcretisationError, ImmutableList<DataType>> arg = Either.<TypeConcretisationError, DataType, Either<UnitExp, TypeExp>>mapMEx(operands.subList(0, operands.size() - 1), op -> op.getRight("Impossible").toConcreteType(typeManager, substituteDefaultIfPossible));
                if (arg.isLeft())
                    return Either.left(arg.getLeft("Impossible"));
                Either<TypeConcretisationError, DataType> ret = operands.get(operands.size() - 1).getRight("Impossible").toConcreteType(typeManager, substituteDefaultIfPossible);
                if (ret.isLeft())
                    return ret;
                return Either.right(DataType.function(arg.getRight("Impossible"), ret.getRight("Impossible")));
            default:
                for (DateTimeType dtt : DateTimeType.values())
                {
                    DataType dataType = DataType.date(new DateTimeInfo(dtt));
                    if (dataType.toString().equals(name))
                        return Either.right(dataType);
                }
                // Not a date type, continue...
                Either<TypeConcretisationError, ImmutableList<Either<Unit, DataType>>> errOrOperandsAsTypes = Either.<TypeConcretisationError, Either<Unit, DataType>, Either<UnitExp, TypeExp>>mapMEx(operands, o -> {
                    // So, the outer either here is for units versus types, but the return type is either error or either-unit-or-type.
                    return o.eitherEx(u -> {
                        Unit concreteUnit = u.toConcreteUnit();
                        if (concreteUnit == null)
                            return Either.<TypeConcretisationError, Either<Unit, DataType>>left(new TypeConcretisationError(StyledString.s("Unit unspecified; could be any unit: " + u)));
                        Either<Unit, DataType> unitOrType = Either.left(concreteUnit);
                        return Either.right(unitOrType);
                    }, t -> t.toConcreteType(typeManager, substituteDefaultIfPossible).map(x -> Either.<Unit, DataType>right(x)));
                });
                return errOrOperandsAsTypes.eitherEx(err -> Either.<TypeConcretisationError, DataType>left(new TypeConcretisationError(err.getErrorText(), null)), (List<Either<Unit, DataType>> operandsAsTypes) -> {
                    @Nullable DataType tagged = typeManager.lookupType(new TypeId(name), ImmutableList.copyOf(operandsAsTypes));
                    if (tagged != null)
                    {
                        return Either.right(tagged);
                    }
                    return Either.left(new TypeConcretisationError(StyledString.s("Unknown type constructor: " + name)));
                });
                
        }
    }

    @Override
    public @Nullable TypeError requireTypeClasses(TypeClassRequirements typeClasses, IdentityHashSet<MutVar> visited)
    {
        if (operands.isEmpty())
        {
            return typeClasses.checkIfSatisfiedBy(toStyledString(), this.typeClasses, this);
            
            /*
                StyledString.Builder b = StyledString.builder();
                b.append("Type: ");
                b.append(name).append(operands.stream().map(s -> StyledString.concat(StyledString.s("-"), s.toStyledString())).collect(StyledString.joining("")));
                b.append(" is not " + Sets.difference(typeClasses, this.typeClasses).stream().collect(Collectors.joining(" or ")));
                return b.build();
                */
        }
        else
        {
            // Apply all type constraints to children.
            // First check that everything they want can be derived:
            @Nullable TypeError derivationError = typeClasses.checkIfSatisfiedBy(toStyledString(), this.typeClasses, this);
            if (derivationError == null)
            {
                // Apply constraints to children so that they know them
                // for future unification:
                for (Either<UnitExp, TypeExp> operand : operands)
                {
                    @Nullable TypeError err = operand.<@Nullable TypeError>either(u -> null, t -> t.requireTypeClasses(typeClasses, visited));
                    if (err != null)
                        return err;
                }
                return null;
            }
            else
            {
                return derivationError;
            }
        }
    }

    @Override
    public StyledString toStyledString(int maxDepth)
    {
        return StyledString.concat(StyledString.s(name), operands.isEmpty() ? StyledString.s("") : StyledString.concat(operands.stream().map(t -> StyledString.concat(StyledString.s("("), t.either(UnitExp::toStyledString, ty -> ty.toStyledString(maxDepth)), StyledString.s(")"))).toArray(StyledString[]::new)));
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TypeCons typeCons = (TypeCons) o;
        return Objects.equals(name, typeCons.name) &&
            Objects.equals(operands, typeCons.operands);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(name, operands);
    }
}
