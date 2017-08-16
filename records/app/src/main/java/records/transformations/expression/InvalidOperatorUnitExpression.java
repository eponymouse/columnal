package records.transformations.expression;

import com.google.common.collect.ImmutableList;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.grammar.GrammarUtility;
import records.gui.expressioneditor.ConsecutiveBase;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.UnitCompound;
import records.gui.expressioneditor.UnitNodeParent;
import records.transformations.expression.UnitTimesExpression.Op;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;

import java.util.Collections;
import java.util.List;

public class InvalidOperatorUnitExpression extends UnitExpression
{
    private final ImmutableList<UnitExpression> operands;
    private final ImmutableList<String> operators;

    public InvalidOperatorUnitExpression(ImmutableList<UnitExpression> operands, ImmutableList<String> operators)
    {
        this.operands = operands;
        this.operators = operators;
    }

    @Override
    public Either<Pair<String, List<UnitExpression>>, Unit> asUnit(UnitManager unitManager)
    {
        return Either.left(new Pair<>("Invalid operator combination", Collections.emptyList()));
    }

    @Override
    public String save(boolean topLevel)
    {
        StringBuilder b = new StringBuilder();
        if (!topLevel)
            b.append("(");
        b.append("@invalidopunit ");
        for (int i = 0; i < operands.size(); i++)
        {
            b.append(operands.get(i).save(false));
            if (i < operators.size() - 1)
            {
                b.append("\"" + GrammarUtility.escapeChars(operators.get(i)) + "\"");
            }
        }
        if (!topLevel)
            b.append(")");
        return b.toString();
    }

    @Override
    public @OnThread(Tag.FXPlatform) OperandNode<UnitExpression> edit(ConsecutiveBase<UnitExpression, UnitNodeParent> parent, boolean topLevel)
    {
        return new UnitCompound(parent, topLevel);
    }
}
