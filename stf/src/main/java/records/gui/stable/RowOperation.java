package records.gui.stable;

import javafx.scene.control.MenuItem;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Workers;
import utility.Workers.Priority;
import utility.gui.GUI;

/**
 * Created by neil on 29/05/2017.
 */
abstract class RowOperation
{
    @OnThread(Tag.Any)
    private final @LocalizableKey String nameKey;

    protected RowOperation(@LocalizableKey String nameKey)
    {
        this.nameKey = nameKey;
    }

    @OnThread(Tag.Simulation)
    public abstract void execute();

    @OnThread(Tag.FXPlatform)
    public MenuItem makeMenuItem()
    {
        return GUI.menuItem(nameKey, () -> Workers.onWorkerThread(nameKey, Priority.SAVE, this::execute));
    }
}
