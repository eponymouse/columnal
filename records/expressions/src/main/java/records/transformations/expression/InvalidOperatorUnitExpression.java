package records.transformations.expression;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.UnitManager;
import records.grammar.GrammarUtility;
import records.gui.expressioneditor.UnitEntry;
import records.gui.expressioneditor.UnitEntry.UnitText;
import records.gui.expressioneditor.UnitSaver;
import records.typeExp.units.UnitExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class InvalidOperatorUnitExpression extends UnitExpression
{
    private final ImmutableList<Either<String, UnitExpression>> items;

    public InvalidOperatorUnitExpression(ImmutableList<Either<String, UnitExpression>> items)
    {
        this.items = items;
    }

    @Override
    public Either<Pair<StyledString, List<UnitExpression>>, UnitExp> asUnit(UnitManager unitManager)
    {
        return Either.left(new Pair<>(StyledString.s("Invalid operator combination"), Collections.emptyList()));
    }

    @Override
    public String save(boolean topLevel)
    {
        StringBuilder s = new StringBuilder("@INVALIDOPS (");
        for (Either<String, UnitExpression> item : items)
        {
            s.append(item.<String>either(q -> "\"" + GrammarUtility.escapeChars(q) + "\"", x -> x.save(false)));
        }
        return s.append(")").toString();
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        InvalidOperatorUnitExpression that = (InvalidOperatorUnitExpression) o;

        return items.equals(that.items);
    }

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    @Override
    public boolean isScalar()
    {
        return false;
    }

    @Override
    public int hashCode()
    {
        return items.hashCode();
    }

    @Override
    public @OnThread(Tag.FXPlatform) Stream<SingleLoader<UnitExpression, UnitSaver>> loadAsConsecutive(BracketedStatus bracketedStatus)
    {
        return items.stream().flatMap(x -> x.either(s -> Stream.of((SingleLoader<UnitExpression, UnitSaver>)(UnitEntry.load(new UnitText(s)))), e -> e.loadAsConsecutive(BracketedStatus.MISC)));
    }

}
