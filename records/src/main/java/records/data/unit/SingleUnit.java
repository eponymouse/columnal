package records.data.unit;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * Created by neil on 09/12/2016.
 */
public class SingleUnit implements Comparable<SingleUnit>
{
    private final String unitName;
    private final String description;
    private final String prefix;
    private final String suffix;

    // package-private
    SingleUnit(String unitName, String description, String prefix, String suffix)
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

        SingleUnit that = (SingleUnit) o;

        return unitName.equals(that.unitName);
    }

    @Override
    public int hashCode()
    {
        return unitName.hashCode();
    }

    @Override
    public int compareTo(@NotNull SingleUnit o)
    {
        return unitName.compareTo(o.unitName);
    }
}
