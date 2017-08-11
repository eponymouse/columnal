package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import utility.Either;
import utility.Pair;

import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 18/02/2017.
 */
public abstract class UnfinishedUnitExpression extends UnitExpression
{
    private final String text;

    public UnfinishedUnitExpression(String text)
    {
        this.text = text;
    }


    @Override
    public Either<Pair<String, List<UnitExpression>>, Unit> asUnit(UnitManager unitManager)
    {
        return Either.left(new Pair<>("Unfinished expression", Collections.emptyList()));
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        return o instanceof UnfinishedUnitExpression && text.equals(((UnfinishedUnitExpression)o).text);
    }

    @Override
    public int hashCode()
    {
        return text.hashCode();
    }
}
