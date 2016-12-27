package records.data.columntype;

import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.unit.Unit;

/**
 * Created by neil on 30/10/2016.
 */
public class NumericColumnType extends ColumnType
{
    public final Unit unit;
    public final int minDP;
    private final @Nullable String commonPrefix;

    public NumericColumnType(Unit unit, int minDP, @Nullable String commonPrefix)
    {
        this.unit = unit;
        this.minDP = minDP;
        this.commonPrefix = commonPrefix;
    }

    @Override
    public boolean isNumeric()
    {
        return true;
    }

    public String removePrefix(String val)
    {
        val = val.trim();
        if (commonPrefix != null)
            return StringUtils.removeStart(val, commonPrefix).trim();
        else if (val.startsWith(unit.getDisplayPrefix()))
            return StringUtils.removeStart(val, unit.getDisplayPrefix()).trim();
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
            ", minDP=" + minDP +
            '}';
    }

    public String getPrefixToRemove()
    {
        return unit.getDisplayPrefix();
    }

    public String getSuffixToRemove()
    {
        return unit.getDisplaySuffix();
    }
}
