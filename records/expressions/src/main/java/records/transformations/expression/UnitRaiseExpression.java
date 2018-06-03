package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.UnitManager;
import records.gui.expressioneditor.UnitEntry;
import records.gui.expressioneditor.UnitNodeParent;
import records.typeExp.units.UnitExp;
import styled.StyledString;
import utility.Either;
import utility.Pair;
import utility.StreamTreeBuilder;

import java.util.List;
import java.util.stream.Stream;

public class UnitRaiseExpression extends UnitExpression
{
    private final UnitExpression unit;
    private final int power;

    public UnitRaiseExpression(UnitExpression unit, int power)
    {
        this.unit = unit;
        this.power = power;
    }

    @Override
    public Either<Pair<StyledString, List<UnitExpression>>, UnitExp> asUnit(UnitManager unitManager)
    {
        Either<Pair<StyledString, List<UnitExpression>>, UnitExp> lhs = unit.asUnit(unitManager);

        return lhs.map(u -> u.raisedTo(power));
    }

    @Override
    public String save(boolean topLevel)
    {
        return unit.save(false) + "^" + power;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UnitRaiseExpression that = (UnitRaiseExpression) o;

        if (power != that.power) return false;
        return unit.equals(that.unit);
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
        int result = unit.hashCode();
        result = 31 * result + power;
        return result;
    }

    @Override
    public Stream<SingleLoader<UnitExpression, UnitNodeParent>> loadAsConsecutive(BracketedStatus bracketedStatus)
    {
        StreamTreeBuilder<SingleLoader<UnitExpression, UnitNodeParent>> r = new StreamTreeBuilder<>();
        r.addAll(unit.loadAsConsecutive(BracketedStatus.MISC));
        r.add(UnitEntry.load(UnitOp.RAISE));
        r.addAll(new UnitExpressionIntLiteral(power).loadAsConsecutive(BracketedStatus.MISC));
        return r.stream();
    }
}
