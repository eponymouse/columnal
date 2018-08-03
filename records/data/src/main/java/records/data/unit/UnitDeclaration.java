package records.data.unit;

import annotation.identifier.qual.UnitIdentifier;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import org.sosy_lab.common.rationals.Rational;
import utility.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by neil on 09/12/2016.
 */
public class UnitDeclaration
{
    private final SingleUnit definedUnit;
    private final @Nullable Pair<Rational, Unit> equivalentTo;
    private final Unit cachedSingleUnit;

    public UnitDeclaration(SingleUnit definedUnit, @Nullable Pair<Rational, Unit> equivalentTo)
    {
        this.definedUnit = definedUnit;
        this.equivalentTo = equivalentTo;
        cachedSingleUnit = new Unit(definedUnit);
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
}
