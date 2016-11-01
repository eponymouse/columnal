package records.gui;

import org.checkerframework.checker.lock.qual.GuardSatisfied;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Optional;

/**
 * A value describing how to display a single piece of data.
 *
 * It can be in three states:
 *  - Loading (with a progress amount)
 *  - Successfully loaded (with a String which is what to show in the cell)
 *  - Unsuccessful (with a String giving an error description)
 */
public class DisplayValue
{
    public static enum ProgressState
    {
        GETTING, QUEUED;
    }

    private final @Nullable ProgressState state;
    private final double loading; // If -1, use String
    private final @Nullable String show;
    private final boolean isError; // Highlight the string differently.

    /**
     * Create successfully loaded item
     */
    public DisplayValue(Object val)
    {
        show = convertToString(val);
        state = null;
        loading = -1;
        isError = false;
    }

    /**
     * Creating loading-in-progress item (param is progress, 0 to 1)
     */
    public DisplayValue(ProgressState state, double d)
    {
        this.state = state;
        loading = d;
        show = null;
        isError = false;
    }

    /**
     * Create error item (if err is true; err being false is same as single-arg constructor).
     */
    public DisplayValue(String s, boolean err)
    {
        show = s;
        isError = err;
        loading = -1;
        state = null;
    }

    @Override
    @SuppressWarnings("nullness")
    public String toString()
    {
        if (loading == -1)
            return show;
        else
            return state.toString() + ": " + loading;
    }

    @SuppressWarnings("nullness")
    private static String convertToString(Object val)
    {
        if (val instanceof Optional)
        {
            Optional<?> o = (Optional<?>)val;
            return o.map(DisplayValue::convertToString).orElse("");
        }

        return val.toString();
    }
}
