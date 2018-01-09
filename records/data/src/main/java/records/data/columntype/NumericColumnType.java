package records.data.columntype;

import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.data.datatype.NumberDisplayInfo;
import records.data.datatype.NumberDisplayInfo.Padding;
import records.data.unit.Unit;

import java.util.Objects;

/**
 * Created by neil on 30/10/2016.
 */
public class NumericColumnType extends ColumnType
{
    public final Unit unit;
    public final NumberDisplayInfo displayInfo;
    private final @Nullable String commonPrefix;

    public NumericColumnType(Unit unit, int minDP, @Nullable String commonPrefix)
    {
        this.unit = unit;
        this.displayInfo = new NumberDisplayInfo(minDP, 10, Padding.ZERO);
        this.commonPrefix = commonPrefix;
    }

    /**
     * Removes numeric prefix from the string, and gets rid of commas.
     */
    public String removePrefix(String val)
    {
        val = val.trim();
        if (commonPrefix != null)
            return StringUtils.removeStart(val, commonPrefix).trim().replace(",", "");
        else if (val.startsWith(unit.getDisplayPrefix()))
            return StringUtils.removeStart(val, unit.getDisplayPrefix()).trim().replace(",", "");
        else
            return val.trim().replace(",", "");
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NumericColumnType that = (NumericColumnType) o;

        if (!displayInfo.equals(that.displayInfo)) return false;
        if (!Objects.equals(commonPrefix, that.commonPrefix)) return false;
        return unit.equals(that.unit);
    }

    @Override
    public int hashCode()
    {
        int result = unit.hashCode();
        result = 31 * result + displayInfo.hashCode();
        if (commonPrefix != null)
            result = 31 * result + commonPrefix.hashCode();
        return result;
    }

    @Override
    public String toString()
    {
        return "Number{" +
            "unit=" + unit +
            ", displayInfo=" + displayInfo +
            ", commonPrefix='" + commonPrefix + '\'' +
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
