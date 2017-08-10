package records.transformations.expression;

import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import utility.Either;
import utility.Pair;

import java.util.List;

public class UnitDivideExpression extends UnitExpression
{
    private final UnitExpression numerator;
    private final UnitExpression denominator;

    public UnitDivideExpression(UnitExpression numerator, UnitExpression denominator)
    {
        this.numerator = numerator;
        this.denominator = denominator;
    }

    @Override
    public Either<Pair<String, List<UnitExpression>>, Unit> asUnit(UnitManager unitManager)
    {
        Either<Pair<String, List<UnitExpression>>, Unit> num = numerator.asUnit(unitManager);
        Either<Pair<String, List<UnitExpression>>, Unit> den = denominator.asUnit(unitManager);

        return num.flatMap(n -> den.map(d -> n.divide(d)));
    }
}
