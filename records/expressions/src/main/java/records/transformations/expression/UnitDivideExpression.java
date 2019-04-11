package records.transformations.expression;

import annotation.recorded.qual.Recorded;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.UnitManager;
import records.jellytype.JellyUnit;
import styled.StyledString;
import utility.Either;
import utility.Pair;

import java.util.List;

public class UnitDivideExpression extends UnitExpression
{
    private final @Recorded UnitExpression numerator;
    private final @Recorded UnitExpression denominator;

    public UnitDivideExpression(@Recorded UnitExpression numerator, @Recorded UnitExpression denominator)
    {
        this.numerator = numerator;
        this.denominator = denominator;
    }

    @Override
    public Either<Pair<@Nullable StyledString, List<UnitExpression>>, JellyUnit> asUnit(UnitManager unitManager)
    {
        Either<Pair<@Nullable StyledString, List<UnitExpression>>, JellyUnit> num = numerator.asUnit(unitManager);
        Either<Pair<@Nullable StyledString, List<UnitExpression>>, JellyUnit> den = denominator.asUnit(unitManager);

        return num.flatMap(n -> den.map(d -> n.divideBy(d)));
    }

    @Override
    public String save(boolean structured, boolean topLevel)
    {
        String core = numerator.save(structured, false) + "/" + denominator.save(structured, false);
        if (topLevel)
            return core;
        else
            return "(" + core + ")";
    }

    public UnitExpression getNumerator()
    {
        return numerator;
    }

    public UnitExpression getDenominator()
    {
        return denominator;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UnitDivideExpression that = (UnitDivideExpression) o;

        if (!numerator.equals(that.numerator)) return false;
        return denominator.equals(that.denominator);
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
        int result = numerator.hashCode();
        result = 31 * result + denominator.hashCode();
        return result;
    }

    @SuppressWarnings("recorded")
    @Override
    public UnitExpression replaceSubExpression(UnitExpression toReplace, UnitExpression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return new UnitDivideExpression(numerator.replaceSubExpression(toReplace, replaceWith), denominator.replaceSubExpression(toReplace, replaceWith));
    }
}
