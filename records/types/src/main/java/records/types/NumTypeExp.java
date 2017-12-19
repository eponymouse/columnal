package records.types;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import records.types.units.UnitExp;
import utility.Either;

public class NumTypeExp extends TypeExp
{
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
    public Either<String, TypeExp> _unify(TypeExp b) throws InternalException
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
    protected Either<String, DataType> _concrete(TypeManager typeManager)
    {
        @Nullable Unit concreteUnit = this.unit.toConcreteUnit();
        if (concreteUnit == null)
            return Either.left("Ambiguous unit");
        else
            return Either.right(DataType.number(new NumberInfo(concreteUnit, null)));
    }
}
