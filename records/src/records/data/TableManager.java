package records.data;

import org.checkerframework.checker.nullness.qual.Nullable;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * Created by neil on 14/11/2016.
 */
public interface TableManager
{
    @OnThread(Tag.Any)
    public @Nullable Table getTable(TableId tableId);
}
