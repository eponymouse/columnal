package xyz.columnal.gui.stable;

import org.checkerframework.checker.i18n.qual.LocalizableKey;
import org.checkerframework.checker.nullness.qual.Nullable;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.TableId;
import xyz.columnal.data.TableManager;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.Workers;
import xyz.columnal.utility.Workers.Priority;

public abstract class SimpleColumnOperation extends ColumnOperation
{
    private final TableManager tableManager;
    private final @Nullable TableId toRightOf;
    
    protected SimpleColumnOperation(TableManager tableManager, @Nullable TableId toRightOf, @LocalizableKey String nameKey)
    {
        super(nameKey);
        this.tableManager = tableManager;
        this.toRightOf = toRightOf;
    }

    // insertPosition is where you could put a new table.
    @OnThread(Tag.Simulation)
    public abstract void execute(CellPosition insertPosition);

    @Override
    public @OnThread(Tag.FXPlatform) void executeFX()
    {
        CellPosition insertPosition = tableManager.getNextInsertPosition(toRightOf);
        Workers.onWorkerThread(nameKey, Priority.SAVE, () -> execute(insertPosition));
    }

    @Override
    protected String[] getStyleClasses()
    {
        return new String[] {"recipe-menu-item"};
    }
}
