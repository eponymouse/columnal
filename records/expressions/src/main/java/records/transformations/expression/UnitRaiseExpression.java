package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.gui.expressioneditor.ConsecutiveBase;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.UnitCompound;
import records.gui.expressioneditor.UnitNodeParent;
import records.types.units.UnitExp;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;

import java.util.List;

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
    public Either<Pair<String, List<UnitExpression>>, UnitExp> asUnit(UnitManager unitManager)
    {
        Either<Pair<String, List<UnitExpression>>, UnitExp> lhs = unit.asUnit(unitManager);

        return lhs.map(u -> u.raisedTo(power));
    }

    @Override
    public String save(boolean topLevel)
    {
        return unit.save(false) + "^" + power;
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public OperandNode<UnitExpression, UnitNodeParent> edit(ConsecutiveBase<UnitExpression, UnitNodeParent> parent, boolean topLevel)
    {
        return new UnitCompound(parent, topLevel);
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
    public int hashCode()
    {
        int result = unit.hashCode();
        result = 31 * result + power;
        return result;
    }
}
