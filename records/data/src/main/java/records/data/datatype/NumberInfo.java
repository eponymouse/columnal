package records.data.datatype;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.unit.Unit;

/**
 * Created by neil on 15/05/2017.
 */
public class NumberInfo
{
    private final Unit unit;
    // Can override file-wide default.  If null, use file-wide default
    private final @Nullable NumberDisplayInfo numberDisplayInfo;

    public NumberInfo(Unit unit, @Nullable NumberDisplayInfo numberDisplayInfo)
    {
        this.unit = unit;
        this.numberDisplayInfo = numberDisplayInfo;
    }

    public static final NumberInfo DEFAULT = new NumberInfo(Unit.SCALAR, null);

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NumberInfo that = (NumberInfo) o;

        if (!unit.equals(that.unit)) return false;
        return numberDisplayInfo != null ? numberDisplayInfo.equals(that.numberDisplayInfo) : that.numberDisplayInfo == null;
    }

    @Override
    public int hashCode()
    {
        int result = unit.hashCode();
        result = 31 * result + (numberDisplayInfo != null ? numberDisplayInfo.hashCode() : 0);
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

    @Pure
    public @Nullable NumberDisplayInfo getDisplayInfo()
    {
        return numberDisplayInfo;
    }
}
