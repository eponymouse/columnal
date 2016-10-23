package records.gui;

import org.checkerframework.checker.lock.qual.GuardSatisfied;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Created by neil on 23/10/2016.
 */
public class DisplayValue
{
    private double loading; // If -1, use String
    private String show;
    private boolean isError; // Highlight the string differently.

    public DisplayValue(String s)
    {
        loading = -1;
        show = s;
    }

    public DisplayValue(double d)
    {
        loading = d;
        show = "";
    }

    public DisplayValue(String s, boolean err)
    {
        show = s;
        isError = err;
    }

    @Override
    public String toString()
    {
        if (loading == -1)
            return show;
        else
            return "Loading: " + loading;
    }
}
