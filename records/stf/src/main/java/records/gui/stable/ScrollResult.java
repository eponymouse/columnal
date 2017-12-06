package records.gui.stable;

import utility.Pair;

public class ScrollResult
{
    public final double scrolledByPixels;
    public final Pair<Integer, Double> scrollPosition;

    public ScrollResult(double scrolledByPixels, Pair<Integer, Double> scrollPosition)
    {
        this.scrolledByPixels = scrolledByPixels;
        this.scrollPosition = scrollPosition;
    }
}
