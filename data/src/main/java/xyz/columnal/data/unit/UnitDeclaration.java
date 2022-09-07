package xyz.columnal.data.unit;

import annotation.identifier.qual.UnitIdentifier;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import org.sosy_lab.common.rationals.Rational;
import xyz.columnal.utility.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Created by neil on 09/12/2016.
 */
public class UnitDeclaration
{
    private final SingleUnit definedUnit;
    private final @Nullable Pair<Rational, Unit> equivalentTo;
    private final Unit cachedSingleUnit;
    private final String category;

    public UnitDeclaration(SingleUnit definedUnit, @Nullable Pair<Rational, Unit> equivalentTo, String category)
    {
        this.definedUnit = definedUnit;
        this.equivalentTo = equivalentTo;
        cachedSingleUnit = new Unit(definedUnit);
        this.category = category;
    }

    public SingleUnit getDefined()
    {
        return definedUnit;
    }

    public Unit getUnit()
    {
        return cachedSingleUnit;
    }

    @Pure
    public @Nullable Pair<Rational, Unit> getEquivalentTo()
    {
        return equivalentTo;
    }

    // Needed for testing:
    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UnitDeclaration that = (UnitDeclaration) o;
        return Objects.equals(definedUnit, that.definedUnit) &&
                Objects.equals(equivalentTo, that.equivalentTo);
    }

    @Override
    public int hashCode()
    {

        return Objects.hash(definedUnit, equivalentTo);
    }

    @Override
    public String toString()
    {
        return "UnitDeclaration{" +
                "definedUnit=" + definedUnit +
                ", equivalentTo=" + equivalentTo +
                '}';
    }

    public String getCategory()
    {
        return category;
    }
}
