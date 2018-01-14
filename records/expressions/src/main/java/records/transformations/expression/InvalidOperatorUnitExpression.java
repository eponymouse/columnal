package records.transformations.expression;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.Unit;
import records.data.unit.UnitManager;
import records.grammar.GrammarUtility;
import records.gui.expressioneditor.ConsecutiveBase;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.OperatorEntry;
import records.gui.expressioneditor.UnitCompound;
import records.gui.expressioneditor.UnitNodeParent;
import records.types.units.UnitExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.Utility;

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
    public Either<Pair<StyledString, List<UnitExpression>>, UnitExp> asUnit(UnitManager unitManager)
    {
        return Either.left(new Pair<>(StyledString.s("Invalid operator combination"), Collections.emptyList()));
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
    public @OnThread(Tag.FXPlatform) OperandNode<UnitExpression, UnitNodeParent> edit(ConsecutiveBase<UnitExpression, UnitNodeParent> parent, boolean topLevel)
    {
        return new UnitCompound(parent, topLevel);
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        InvalidOperatorUnitExpression that = (InvalidOperatorUnitExpression) o;

        if (!operands.equals(that.operands)) return false;
        return operators.equals(that.operators);
    }

    @Override
    public int hashCode()
    {
        int result = operands.hashCode();
        result = 31 * result + operators.hashCode();
        return result;
    }

    @Override
    public Pair<List<SingleLoader<UnitExpression, UnitNodeParent, OperandNode<UnitExpression, UnitNodeParent>>>, List<SingleLoader<UnitExpression, UnitNodeParent, OperatorEntry<UnitExpression, UnitNodeParent>>>> loadAsConsecutive(boolean implicitlyRoundBracketed)
    {
        return new Pair<>(Utility.mapList(operands, o -> o.loadAsSingle()), Utility.mapList(operators, o -> new SingleLoader<UnitExpression, UnitNodeParent, OperatorEntry<UnitExpression, UnitNodeParent>>()
        {
            @Override
            @OnThread(Tag.FXPlatform)
            public OperatorEntry<UnitExpression, UnitNodeParent> load(ConsecutiveBase<UnitExpression, UnitNodeParent> p, UnitNodeParent s)
            {
                return new OperatorEntry<>(UnitExpression.class, o, false, p);
            }
        }));
    }
}
