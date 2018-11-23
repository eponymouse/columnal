package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.UnitManager;
import records.gui.expressioneditor.UnitEntry;
import records.gui.expressioneditor.UnitEntry.UnitBracket;
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
    public Either<Pair<StyledString, List<UnitExpression>>, JellyUnit> asUnit(UnitManager unitManager)
    {
        Either<Pair<StyledString, List<UnitExpression>>, JellyUnit> num = numerator.asUnit(unitManager);
        Either<Pair<StyledString, List<UnitExpression>>, JellyUnit> den = denominator.asUnit(unitManager);

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

    @Override
    public Stream<SingleLoader<UnitExpression, UnitSaver>> loadAsConsecutive(BracketedStatus bracketedStatus)
    {
        StreamTreeBuilder<SingleLoader<UnitExpression, UnitSaver>> r = new StreamTreeBuilder<>();
        boolean needsBrackets = bracketedStatus != BracketedStatus.DIRECT_ROUND_BRACKETED && bracketedStatus != BracketedStatus.TOP_LEVEL;
        if (needsBrackets)
            r.add(UnitEntry.load(UnitBracket.OPEN_ROUND.getContent()));
        r.addAll(numerator.loadAsConsecutive(BracketedStatus.MISC));
        r.add(UnitEntry.load(UnitOp.DIVIDE));
        r.addAll(denominator.loadAsConsecutive(BracketedStatus.MISC));
        if (needsBrackets)
            r.add(UnitEntry.load(UnitBracket.CLOSE_ROUND.getContent()));
        return r.stream();
    }

    @Override
    public UnitExpression replaceSubExpression(UnitExpression toReplace, UnitExpression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return new UnitDivideExpression(numerator.replaceSubExpression(toReplace, replaceWith), denominator.replaceSubExpression(toReplace, replaceWith));
    }
}
