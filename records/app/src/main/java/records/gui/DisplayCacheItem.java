package records.gui;

import javafx.beans.property.SimpleObjectProperty;
import records.data.DisplayValue;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * A display cache.  This is an FX thread item which has a value to display,
 * and knows which index it was calculated from in its parent column.  See
 * DisplayCache class.
 */
@OnThread(Tag.FXPlatform)
public class DisplayCacheItem
{
    public final int index;
    public final SimpleObjectProperty<DisplayValue> display;

    public DisplayCacheItem(int index, SimpleObjectProperty<DisplayValue> display)
    {
        this.index = index;
        this.display = display;
    }
}
