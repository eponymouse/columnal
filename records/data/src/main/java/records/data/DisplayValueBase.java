package records.data;

import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Because of the way TableView works, we have to send the string
 * through a DisplayValueBase type before it can be stored, even
 * though really DisplayValue was intended only for display, not for entry.
 *
 * So DisplayValue is the "real" DisplayValue type, and DisplayValueBase
 * only really exists to allow EnteredDisplayValue to pass through in the
 * other direction.
 */
@OnThread(Tag.FX)
public abstract class DisplayValueBase
{
    /**
     * The row index (starting at zero) from which this value comes.
     */
    private final int rowIndex;

    @OnThread(Tag.Any)
    public DisplayValueBase(int rowIndex)
    {
        this.rowIndex = rowIndex;
    }

    @OnThread(Tag.Any)
    public int getRowIndex()
    {
        return rowIndex;
    }

    public abstract String getEditString();
}
