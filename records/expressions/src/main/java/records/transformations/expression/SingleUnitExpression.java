package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.gui.expressioneditor.ConsecutiveBase;
import records.gui.expressioneditor.ExpressionNodeParent;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.OperatorEntry;
import records.gui.expressioneditor.UnitEntry;
import records.gui.expressioneditor.UnitNodeParent;
import records.types.units.UnitExp;
import styled.StyledString;
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
    public Either<Pair<StyledString, List<UnitExpression>>, UnitExp> asUnit(UnitManager unitManager)
    {
        try
        {
            return Either.right(UnitExp.fromConcrete(unitManager.loadUse(name)));
        }
        catch (InternalException | UserException e)
        {
            // TODO add similarly spelt unit names:
            return Either.left(new Pair<>(StyledString.s(e.getLocalizedMessage()), Collections.emptyList()));
        }
    }

    @Override
    public String save(boolean topLevel)
    {
        return name;
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public OperandNode<UnitExpression, UnitNodeParent> edit(ConsecutiveBase<UnitExpression, UnitNodeParent> parent, boolean topLevel)
    {
        return new UnitEntry(parent, name, false);
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SingleUnitExpression that = (SingleUnitExpression) o;

        return name.equals(that.name);
    }

    @Override
    public int hashCode()
    {
        return name.hashCode();
    }

    @Override
    public Pair<List<SingleLoader<UnitExpression, UnitNodeParent, OperandNode<UnitExpression, UnitNodeParent>>>, List<SingleLoader<UnitExpression, UnitNodeParent, OperatorEntry<UnitExpression, UnitNodeParent>>>> loadAsConsecutive(boolean implicitlyRoundBracketed)
    {
        return new Pair<>(Collections.singletonList(loadAsSingle()), Collections.emptyList());
    }
}
