package records.data.unit;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Created by neil on 09/12/2016.
 */
public abstract class SingleUnit implements Comparable<SingleUnit>
{
    public abstract String getPrefix();
    public abstract String getSuffix();
    
    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(@Nullable Object obj);
}
