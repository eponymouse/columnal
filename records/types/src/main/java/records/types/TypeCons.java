package records.types;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.DateTimeInfo.DateTimeType;
import records.data.datatype.TypeManager;
import records.error.InternalException;
import records.error.UserException;
import styled.StyledString;
import utility.Either;
import utility.Utility;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class TypeCons extends TypeExp
{
    public final String name;
    // Can be size 0+:
    public final ImmutableList<TypeExp> operands;
    
    // If operands is empty, this is the actual set of type-classes.  If operands is non-empty,
    // this is the list of type-classes which can be derived as long as all of the inner types
    // satisfy them (bit of a hack, but it will do for now)
    private final ImmutableSet<String> typeClasses;

    public TypeCons(@Nullable ExpressionBase src, String name, ImmutableSet<String> typeClasses)
    {
        super(src);
        this.name = name;
        this.operands = ImmutableList.of();
        this.typeClasses = typeClasses;
    }
    
    public TypeCons(@Nullable ExpressionBase src, String name, ImmutableList<TypeExp> operands, ImmutableSet<String> derivableTypeClasses)
    {
        super(src);
        this.name = name;
        this.operands = operands;
        this.typeClasses = derivableTypeClasses;
    }

    @Override
    public Either<StyledString, TypeExp> _unify(TypeExp b) throws InternalException
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

        ImmutableList.Builder<TypeExp> unifiedOperands = ImmutableList.builder();
        for (int i = 0; i < operands.size(); i++)
        {
            Either<StyledString, TypeExp> sub = operands.get(i).unifyWith(bt.operands.get(i));
            if (sub.isLeft())
                return sub;
            unifiedOperands.add(sub.getRight());
        }
        return Either.right(new TypeCons(src != null ? src : b.src, name, unifiedOperands.build(), ImmutableSet.copyOf(Sets.intersection(typeClasses, ((TypeCons) b).typeClasses))));
    }

    @Override
    public @Nullable TypeExp withoutMutVar(MutVar mutVar)
    {
        ImmutableList.Builder<TypeExp> without = ImmutableList.builder();
        for (TypeExp operand : operands)
        {
            TypeExp t = operand.withoutMutVar(mutVar);
            if (t == null)
                return null;
            without.add(t);
        }
        return new TypeCons(src, name, without.build(), typeClasses);
    }

    @Override
    protected Either<TypeConcretisationError, DataType> _concrete(TypeManager typeManager) throws InternalException, UserException
    {
        switch (name)
        {
            case CONS_TEXT:
                return Either.right(DataType.TEXT);
            case CONS_BOOLEAN:
                return Either.right(DataType.BOOLEAN);
            case CONS_LIST:
                return operands.get(0).toConcreteType(typeManager).map(t -> DataType.array(t));
            default:
                try
                {
                    return Either.right(DataType.date(new DateTimeInfo(DateTimeType.valueOf(name))));
                }
                catch (IllegalArgumentException e)
                {
                    // Not a date type, continue...
                }
                Either<TypeConcretisationError, List<DataType>> errOrOperandsAsTypes = Either.mapMEx(operands, o -> o.toConcreteType(typeManager));
                return errOrOperandsAsTypes.eitherEx(err -> Either.left(new TypeConcretisationError(err.getErrorText(), null)), (List<DataType> operandsAsTypes) -> {
                    @Nullable DataType tagged = typeManager.lookupType(name, ImmutableList.copyOf(operandsAsTypes));
                    if (tagged != null)
                    {
                        return Either.right(tagged);
                    }
                    return Either.left(new TypeConcretisationError(StyledString.s("Unknown type constructor: " + name)));
                });
                
        }
    }

    @Override
    public @Nullable StyledString requireTypeClasses(TypeClassRequirements typeClasses)
    {
        if (operands.isEmpty())
        {
            return typeClasses.checkIfSatisfiedBy(StyledString.s(name), this.typeClasses);
            
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
            @Nullable StyledString derivationError = typeClasses.checkIfSatisfiedBy(toStyledString(), this.typeClasses);
            if (derivationError == null)
            {
                // Apply constraints to children so that they know them
                // for future unification:
                for (TypeExp operand : operands)
                {
                    @Nullable StyledString err = operand.requireTypeClasses(typeClasses);
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
    public StyledString toStyledString()
    {
        return StyledString.concat(StyledString.s(name), operands.isEmpty() ? StyledString.s("") : StyledString.concat(operands.stream().map(t -> StyledString.concat(StyledString.s(" "), t.toStyledString())).toArray(StyledString[]::new)));
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
