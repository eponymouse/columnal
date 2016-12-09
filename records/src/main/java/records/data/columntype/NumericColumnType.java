package records.data.columntype;

import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.Unit;

/**
 * Created by neil on 30/10/2016.
 */
public class NumericColumnType extends ColumnType
{
    public final Unit unit;
    public final boolean mayBeBlank;
    public final int minDP;

    public NumericColumnType(Unit unit, int minDP, boolean mayBeBlank)
    {
        this.unit = unit;
        this.minDP = minDP;
        this.mayBeBlank = mayBeBlank;
    }

    @Override
    public boolean isNumeric()
    {
        return true;
    }

    public String removePrefix(String val)
    {
        if (val.startsWith(unit.getDisplayPrefix()))
            return val.substring(unit.getDisplayPrefix().length()).trim();
        else
            return val.trim();
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NumericColumnType that = (NumericColumnType) o;

        if (minDP != that.minDP) return false;
        return unit.equals(that.unit);
    }

    @Override
    public int hashCode()
    {
        int result = unit.hashCode();
        result = 31 * result + minDP;
        return result;
    }

    @Override
    public String toString()
    {
        return "NumericColumnType{" +
            "unit=" + unit +
            ", mayBeBlank=" + mayBeBlank +
            ", minDP=" + minDP +
            '}';
    }
}
