package records.transformations.expression;

import annotation.identifier.qual.UnitIdentifier;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.UnitManager;
import records.jellytype.JellyUnit;
import records.loadsave.OutputBuilder;
import styled.StyledString;
import utility.Either;
import utility.IdentifierUtility;
import utility.Pair;

import java.util.List;

// Same distinction as IdentExpression/InvalidIdentExpression
public class InvalidSingleUnitExpression extends UnitExpression
{
    private final String name;

    public InvalidSingleUnitExpression(String text)
    {
        this.name = text;
    }

    @Override
    public Either<Pair<StyledString, ImmutableList<QuickFix<@Recorded UnitExpression>>>, JellyUnit> asUnit(UnitManager unitManager)
    {
        return Either.left(new Pair<>(StyledString.s("Invalid unit name"), ImmutableList.of()));
    }

    @Override
    public String save(boolean structured, boolean topLevel)
    {
        if (structured)
            return "@unfinished "+ OutputBuilder.quoted(name);
        else
            return name;
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
