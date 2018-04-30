package records.data.unit;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public class SpecificSingleUnit extends SingleUnit
{
    private final String unitName;
    private final String description;
    private final String prefix;
    private final String suffix;

    // package-private
    SpecificSingleUnit(String unitName, String description, String prefix, String suffix)
    {
        this.unitName = unitName;
        this.description = description;
        this.prefix = prefix;
        this.suffix = suffix;
    }

    public String getName()
    {
        return unitName;
    }

    public String getPrefix()
    {
        return prefix;
    }

    public String getSuffix()
    {
        return suffix;
    }

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SpecificSingleUnit that = (SpecificSingleUnit) o;

        return unitName.equals(that.unitName);
    }

    @Override
    public int hashCode()
    {
        return unitName.hashCode();
    }

    @Override
    public String toString()
    {
        return unitName;
    }
    
    @Override
    public int compareTo(@NonNull SingleUnit o)
    {
        if (o instanceof SpecificSingleUnit)
            return unitName.compareTo(((SpecificSingleUnit)o).unitName);
        else
            return 1; // type vars come before us
    }
}
