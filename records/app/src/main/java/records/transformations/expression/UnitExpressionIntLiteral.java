package records.transformations.expression;

import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.gui.expressioneditor.ConsecutiveBase;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.UnitEntry;
import records.gui.expressioneditor.UnitNodeParent;
import threadchecker.OnThread;
import threadchecker.Tag;
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
    public Either<Pair<String, List<UnitExpression>>, Unit> asUnit(UnitManager unitManager)
    {
        if (number == 1)
            return Either.right(Unit.SCALAR);
        else
            return Either.left(new Pair<>("Expected unit not number", Collections.emptyList()));
    }

    @Override
    public String save(boolean topLevel)
    {
        return Integer.toString(number);
    }

    @Override
    public @OnThread(Tag.FXPlatform) OperandNode<UnitExpression> edit(ConsecutiveBase<UnitExpression, UnitNodeParent> parent, boolean topLevel)
    {
        return new UnitEntry(parent, Integer.toString(number));
    }

    public int getNumber()
    {
        return number;
    }
}
