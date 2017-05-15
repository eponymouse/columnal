package records.data.datatype;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.Unit;

/**
 * Created by neil on 15/05/2017.
 */
public class NumberInfo
{
    private final Unit unit;
    private final int minimumDP;

    public NumberInfo(Unit unit, int minimumDP)
    {
        this.unit = unit;
        this.minimumDP = minimumDP;
    }

    public static final NumberInfo DEFAULT = new NumberInfo(Unit.SCALAR, 0);

    public int getMinimumDP()
    {
        return minimumDP;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NumberInfo that = (NumberInfo) o;

        if (minimumDP != that.minimumDP) return false;
        return unit.equals(that.unit);
    }

    @Override
    public int hashCode()
    {
        int result = unit.hashCode();
        result = 31 * result + minimumDP;
        return result;
    }

    public Unit getUnit()
    {
        return unit;
    }

    public boolean sameType(@Nullable NumberInfo numberInfo)
    {
        if (numberInfo == null)
            return false;
        return unit.equals(numberInfo.unit);
    }


    public int hashCodeForType()
    {
        return unit.hashCode();
    }
}
