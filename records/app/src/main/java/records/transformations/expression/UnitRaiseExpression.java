package records.transformations.expression;

import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.gui.expressioneditor.ConsecutiveBase;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.UnitCompound;
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
    public Either<Pair<String, List<UnitExpression>>, Unit> asUnit(UnitManager unitManager)
    {
        Either<Pair<String, List<UnitExpression>>, Unit> lhs = unit.asUnit(unitManager);

        return lhs.map(u -> u.raisedTo(power));
    }

    @Override
    public String save(boolean topLevel)
    {
        return unit.save(false) + "^" + power;
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public OperandNode<UnitExpression> edit(ConsecutiveBase<UnitExpression, ExpressionNodeParent> parent, boolean topLevel)
    {
        return new UnitCompound(parent, topLevel);
    }
}
