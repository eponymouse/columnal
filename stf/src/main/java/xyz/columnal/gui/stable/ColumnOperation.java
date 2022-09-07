package records.gui.stable;

import javafx.scene.control.MenuItem;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Workers;
import xyz.columnal.utility.Workers.Priority;
import xyz.columnal.utility.gui.GUI;

/**
 * Created by neil on 29/05/2017.
 */
public abstract class ColumnOperation
{
    @OnThread(Tag.Any)
    protected final @LocalizableKey String nameKey;

    protected ColumnOperation(@LocalizableKey String nameKey)
    {
        this.nameKey = nameKey;
    }

    @OnThread(Tag.FXPlatform)
    public final MenuItem makeMenuItem()
    {
        return GUI.menuItem(nameKey, () -> executeFX(), getStyleClasses());
    }

    protected String[] getStyleClasses()
    {
        return new String[0];
    }

    @OnThread(Tag.FXPlatform)
    public abstract void executeFX();
}
