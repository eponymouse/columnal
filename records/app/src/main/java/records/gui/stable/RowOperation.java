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
interface RowOperation
{
    @OnThread(Tag.FXPlatform)
    public @LocalizableKey String getNameKey();

    @OnThread(Tag.Simulation)
    public void execute();

    @OnThread(Tag.FXPlatform)
    default public MenuItem makeMenuItem()
    {
        return GUI.menuItem(getNameKey(), () -> Workers.onWorkerThread(getNameKey(), Priority.SAVE_ENTRY, this::execute));
    }
}
