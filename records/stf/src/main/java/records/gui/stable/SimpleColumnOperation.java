package records.gui.stable;

import javafx.scene.control.MenuItem;
import org.checkerframework.checker.i18n.qual.LocalizableKey;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Workers;
import utility.Workers.Priority;
import utility.gui.GUI;

public abstract class SimpleColumnOperation extends ColumnOperation
{
    protected SimpleColumnOperation(@LocalizableKey String nameKey)
    {
        super(nameKey);
    }

    @OnThread(Tag.Simulation)
    public abstract void execute();

    @Override
    protected @OnThread(Tag.FXPlatform) void executeFX()
    {
        Workers.onWorkerThread(nameKey, Priority.SAVE_ENTRY, this::execute);
    }
}
