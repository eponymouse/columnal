package records.gui;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.unit.Unit;

import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A value describing how to display a single piece of data.
 *
 * It can be in three states:
 *  - Loading (with a progress amount)
 *  - Successfully loaded (with a String which is what to show in the cell)
 *  - Unsuccessful (with a String giving an error description)
 */
public class DisplayValue extends DisplayValueBase
{
    public static enum ProgressState
    {
        GETTING, QUEUED;
    }

    private final @Nullable Number number;
    // These next two are fixed per-column, but it's just
    // easier to store them with the data item itself:
    private final Unit unit;
    private final int minimumDecimalPlaces;
    private final @Nullable ProgressState state;
    private final double loading; // If -1, use String
    private final @Nullable String show;
    private final boolean isError; // Highlight the string differently.
    private final boolean isAddExtraRowItem; // The "Add more data" row

    /**
     * Create successfully loaded item with text
     */
    public DisplayValue(int rowIndex, String val)
    {
        this(rowIndex, val, false);
    }

    public DisplayValue(int rowIndex, TemporalAccessor temporal)
    {
        this(rowIndex, temporal.toString());
    }

    public DisplayValue(int rowIndex, boolean b)
    {
        this(rowIndex, Boolean.toString(b));
    }

    /**
     * Create successfully loaded item with number
     */
    public DisplayValue(int rowIndex, Number val, Unit unit, int minimumDecimalPlaces)
    {
        super(rowIndex);
        number = val;
        this.unit = unit;
        this.minimumDecimalPlaces = minimumDecimalPlaces;
        show = null;
        state = null;
        loading = -1;
        isError = false;
        isAddExtraRowItem = false;
    }

    /**
     * Creating loading-in-progress item (param is progress, 0 to 1)
     */
    public DisplayValue(int rowIndex, ProgressState state, double d)
    {
        super(rowIndex);
        number = null;
        minimumDecimalPlaces = 0;
        this.state = state;
        this.unit = Unit.SCALAR;
        loading = d;
        show = null;
        isError = false;
        isAddExtraRowItem = false;
    }

    /**
     * Create error item (if err is true; err being false is same as single-arg constructor).
     */
    public DisplayValue(int rowIndex, String s, boolean err)
    {
        super(rowIndex);
        unit = Unit.SCALAR;
        number = null;
        minimumDecimalPlaces = 0;
        show = s;
        isError = err;
        loading = -1;
        state = null;
        isAddExtraRowItem = false;
    }

    /**
     * Special constructor for the "add more data" row.
     */
    private DisplayValue(int rowIndex)
    {
        super(rowIndex);
        number = null;
        minimumDecimalPlaces = 0;
        this.state = null;
        this.unit = Unit.SCALAR;
        loading = -1;
        show = null;
        isError = false;
        isAddExtraRowItem = true;
    }

    @Override
    public String getEditString()
    {
        //TODO: implement this properly
        return toString();
    }

    @Pure
    public @Nullable Number getNumber()
    {
        return number;
    }

    @Pure
    public Unit getUnit()
    {
        return unit;
    }

    @Pure
    public int getMinimumDecimalPlaces()
    {
        return minimumDecimalPlaces;
    }

    @SuppressWarnings("nullness")
    public static DisplayValue tuple(int rowIndex, List<DisplayValue> displays)
    {
        boolean allLoaded = true;
        double loadingProgress = 0;
        for (DisplayValue display : displays)
        {
            if (display.loading == -1)
            {
                loadingProgress += 1;
            }
            else
            {
                allLoaded = false;
                loadingProgress += display.loading;
            }
        }
        if (allLoaded)
        {
            return new DisplayValue(rowIndex, "(" + displays.stream().map(d -> d.show).collect(Collectors.joining(", ")) + ")", false);
        }
        else
        {
            return new DisplayValue(rowIndex, ProgressState.GETTING, loadingProgress / (double)displays.size());
        }
    }

    @SuppressWarnings("nullness")
    public static DisplayValue array(int rowIndex, List<DisplayValue> displays)
    {
        boolean allLoaded = true;
        double loadingProgress = 0;
        for (DisplayValue display : displays)
        {
            if (display.loading == -1)
            {
                loadingProgress += 1;
            }
            else
            {
                allLoaded = false;
                loadingProgress += display.loading;
            }
        }
        if (allLoaded)
        {
            return new DisplayValue(rowIndex, "[" + displays.stream().map(d -> d.show).collect(Collectors.joining(", ")) + "]", false);
        }
        else
        {
            return new DisplayValue(rowIndex, ProgressState.GETTING, loadingProgress / (double)displays.size());
        }
    }

    // The item showing text like "add row" at bottom of table
    public static DisplayValue getAddDataItem(int rowIndex)
    {
        return new DisplayValue(rowIndex);
    }

    public boolean isAddExtraRowItem()
    {
        return isAddExtraRowItem;
    }

    @Override
    @SuppressWarnings("nullness")
    public String toString()
    {
        if (loading == -1)
        {
            if (isAddExtraRowItem)
                return "";
            else if (show == null)
                return number.toString();
            else
                return show;
        }
        else
            return state.toString() + ": " + loading;
    }
}
