package records.transformations.expression;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.UnitManager;
import records.gui.expressioneditor.OperandNode;
import records.gui.expressioneditor.OperatorEntry;
import records.gui.expressioneditor.UnitEntry;
import records.gui.expressioneditor.UnitNodeParent;
import records.loadsave.OutputBuilder;
import records.typeExp.units.UnitExp;
import styled.StyledString;
import utility.Either;
import utility.Pair;

import java.util.Collections;
import java.util.List;

/**
 * Created by neil on 18/02/2017.
 */
public class UnfinishedUnitExpression extends UnitExpression
{
    private final String text;

    public UnfinishedUnitExpression(String text)
    {
        this.text = text;
    }

    @Override
    public Either<Pair<StyledString, List<UnitExpression>>, UnitExp> asUnit(UnitManager unitManager)
    {
        // TODO add known similar units:
        return Either.left(new Pair<>(StyledString.s("Unknown unit"), Collections.emptyList()));
    }

    @Override
    public String save(boolean topLevel)
    {
        return "@unfinished " + OutputBuilder.quoted(text);
    }

    @Override
    public SingleLoader<UnitExpression, UnitNodeParent, OperandNode<UnitExpression, UnitNodeParent>> loadAsSingle()
    {
        return (p, s) -> new UnitEntry(p, text, false);
    }
    


    @Override
    public boolean equals(@Nullable Object o)
    {
        return o instanceof UnfinishedUnitExpression && text.equals(((UnfinishedUnitExpression)o).text);
    }

    @Override
    public boolean isEmpty()
    {
        return text.isEmpty();
    }

    @Override
    public boolean isScalar()
    {
        return text.equals("1") || isEmpty();
    }

    @Override
    public int hashCode()
    {
        return text.hashCode();
    }

    public String getText()
    {
        return text;
    }

    @Override
    public Pair<List<SingleLoader<UnitExpression, UnitNodeParent, OperandNode<UnitExpression, UnitNodeParent>>>, List<SingleLoader<UnitExpression, UnitNodeParent, OperatorEntry<UnitExpression, UnitNodeParent>>>> loadAsConsecutive(boolean implicitlyRoundBracketed)
    {
        return new Pair<>(Collections.singletonList(loadAsSingle()), Collections.emptyList());
    }
}
