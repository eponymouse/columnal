package records.gui.stable;

import org.checkerframework.checker.i18n.qual.LocalizableKey;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Workers;
import utility.Workers.Priority;

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
        Workers.onWorkerThread(nameKey, Priority.SAVE, this::execute);
    }
}
