package xyz.columnal.transformations.expression;

import annotation.identifier.qual.UnitIdentifier;
import annotation.recorded.qual.Recorded;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.jellytype.JellyUnit;
import xyz.columnal.loadsave.OutputBuilder;
import xyz.columnal.transformations.expression.Expression.SaveDestination;
import xyz.columnal.styled.StyledString;
import xyz.columnal.utility.IdentifierUtility;

// Same distinction as IdentExpression/InvalidIdentExpression
public class InvalidSingleUnitExpression extends UnitExpression
{
    private final String name;

    public InvalidSingleUnitExpression(String text)
    {
        this.name = text;
    }

    @Override
    public JellyUnit asUnit(@Recorded InvalidSingleUnitExpression this, UnitManager unitManager) throws UnitLookupException
    {
        throw new UnitLookupException(StyledString.s("Invalid unit name"), this, ImmutableList.of());
    }

    @Override
    public String save(SaveDestination saveDestination, boolean topLevel)
    {
        if (saveDestination.needKeywords())
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
