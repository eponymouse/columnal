package records.data.type;

import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Created by neil on 30/10/2016.
 */
public class NumericColumnType extends ColumnType
{
    public final String displayPrefix;

    public NumericColumnType(String displayPrefix)
    {
        this.displayPrefix = displayPrefix;
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

        return displayPrefix.equals(that.displayPrefix);

    }

    @Override
    public int hashCode()
    {
        return displayPrefix.hashCode();
    }
}
