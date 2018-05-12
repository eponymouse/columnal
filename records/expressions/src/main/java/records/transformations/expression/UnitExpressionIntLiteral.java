package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.UnitManager;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.OperatorEntry;
import records.gui.expressioneditor.UnitEntry;
import records.gui.expressioneditor.UnitNodeParent;
import records.typeExp.units.UnitExp;
import styled.StyledString;
import utility.Either;
import utility.Pair;

import java.util.Collections;
import java.util.List;

public class UnitExpressionIntLiteral extends UnitExpression
{
    private final int number;

    public UnitExpressionIntLiteral(int number)
    {
        this.number = number;
    }

    @Override
    public Either<Pair<StyledString, List<UnitExpression>>, UnitExp> asUnit(UnitManager unitManager)
    {
        if (number == 1)
            return Either.right(UnitExp.SCALAR);
        else
            return Either.left(new Pair<>(StyledString.s("Expected unit not number"), Collections.emptyList()));
    }

    @Override
    public String save(boolean topLevel)
    {
        return Integer.toString(number);
    }

    @Override
    public SingleLoader<UnitExpression, UnitNodeParent, OperandNode<UnitExpression, UnitNodeParent>> loadAsSingle()
    {
        return (p, s) -> new UnitEntry(p, Integer.toString(number), false);
    }

    public int getNumber()
    {
        return number;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UnitExpressionIntLiteral that = (UnitExpressionIntLiteral) o;

        return number == that.number;
    }

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    @Override
    public boolean isScalar()
    {
        return number == 1;
    }

    @Override
    public int hashCode()
    {
        return number;
    }

    @Override
    public Pair<List<SingleLoader<UnitExpression, UnitNodeParent, OperandNode<UnitExpression, UnitNodeParent>>>, List<SingleLoader<UnitExpression, UnitNodeParent, OperatorEntry<UnitExpression, UnitNodeParent>>>> loadAsConsecutive(boolean implicitlyRoundBracketed)
    {
        return new Pair<>(Collections.singletonList(loadAsSingle()), Collections.emptyList());
    }
}
