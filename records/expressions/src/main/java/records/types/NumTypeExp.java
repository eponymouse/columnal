package records.types;

import records.data.unit.Unit;
import records.error.InternalException;
import utility.Either;

public class NumTypeExp extends TypeExp
{
    public final Unit unit;

    public NumTypeExp(Unit unit)
    {
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
}
