package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.UnitManager;
import records.gui.expressioneditor.ConsecutiveBase;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.UnitCompound;
import records.gui.expressioneditor.UnitNodeParent;
import records.types.units.UnitExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
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
    public Either<Pair<StyledString, List<UnitExpression>>, UnitExp> asUnit(UnitManager unitManager)
    {
        Either<Pair<StyledString, List<UnitExpression>>, UnitExp> num = numerator.asUnit(unitManager);
        Either<Pair<StyledString, List<UnitExpression>>, UnitExp> den = denominator.asUnit(unitManager);

        return num.flatMap(n -> den.map(d -> n.divideBy(d)));
    }

    @Override
    public String save(boolean topLevel)
    {
        String core = numerator.save(false) + "/" + denominator.save(false);
        if (topLevel)
            return core;
        else
            return "(" + core + ")";
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public OperandNode<UnitExpression, UnitNodeParent> edit(ConsecutiveBase<UnitExpression, UnitNodeParent> parent, boolean topLevel)
    {
        return new UnitCompound(parent, topLevel);
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
    public int hashCode()
    {
        int result = numerator.hashCode();
        result = 31 * result + denominator.hashCode();
        return result;
    }
}
