package records.transformations.expression;

import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import utility.Either;
import utility.Pair;

import java.util.Collections;
import java.util.List;

public class SingleUnitExpression extends UnitExpression
{
    private final String name;
    private final int power;

    public SingleUnitExpression(String text, int power)
    {
        this.name = text;
        this.power = power;
    }

    @Override
    public Either<Pair<String, List<UnitExpression>>, Unit> asUnit(UnitManager unitManager)
    {
        try
        {
            return Either.right(unitManager.loadUse(name).raisedTo(power));
        }
        catch (InternalException | UserException e)
        {
            // TODO add similarly spelt unit names:
            return Either.left(new Pair<>(e.getLocalizedMessage(), Collections.emptyList()));
        }
    }
}
