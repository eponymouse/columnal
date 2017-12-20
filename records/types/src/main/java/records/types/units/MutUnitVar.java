package records.types.units;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;
import records.data.unit.SingleUnit;

public class MutUnitVar implements Comparable<MutUnitVar>
{
    // For Comparable purposes
    private static long nextId = 0;
    private final long id = nextId++;
    
    // package-visible:
    @Nullable UnitExp pointer;

    @Override
    public int compareTo(@NotNull MutUnitVar o)
    {
        return Long.compare(id, o.id);
    }

    @Override
    public String toString()
    {
        return "_u" + id; 
    }
}
