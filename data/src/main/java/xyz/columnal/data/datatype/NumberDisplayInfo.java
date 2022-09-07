package xyz.columnal.data.datatype;

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

    public String getPaddingChar()
    {
        return rightPadding == Padding.ZERO ? "0" : " ";
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

    @Override
    public boolean equals(@Nullable Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NumberDisplayInfo that = (NumberDisplayInfo) o;

        if (minimumDP != that.minimumDP) return false;
        if (maximumDP != that.maximumDP) return false;
        return rightPadding == that.rightPadding;
    }

    @Override
    public int hashCode()
    {
        int result = minimumDP;
        result = 31 * result + maximumDP;
        result = 31 * result + rightPadding.hashCode();
        return result;
    }

    @Override
    public String toString()
    {
        return "NumberDisplayInfo{" +
            "minimumDP=" + minimumDP +
            ", maximumDP=" + maximumDP +
            ", rightPadding=" + rightPadding +
            '}';
    }
}
