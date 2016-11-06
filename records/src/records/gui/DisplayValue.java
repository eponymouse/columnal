package records.gui;

import org.checkerframework.checker.lock.qual.GuardSatisfied;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;

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

    private final @Nullable Number number;
    // These next two are fixed per-column, but it's just
    // easier to store them with the data item itself:
    private final String displayPrefix;
    private final int minimumDecimalPlaces;
    private final @Nullable ProgressState state;
    private final double loading; // If -1, use String
    private final @Nullable String show;
    private final boolean isError; // Highlight the string differently.

    /**
     * Create successfully loaded item with text
     */
    public DisplayValue(String val)
    {
        this(val, false);
    }

    /**
     * Create successfully loaded item with number
     */
    public DisplayValue(Number val, String displayPrefix, int minimumDecimalPlaces)
    {
        number = val;
        this.displayPrefix = displayPrefix;
        this.minimumDecimalPlaces = minimumDecimalPlaces;
        show = null;
        state = null;
        loading = -1;
        isError = false;
    }

    /**
     * Creating loading-in-progress item (param is progress, 0 to 1)
     */
    public DisplayValue(ProgressState state, double d)
    {
        number = null;
        minimumDecimalPlaces = 0;
        this.state = state;
        displayPrefix = "";
        loading = d;
        show = null;
        isError = false;
    }

    /**
     * Create error item (if err is true; err being false is same as single-arg constructor).
     */
    public DisplayValue(String s, boolean err)
    {
        displayPrefix = "";
        number = null;
        minimumDecimalPlaces = 0;
        show = s;
        isError = err;
        loading = -1;
        state = null;
    }

    @Pure
    public @Nullable Number getNumber()
    {
        return number;
    }

    @Pure
    public String getDisplayPrefix()
    {
        return displayPrefix;
    }

    @Pure
    public int getMinimumDecimalPlaces()
    {
        return minimumDecimalPlaces;
    }

    @Override
    @SuppressWarnings("nullness")
    public String toString()
    {
        if (loading == -1)
            return show == null ? number.toString() : show;
        else
            return state.toString() + ": " + loading;
    }
}
