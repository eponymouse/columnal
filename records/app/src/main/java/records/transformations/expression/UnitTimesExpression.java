package records.transformations.expression;

import com.google.common.collect.ImmutableList;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.gui.expressioneditor.ConsecutiveBase;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.UnitCompound;
import records.gui.expressioneditor.UnitNodeParent;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;

import java.util.List;

public class UnitTimesExpression extends UnitExpression
{
    public static enum Op { STAR, SPACE }
    private final ImmutableList<UnitExpression> operands;
    private final ImmutableList<Op> operators;

    public UnitTimesExpression(ImmutableList<UnitExpression> operands, ImmutableList<Op> operators)
    {
        this.operands = operands;
        this.operators = operators;
    }

    @Override
    public Either<Pair<String, List<UnitExpression>>, Unit> asUnit(UnitManager unitManager)
    {
        Either<Pair<String, List<UnitExpression>>, Unit> r = Either.right(Unit.SCALAR);
        for (UnitExpression operand : operands)
        {
            r = r.flatMap(u -> operand.asUnit(unitManager).map(v -> u.times(v)));
        }
        return r;
    }

    @Override
    public String save(boolean topLevel)
    {
        StringBuilder b = new StringBuilder();
        if (!topLevel)
            b.append("(");
        for (int i = 0; i < operands.size(); i++)
        {
            b.append(operands.get(i).save(false));
            if (i < operators.size() - 1)
            {
                b.append(operators.get(i) == Op.STAR ? "*" : " ");
            }
        }
        if (!topLevel)
            b.append(")");
        return b.toString();
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public OperandNode<UnitExpression> edit(ConsecutiveBase<UnitExpression, UnitNodeParent> parent, boolean topLevel)
    {
        return new UnitCompound(parent, topLevel);
    }

    public ImmutableList<UnitExpression> getOperands()
    {
        return operands;
    }
}
