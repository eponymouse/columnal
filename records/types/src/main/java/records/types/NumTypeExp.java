package records.types;

import records.data.datatype.DataType;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeManager;
import records.data.unit.Unit;
import records.error.InternalException;
import utility.Either;

public class NumTypeExp extends TypeExp
{
    public final Unit unit;

    public NumTypeExp(ExpressionBase src, Unit unit)
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
        
        if (!unit.equals(bn.unit))
            return typeMismatch(b);
        
        // We are both the same, so just return us:
        return Either.right(this);
    }


    @Override
    protected Either<String, DataType> _concrete(TypeManager typeManager)
    {
        return Either.right(DataType.number(new NumberInfo(unit, null)));
    }
}
