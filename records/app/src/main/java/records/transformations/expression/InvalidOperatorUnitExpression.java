package records.transformations.expression;

import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.gui.expressioneditor.ConsecutiveBase;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.UnitCompound;
import records.gui.expressioneditor.UnitNodeParent;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;

import java.util.Collections;
import java.util.List;

public class InvalidOperatorUnitExpression extends UnitExpression
{
    @Override
    public Either<Pair<String, List<UnitExpression>>, Unit> asUnit(UnitManager unitManager)
    {
        return Either.left(new Pair<>("Invalid operator combination", Collections.emptyList()));
    }

    @Override
    public String save(boolean topLevel)
    {
        return "TODO";
    }

    @Override
    public @OnThread(Tag.FXPlatform) OperandNode<UnitExpression> edit(ConsecutiveBase<UnitExpression, UnitNodeParent> parent, boolean topLevel)
    {
        return new UnitCompound(parent, topLevel);
    }
}
