package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.UnitManager;
import records.gui.expressioneditor.UnitEntry;
import records.gui.expressioneditor.UnitEntry.UnitOp;
import records.gui.expressioneditor.UnitSaver;
import records.jellytype.JellyUnit;
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
    public Either<Pair<StyledString, List<UnitExpression>>, JellyUnit> asUnit(UnitManager unitManager)
    {
        Either<Pair<StyledString, List<UnitExpression>>, JellyUnit> lhs = unit.asUnit(unitManager);

        return lhs.map(u -> u.raiseBy(power));
    }

    @Override
    public String save(boolean structured, boolean topLevel)
    {
        return unit.save(structured, false) + "^" + power;
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
    public Stream<SingleLoader<UnitExpression, UnitSaver>> loadAsConsecutive(BracketedStatus bracketedStatus)
    {
        StreamTreeBuilder<SingleLoader<UnitExpression, UnitSaver>> r = new StreamTreeBuilder<>();
        r.addAll(unit.loadAsConsecutive(BracketedStatus.MISC));
        r.add(UnitEntry.load(UnitOp.RAISE));
        r.addAll(new UnitExpressionIntLiteral(power).loadAsConsecutive(BracketedStatus.MISC));
        return r.stream();
    }

    @Override
    public UnitExpression replaceSubExpression(UnitExpression toReplace, UnitExpression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return new UnitRaiseExpression(unit.replaceSubExpression(toReplace, replaceWith), power);
    }
}
