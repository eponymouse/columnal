package records.data.unit;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.DataType;
import records.data.datatype.DataType.TagType;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by neil on 09/12/2016.
 */
public class UnitDeclaration
{
    private final SingleUnit definedUnit;
    private final List<String> otherNames = new ArrayList<>();
    private final @Nullable Unit equivalentTo;
    private final Unit cachedSingleUnit;

    public UnitDeclaration(SingleUnit definedUnit, @Nullable Unit equivalentTo)
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

    public void addAlias(String newName)
    {
        otherNames.add(newName);
    }
}
