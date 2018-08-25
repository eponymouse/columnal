package records.transformations.expression;

import annotation.identifier.qual.UnitIdentifier;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.GrammarUtility;
import records.gui.expressioneditor.UnitEntry;
import records.gui.expressioneditor.UnitSaver;
import records.jellytype.JellyUnit;
import records.loadsave.OutputBuilder;
import records.typeExp.units.UnitExp;
import styled.StyledString;
import utility.Either;
import utility.IdentifierUtility;
import utility.Pair;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

// Same distinction as IdentExpression/InvalidIdentExpression
public class InvalidSingleUnitExpression extends UnitExpression
{
    private final String name;

    public InvalidSingleUnitExpression(String text)
    {
        this.name = text;
    }

    @Override
    public Either<Pair<StyledString, List<UnitExpression>>, JellyUnit> asUnit(UnitManager unitManager)
    {
        return Either.left(new Pair<>(StyledString.s("Invalid unit name"), ImmutableList.of()));
    }

    @Override
    public String save(boolean topLevel)
    {
        return "@unfinished "+ OutputBuilder.quoted(name);
    }

    @Override
    public Stream<SingleLoader<UnitExpression, UnitSaver>> loadAsConsecutive(BracketedStatus bracketedStatus)
    {
        return Stream.of(UnitEntry.load(name));
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        InvalidSingleUnitExpression that = (InvalidSingleUnitExpression) o;

        return name.equals(that.name);
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
        return name.hashCode();
    }

    @Override
    public UnitExpression replaceSubExpression(UnitExpression toReplace, UnitExpression replaceWith)
    {
        return this == toReplace ? replaceWith : this;
    }

    // IdentExpression if possible, otherwise InvalidIdentExpression
    public static UnitExpression identOrUnfinished(String src)
    {
        @UnitIdentifier String valid = IdentifierUtility.asUnitIdentifier(src);
        if (valid != null)
            return new SingleUnitExpression(valid);
        else
            return new InvalidSingleUnitExpression(src);
    }
}
