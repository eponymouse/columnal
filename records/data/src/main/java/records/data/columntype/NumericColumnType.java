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
    private final @Nullable String commonSuffix;

    public NumericColumnType(Unit unit, int minDP, @Nullable String commonPrefix, @Nullable String commonSuffix)
    {
        this.unit = unit;
        this.displayInfo = new NumberDisplayInfo(minDP, 10, Padding.ZERO);
        this.commonPrefix = commonPrefix;
        this.commonSuffix = commonSuffix;
    }

    /**
     * Removes numeric prefix from the string, and gets rid of commas.
     */
    public String removePrefixAndSuffix(String val)
    {
        val = val.trim();
        if (commonPrefix != null)
            val = StringUtils.removeStart(val, commonPrefix);
        else if (val.startsWith(unit.getDisplayPrefix()))
            val = StringUtils.removeStart(val, unit.getDisplayPrefix());
        
        if (commonSuffix != null)
            val = StringUtils.removeEnd(val, commonSuffix);
        
        val = val.trim().replace(",", "");
        return val;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NumericColumnType that = (NumericColumnType) o;

        if (!displayInfo.equals(that.displayInfo)) return false;
        if (!Objects.equals(commonPrefix, that.commonPrefix)) return false;
        if (!Objects.equals(commonSuffix, that.commonSuffix)) return false;
        return unit.equals(that.unit);
    }

    @Override
    public int hashCode()
    {
        int result = unit.hashCode();
        result = 31 * result + displayInfo.hashCode();
        if (commonPrefix != null)
            result = 31 * result + commonPrefix.hashCode();
        if (commonSuffix != null)
            result = 31 * result + commonSuffix.hashCode();
        return result;
    }

    @Override
    public String toString()
    {
        return "Number{" +
            "unit=" + unit +
            ", displayInfo=" + displayInfo +
            ", commonPrefix='" + commonPrefix + '\'' +
            ", commonPrefix='" + commonSuffix + '\'' +
            '}';
    }
}
