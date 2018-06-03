package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.UnitEntry;
import records.gui.expressioneditor.UnitNodeParent;
import records.typeExp.units.UnitExp;
import styled.StyledString;
import utility.Either;
import utility.Pair;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class SingleUnitExpression extends UnitExpression
{
    private final String name;

    public SingleUnitExpression(String text)
    {
        this.name = text;
    }

    @Override
    public Either<Pair<StyledString, List<UnitExpression>>, UnitExp> asUnit(UnitManager unitManager)
    {
        try
        {
            return Either.right(UnitExp.fromConcrete(unitManager.loadUse(name)));
        }
        catch (InternalException | UserException e)
        {
            // TODO add similarly spelt unit names:
            return Either.left(new Pair<>(StyledString.s(e.getLocalizedMessage()), Collections.emptyList()));
        }
    }

    @Override
    public String save(boolean topLevel)
    {
        return name;
    }

    @Override
    public Stream<SingleLoader<UnitExpression, UnitNodeParent>> loadAsConsecutive(BracketedStatus bracketedStatus)
    {
        return Stream.of(p -> new UnitEntry(p, name, false));
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SingleUnitExpression that = (SingleUnitExpression) o;

        return name.equals(that.name);
    }

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    @Override
    public boolean isScalar()
    {
        return false;
    }

    @Override
    public int hashCode()
    {
        return name.hashCode();
    }
}
