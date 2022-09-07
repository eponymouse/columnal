package xyz.columnal.data.datatype;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import xyz.columnal.data.unit.Unit;

/**
 * Created by neil on 15/05/2017.
 */
public class NumberInfo
{
    private final Unit unit;

    public NumberInfo(Unit unit)
    {
        this.unit = unit;
    }

    public static final NumberInfo DEFAULT = new NumberInfo(Unit.SCALAR);
    
    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NumberInfo that = (NumberInfo) o;

        return unit.equals(that.unit);
    }

    @Override
    public int hashCode()
    {
        return unit.hashCode();
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
