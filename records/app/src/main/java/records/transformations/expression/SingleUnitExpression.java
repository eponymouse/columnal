package records.transformations.expression;

import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ConsecutiveBase;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.UnitEntry;
import records.gui.expressioneditor.UnitNodeParent;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;

import java.util.Collections;
import java.util.List;

public class SingleUnitExpression extends UnitExpression
{
    private final String name;

    public SingleUnitExpression(String text)
    {
        this.name = text;
    }

    @Override
    public Either<Pair<String, List<UnitExpression>>, Unit> asUnit(UnitManager unitManager)
    {
        try
        {
            return Either.right(unitManager.loadUse(name));
        }
        catch (InternalException | UserException e)
        {
            // TODO add similarly spelt unit names:
            return Either.left(new Pair<>(e.getLocalizedMessage(), Collections.emptyList()));
        }
    }

    @Override
    public String save(boolean topLevel)
    {
        return name;
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public OperandNode<UnitExpression> edit(ConsecutiveBase<UnitExpression, UnitNodeParent> parent, boolean topLevel)
    {
        return new UnitEntry(parent, name);
    }
}
