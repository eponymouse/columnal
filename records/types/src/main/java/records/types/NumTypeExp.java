package records.types;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.types.units.UnitExp;
import styled.StyledString;
import utility.Either;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class NumTypeExp extends TypeExp
{
    private static final ImmutableSet<String> NATURAL_TYPE_CLASSES = ImmutableSet.of(
        "Equatable", "Comparable"
    );
    
    public final UnitExp unit;

    public NumTypeExp(@Nullable ExpressionBase src, UnitExp unit)
    {
        super(src);
        this.unit = unit;
    }

    @Override
    public TypeExp withoutMutVar(MutVar mutVar)
    {
        return this;
    }

    @Override
    public Either<StyledString, TypeExp> _unify(TypeExp b) throws InternalException
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
    protected Either<TypeConcretisationError, DataType> _concrete(TypeManager typeManager)
    {
        @Nullable Unit concreteUnit = this.unit.toConcreteUnit();
        if (concreteUnit == null)
            return Either.left(new TypeConcretisationError(StyledString.concat(StyledString.s("Ambiguous unit: "), unit.toStyledString()), DataType.NUMBER));
        else
            return Either.right(DataType.number(new NumberInfo(concreteUnit)));
    }

    @Override
    public @Nullable StyledString requireTypeClasses(TypeClassRequirements typeClasses)
    {
        return typeClasses.checkIfSatisfiedBy(StyledString.s("Number"), NATURAL_TYPE_CLASSES);
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
    public StyledString toStyledString()
    {
        return StyledString.concat(StyledString.s("Number{"), unit.toStyledString(), StyledString.s("}"));
    }
}
