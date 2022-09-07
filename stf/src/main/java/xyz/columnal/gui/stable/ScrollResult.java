package records.gui.stable;

import xyz.columnal.utility.Pair;

public class ScrollResult<T extends Integer>
{
    public final double scrolledByPixels;
    public final Pair<T, Double> scrollPosition;

    public ScrollResult(double scrolledByPixels, Pair<T, Double> scrollPosition)
    {
        this.scrolledByPixels = scrolledByPixels;
        this.scrollPosition = scrollPosition;
    }
}
