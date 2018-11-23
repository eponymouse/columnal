package records.transformations.expression;

import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.Unit;
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
import utility.Utility;

import java.util.List;
import java.util.stream.Stream;

public class UnitTimesExpression extends UnitExpression
{
    private final ImmutableList<@Recorded UnitExpression> operands;

    public UnitTimesExpression(ImmutableList<@Recorded UnitExpression> operands)
    {
        this.operands = operands;
    }

    @Override
    public Either<Pair<StyledString, List<UnitExpression>>, JellyUnit> asUnit(UnitManager unitManager)
    {
        Either<Pair<StyledString, List<UnitExpression>>, JellyUnit> r = Either.right(JellyUnit.fromConcrete(Unit.SCALAR));
        for (UnitExpression operand : operands)
        {
            r = r.flatMap(u -> operand.asUnit(unitManager).map(v -> u.times(v)));
        }
        return r;
    }

    @Override
    public String save(boolean structured, boolean topLevel)
    {
        StringBuilder b = new StringBuilder();
        if (!topLevel)
            b.append("(");
        for (int i = 0; i < operands.size(); i++)
        {
            b.append(operands.get(i).save(structured, false));
            if (i < operands.size() - 1)
            {
                b.append("*");
            }
        }
        if (!topLevel)
            b.append(")");
        return b.toString();
    }

    public ImmutableList<@Recorded UnitExpression> getOperands()
    {
        return operands;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UnitTimesExpression that = (UnitTimesExpression) o;

        return operands.equals(that.operands);
    }

    @Override
    public boolean isEmpty()
    {
        return operands.isEmpty() || (operands.size() == 1 && operands.get(0).isEmpty());
    }

    @Override
    public boolean isScalar()
    {
        return operands.size() == 1 && operands.get(0).isScalar();
    }

    @Override
    public int hashCode()
    {
        return operands.hashCode();
    }

    @Override
    public Stream<SingleLoader<UnitExpression, UnitSaver>> loadAsConsecutive(BracketedStatus bracketedStatus)
    {
        StreamTreeBuilder<SingleLoader<UnitExpression, UnitSaver>> r = new StreamTreeBuilder<>();
        boolean needsBrackets = bracketedStatus != BracketedStatus.DIRECT_ROUND_BRACKETED && bracketedStatus != BracketedStatus.TOP_LEVEL;
        if (needsBrackets)
            r.add(UnitEntry.load(UnitBracket.OPEN_ROUND.getContent()));
        r.addAll(Utility.<Stream<SingleLoader<UnitExpression, UnitSaver>>>intercalateStreamM(operands.stream().map(o -> o.loadAsConsecutive(BracketedStatus.MISC)), () -> Stream.of(UnitEntry.load(UnitOp.MULTIPLY))).flatMap(s -> s));
        if (needsBrackets)
            r.add(UnitEntry.load(UnitBracket.CLOSE_ROUND.getContent()));
        return r.stream();
    }

    @Override
    @SuppressWarnings("recorded") // Because the replaced version is immediately loaded again
    public UnitExpression replaceSubExpression(UnitExpression toReplace, UnitExpression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return new UnitTimesExpression(Utility.mapListI(operands, t -> t.replaceSubExpression(toReplace, replaceWith)));
    }
}
