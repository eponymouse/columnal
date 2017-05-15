package records.data.datatype;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;

/**
 * Created by neil on 15/05/2017.
 */
public class NumberDisplayInfo
{
    public static enum Padding
    {
        ZERO, SPACE;
    }

    private final int minimumDP;
    private final int maximumDP;
    private final Padding rightPadding;

    public static final NumberDisplayInfo SYSTEMWIDE_DEFAULT = new NumberDisplayInfo(0, 3, Padding.SPACE);

    public NumberDisplayInfo(int minimumDP, int maximumDP, Padding rightPadding)
    {
        this.minimumDP = minimumDP;
        this.maximumDP = maximumDP;
        this.rightPadding = rightPadding;
    }

    @Pure
    public int getMinimumDP()
    {
        return minimumDP;
    }

    @Pure
    public int getMaximumDP()
    {
        return maximumDP;
    }

    public static @Nullable NumberDisplayInfo merge(@Nullable NumberDisplayInfo a, @Nullable NumberDisplayInfo b)
    {
        if (a == null && b == null)
            return null;
        else if (a == null)
            return b;
        else if (b == null)
            return a;
        else
        {
            return new NumberDisplayInfo(Math.max(a.minimumDP, b.minimumDP), Math.max(a.maximumDP, b.maximumDP), a.rightPadding);
        }
    }
}
