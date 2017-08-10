package records.transformations.expression;

import com.google.common.collect.ImmutableList;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import utility.Either;
import utility.Pair;

import java.util.List;

public class UnitTimesExpression extends UnitExpression
{
    private final ImmutableList<UnitExpression> operands;

    public UnitTimesExpression(ImmutableList<UnitExpression> operands)
    {
        this.operands = operands;
    }

    @Override
    public Either<Pair<String, List<UnitExpression>>, Unit> asUnit(UnitManager unitManager)
    {
        Either<Pair<String, List<UnitExpression>>, Unit> r = Either.right(Unit.SCALAR);
        for (UnitExpression operand : operands)
        {
            r = r.flatMap(u -> operand.asUnit(unitManager).map(v -> u.times(v)));
        }
        return r;
    }
}
