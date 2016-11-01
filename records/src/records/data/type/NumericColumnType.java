package records.data.type;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Created by neil on 30/10/2016.
 */
public class NumericColumnType extends ColumnType
{
    public final String displayPrefix;
    public final boolean mayBeBlank;

    public NumericColumnType(String displayPrefix, boolean mayBeBlank)
    {
        this.displayPrefix = displayPrefix;
        this.mayBeBlank = mayBeBlank;
    }

    @Override
    public boolean isNumeric()
    {
        return true;
    }

    public String removePrefix(String val)
    {
        if (val.startsWith(displayPrefix))
            return val.substring(displayPrefix.length()).trim();
        else
            return val.trim();
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NumericColumnType that = (NumericColumnType) o;

        return displayPrefix.equals(that.displayPrefix) && mayBeBlank == that.mayBeBlank;

    }

    @Override
    public int hashCode()
    {
        return displayPrefix.hashCode() + (mayBeBlank ? 100000 : 0);
    }
}
