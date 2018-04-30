package records.data.unit;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.rationals.Rational;
import utility.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by neil on 09/12/2016.
 */
public class UnitDeclaration
{
    private final SpecificSingleUnit definedUnit;
    private final List<String> otherNames = new ArrayList<>();
    private final @Nullable Pair<Rational, Unit> equivalentTo;
    private final Unit cachedSingleUnit;

    public UnitDeclaration(SpecificSingleUnit definedUnit, @Nullable Pair<Rational, Unit> equivalentTo)
    {
        this.definedUnit = definedUnit;
        this.equivalentTo = equivalentTo;
        cachedSingleUnit = new Unit(definedUnit);
    }

    public SpecificSingleUnit getDefined()
    {
        return definedUnit;
    }

    public Unit getUnit()
    {
        return cachedSingleUnit;
    }

    public void addAlias(String newName)
    {
        otherNames.add(newName);
    }

    public @Nullable Pair<Rational, Unit> getEquivalentTo()
    {
        return equivalentTo;
    }
}
