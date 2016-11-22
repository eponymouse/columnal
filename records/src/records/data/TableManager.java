package records.data;

import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import records.error.UserException;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by neil on 14/11/2016.
 */
@OnThread(Tag.Any)
public class TableManager
{
    @OnThread(value = Tag.Any,requireSynchronized = true)
    private int nextId = 1;
    @OnThread(value = Tag.Any,requireSynchronized = true)
    private final Map<TableId, List<Table>> usedIds = new HashMap<>();

    public synchronized @Nullable Table getSingleTableOrNull(TableId tableId)
    {
        List<Table> tables = usedIds.get(tableId);
        if (tables != null && tables.size() == 1)
            return tables.get(0);
        else
            return null;
    }

    // Generates a new unused ID and registers it.
    public synchronized TableId getNextFreeId(@UnknownInitialization(Object.class) Table table)
    {
        TableId id;
        do
        {
            id = new TableId("T" + nextId);
            nextId += 1;
        }
        while (usedIds.containsKey(id));
        record(table, id);
        return id;
    }

    // We are given initialising table, but we store as if it is initialised:
    @SuppressWarnings("initialization")
    private synchronized void record(@UnknownInitialization(Object.class) Table table, TableId id)
    {
        usedIds.computeIfAbsent(id, x -> new ArrayList<>()).add(table);
    }

    // Throws a UserException if already in use, otherwise registers it as used.
    public synchronized void registerId(TableId tableId, @UnknownInitialization(Object.class) Table table)
    {
        record(table, tableId);
    }

}
