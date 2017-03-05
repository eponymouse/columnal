package records.data;

import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.Table.Saver;
import records.data.datatype.TypeManager;
import records.data.unit.UnitManager;
import records.error.InternalException;
import records.error.UserException;
import records.grammar.MainLexer;
import records.grammar.MainParser;
import records.grammar.MainParser.FileContext;
import records.grammar.MainParser.TableContext;
import records.transformations.TransformationManager;
import records.transformations.expression.TypeState;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Utility;
import utility.Workers;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Created by neil on 14/11/2016.
 */
@OnThread(Tag.Any)
public class TableManager
{
    @OnThread(value = Tag.Any,requireSynchronized = true)
    private final Map<TableId, List<Table>> usedIds = new HashMap<>();
    private final UnitManager unitManager;
    private final TypeManager typeManager;

    public TableManager() throws UserException, InternalException
    {
        this.unitManager = new UnitManager();;
        this.typeManager = new TypeManager(unitManager);
    }

    @Pure
    public synchronized @Nullable Table getSingleTableOrNull(TableId tableId)
    {
        List<Table> tables = usedIds.get(tableId);
        if (tables != null && tables.size() == 1)
            return tables.get(0);
        else
            return null;
    }

    @Pure
    public Table getSingleTableOrThrow(TableId tableId) throws UserException
    {
        @Nullable Table t = getSingleTableOrNull(tableId);
        if (t == null)
            throw new UserException("Could not find table \"" + tableId + "\"");
        return t;
    }

    // Generates a new unused ID and registers it.
    public synchronized TableId getNextFreeId(@UnknownInitialization(Object.class) Table table)
    {
        TableId id;
        // So GUID is very unlikely to be in use already, but no harm in checking:
        do
        {
            id = new TableId("Auto-" + UUID.randomUUID().toString());
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

    public UnitManager getUnitManager()
    {
        return unitManager;
    }

    public TypeState getTypeState()
    {
        return new TypeState(unitManager, typeManager);
    }

    @OnThread(Tag.FXPlatform)
    public List<Table> loadAll(String completeSrc) throws UserException, InternalException
    {
        FileContext file = Utility.parseAsOne(completeSrc, MainLexer::new, MainParser::new, p -> p.file());
        // TODO load units
        typeManager.loadTypeDecls(file.types());
        List<Table> loaded = new ArrayList<>();
        List<Exception> exceptions = new ArrayList<>();
        CompletableFuture<Object> allDone = new CompletableFuture<>();
        int total = file.table().size();
        for (TableContext tableContext : file.table())
        {
            // No race hazards here because worker thread will serialise:
            if (tableContext.dataSource() != null)
            {
                Workers.onWorkerThread("Loading table", () -> {
                    try
                    {
                        loaded.add(DataSource.loadOne(this, tableContext));
                    }
                    catch (InternalException | UserException e)
                    {
                        Utility.log(e);
                        exceptions.add(e);
                    }
                    if (loaded.size() + exceptions.size() == total)
                        allDone.complete(new Object());
                });
            }
            else if (tableContext.transformation() != null)
            {
                Workers.onWorkerThread("Loading table", () -> {
                    try
                    {
                        loaded.add(TransformationManager.getInstance().loadOne(this, tableContext));
                    }
                    catch (InternalException | UserException e)
                    {
                        Utility.log(e);
                        exceptions.add(e);
                    }
                    if (loaded.size() + exceptions.size() == total)
                        allDone.complete(new Object());
                });
            }
        }
        try
        {
            allDone.get();
        }
        catch (InterruptedException | ExecutionException e)
        {
            Utility.log(e);
        }

        if (exceptions.isEmpty())
            return loaded;
        else if (exceptions.get(0) instanceof UserException)
            throw new UserException("Loading problem", exceptions.get(0));
        else if (exceptions.get(0) instanceof InternalException)
            throw new InternalException("Loading problem", exceptions.get(0));
        else
            throw new InternalException("Unrecognised exception", exceptions.get(0));
    }

    public TypeManager getTypeManager()
    {
        return typeManager;
    }

    @OnThread(Tag.FXPlatform)
    public void save(@Nullable File destination, Saver saver) throws InternalException, UserException
    {
        // TODO save units
        typeManager.save(saver);
        List<List<Table>> values = new ArrayList<>();
        // Deep copy:
        synchronized (this)
        {
            for (List<Table> tables : usedIds.values())
            {
                values.add(new ArrayList<>(tables));
            }
        }
        for (List<Table> tables : values)
        {
            for (Table table : tables)
            {
                table.save(destination, saver);
            }
        }
    }

    public synchronized Collection<Table> getAllTables()
    {
        return usedIds.values().stream().flatMap(Collection::stream).collect(Collectors.<@NonNull Table>toList());
    }
}
