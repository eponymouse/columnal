package records.transformations.expression;

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
    public Either<Pair<String, List<UnitExpression>>, Unit> asUnit(UnitManager unitManager)
    {
        Either<Pair<String, List<UnitExpression>>, Unit> num = numerator.asUnit(unitManager);
        Either<Pair<String, List<UnitExpression>>, Unit> den = denominator.asUnit(unitManager);

        return num.flatMap(n -> den.map(d -> n.divide(d)));
    }

    @Override
    public String save(boolean topLevel)
    {
        return numerator.save(false) + "/" + denominator.save(false);
    }

    @Override
    @OnThread(Tag.FXPlatform)
    public OperandNode<UnitExpression> edit(ConsecutiveBase<UnitExpression, UnitNodeParent> parent, boolean topLevel)
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
}
