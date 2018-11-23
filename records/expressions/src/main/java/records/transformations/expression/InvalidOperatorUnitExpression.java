package records.transformations.expression;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.UnitManager;
import records.grammar.GrammarUtility;
import records.gui.expressioneditor.UnitEntry;
import records.gui.expressioneditor.UnitSaver;
import records.jellytype.JellyUnit;
import records.typeExp.units.UnitExp;
import styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.Utility;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InvalidOperatorUnitExpression extends UnitExpression
{
    private final ImmutableList<UnitExpression> items;

    public InvalidOperatorUnitExpression(ImmutableList<UnitExpression> items)
    {
        this.items = items;
    }

    @Override
    public Either<Pair<StyledString, List<UnitExpression>>, JellyUnit> asUnit(UnitManager unitManager)
    {
        return Either.left(new Pair<>(StyledString.s("Invalid operator combination"), Collections.emptyList()));
    }

    @Override
    public String save(boolean structured, boolean topLevel)
    {
        if (structured)
            return "@INVALIDOPS (" + 
                items.stream().map(item -> item.save(structured, false)).collect(Collectors.joining(", "))
                + ")";
        else
            return items.stream().map(item -> item.save(structured, false)).collect(Collectors.joining(""));
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
        return items.stream().flatMap(x -> x.loadAsConsecutive(BracketedStatus.MISC));
    }

    @Override
    public UnitExpression replaceSubExpression(UnitExpression toReplace, UnitExpression replaceWith)
    {
        if (this == toReplace)
            return replaceWith;
        else
            return new InvalidOperatorUnitExpression(Utility.mapListI(items, e -> e.replaceSubExpression(toReplace, replaceWith)));
    }
}
