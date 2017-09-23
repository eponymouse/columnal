package records.data;

import com.google.common.collect.Sets;
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
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.GraphUtility;
import utility.SimulationSupplier;
import utility.Utility;

import java.io.File;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by neil on 14/11/2016.
 */
@OnThread(Tag.Any)
public class TableManager
{
    // We use a TreeMap here to have reliable ordering for tables, especially when saving:
    @OnThread(value = Tag.Any,requireSynchronized = true)
    private final Map<TableId, Set<Table>> usedIds = new TreeMap<>();
    @OnThread(value = Tag.Any,requireSynchronized = true)
    private final Set<DataSource> sources = Sets.newIdentityHashSet();
    @OnThread(value = Tag.Any,requireSynchronized = true)
    private final Set<Transformation> transformations = Sets.newIdentityHashSet();
    private final UnitManager unitManager;
    private final TypeManager typeManager;
    private final TableManagerListener listener;
    private final TransformationLoader transformationLoader;

    public TableManager(TransformationLoader transformationLoader, TableManagerListener listener) throws UserException, InternalException
    {
        this.transformationLoader = transformationLoader;
        this.listener = listener;
        this.unitManager = new UnitManager();;
        this.typeManager = new TypeManager(unitManager);
    }

    @Pure
    public synchronized @Nullable Table getSingleTableOrNull(TableId tableId)
    {
        Set<Table> tables = usedIds.get(tableId);
        if (tables != null && tables.size() == 1)
            return tables.iterator().next();
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
    @OnThread(Tag.Simulation)
    public synchronized TableId registerNextFreeId()
    {
        TableId id;
        // So GUID is very unlikely to be in use already, but no harm in checking:
        do
        {
            id = new TableId("Auto-" + UUID.randomUUID().toString());
        }
        while (usedIds.containsKey(id));
        // Reserve the spot for now:
        usedIds.put(id, Sets.newIdentityHashSet());
        return id;
    }

    @OnThread(Tag.Simulation)
    public synchronized void record(Table table)
    {
        usedIds.computeIfAbsent(table.getId(), x -> Sets.newIdentityHashSet()).add(table);
        if (table instanceof DataSource)
        {
            if (sources.add((DataSource)table))
                listener.addSource((DataSource) table);
        }
        else if (table instanceof Transformation)
        {
            if (transformations.add((Transformation)table))
                listener.addTransformation((Transformation)table);
        }
    }

    public UnitManager getUnitManager()
    {
        return unitManager;
    }

    @OnThread(Tag.Simulation)
    public List<Table> loadAll(String completeSrc) throws UserException, InternalException
    {
        FileContext file = Utility.parseAsOne(completeSrc, MainLexer::new, MainParser::new, p -> p.file());
        // TODO load units
        typeManager.loadTypeDecls(file.types());
        List<Table> loaded = new ArrayList<>();
        List<Exception> exceptions = new ArrayList<>();
        // TODO Don't need future any more now that this is all one thread
        CompletableFuture<Object> allDone = new CompletableFuture<>();
        int total = file.table().size();
        for (TableContext tableContext : file.table())
        {
            if (tableContext.dataSource() != null)
            {
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
            }
            else if (tableContext.transformation() != null)
            {
                try
                {
                    loaded.add(transformationLoader.loadOne(this, tableContext));
                }
                catch (InternalException | UserException e)
                {
                    Utility.log(e);
                    exceptions.add(e);
                }
                if (loaded.size() + exceptions.size() == total)
                    allDone.complete(new Object());
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

    @OnThread(Tag.Simulation)
    public void save(@Nullable File destination, Saver saver) throws InternalException, UserException
    {
        // TODO save units
        saver.saveType(typeManager.save());
        Map<TableId, TablesWithSameId> values = new HashMap<>();
        // Deep copy:
        synchronized (this)
        {
            for (Entry<TableId, Set<Table>> tables : usedIds.entrySet())
            {
                values.put(tables.getKey(), new TablesWithSameId(tables.getKey(), tables.getValue()));
            }
        }

        Map<@NonNull TablesWithSameId, List<TablesWithSameId>> incomingEdges = new HashMap<>();
        for (TablesWithSameId tablesWithSameId : values.values())
        {
            for (Table table : tablesWithSameId.tables)
            {
                if (table instanceof Transformation)
                {
                    for (TableId srcId : ((Transformation) table).getSources())
                    {
                        TablesWithSameId dest = values.get(srcId);
                        if (dest != null)
                        {
                            List<TablesWithSameId> incoming = incomingEdges.computeIfAbsent(dest, k -> new ArrayList<>());
                            incoming.add(tablesWithSameId);
                        }
                    }
                }
            }
        }


        List<TablesWithSameId> ordered = GraphUtility.lineariseDAG(values.values(), incomingEdges, Collections.emptyList());
        // lineariseDAG makes all edges point forwards, but we want them pointing backwards
        // so reverse:
        Collections.reverse(ordered);
        for (TablesWithSameId tables : ordered)
        {
            for (Table table : tables.tables)
            {
                table.save(destination, saver);
            }
        }
    }

    public synchronized List<Table> getAllTables()
    {
        return Stream.<Table>concat(sources.stream(), transformations.stream()).collect(Collectors.<Table>toList());
    }

    /**
     * When you edit a table, we must update all dependent tables.  The way we do this
     * is to work out what all the dependent tables *are*, then save them as a script.
     * Then all dependent tables are removed, and the edited table is replaced.
     * The saved scripts are then re-run with the new data (which may mean they
     * contain errors where they did not before)
     *
     * @param affectedTableId The TableId which is affected, i.e. the table for which all dependents will need to be re-run
     * @param makeReplacement If null, existing table will be
     *                        left untouched and only its dependents re-run.  If non-null,
     *                        the table will be replaced by output
     *                        of this supplier.
     *
     * @throws InternalException
     * @throws UserException
     */
    @OnThread(Tag.Simulation)
    public void edit(@Nullable TableId affectedTableId, @Nullable SimulationSupplier<? extends Table> makeReplacement) throws InternalException, UserException
    {
        Map<TableId, List<TableId>> edges = new HashMap<>();
        HashSet<TableId> affected = new HashSet<>();
        // If it is null, new table, so nothing should be affected:
        if (affectedTableId != null)
            affected.add(affectedTableId);
        HashSet<TableId> allIds = new HashSet<>();
        synchronized (this)
        {
            for (Table t : sources)
                allIds.add(t.getId());
            for (Transformation t : transformations)
            {
                allIds.add(t.getId());
                edges.put(t.getId(), t.getSources());
            }
        }
        allIds.addAll(affected);
        List<TableId> linearised = GraphUtility.lineariseDAG(allIds, edges, affected);

        // Find first affected:
        int processFrom = affected.stream().mapToInt(o -> linearised.indexOf(o)).min().orElse(-1);
        // If it's not in affected itself, serialise it:
        List<String> reRun = new ArrayList<>();
        AtomicInteger toSave = new AtomicInteger(1); // Keep one extra until we've lined up all jobs
        CompletableFuture<List<String>> savedToReRun = new CompletableFuture<>();

        if (processFrom != -1)
        {
            for (int i = processFrom; i < linearised.size(); i++)
            {
                // Don't include the original changed transformation itself:
                if (!affected.contains(linearised.get(i)))
                {
                    // Add job:
                    toSave.incrementAndGet();
                    removeAndSerialise(linearised.get(i), new Table.BlankSaver()
                    {
                        // Ignore types and units because they are all already loaded
                        @Override
                        public @OnThread(Tag.Simulation) void saveTable(String script)
                        {
                            reRun.add(script);
                            if (toSave.decrementAndGet() == 0)
                            {
                                // Saved all of them
                                savedToReRun.complete(reRun);
                            }
                        }
                    });
                }
            }
        }
        if (toSave.decrementAndGet() == 0) // Remove extra; can complete now when hits zero
        {
            savedToReRun.complete(reRun);
        }

        if (makeReplacement != null)
        {
            synchronized (this)
            {
                if (affectedTableId != null)
                    removeAndSerialise(affectedTableId, new Table.BlankSaver());
            }
            record(makeReplacement.get());
        }

        savedToReRun.thenAccept(ss -> {
            Utility.alertOnError_(() -> reAddAll(ss));
        });
    }

    /**
     * Removes the given table, saving a script to reproduce it
     * in the given Saver.
     */
    @OnThread(Tag.Simulation)
    private void removeAndSerialise(TableId tableId, Saver then)
    {
        Table removed = null;
        synchronized (this)
        {
            for (Iterator<DataSource> iterator = sources.iterator(); iterator.hasNext(); )
            {
                Table t = iterator.next();
                if (t.getId().equals(tableId))
                {
                    iterator.remove();
                    removed = t;
                    break;
                }
            }
            if (removed == null)
                for (Iterator<Transformation> iterator = transformations.iterator(); iterator.hasNext(); )
                {
                    Transformation t = iterator.next();
                    if (t.getId().equals(tableId))
                    {
                        iterator.remove();

                        removed = t;
                        break;
                    }
                }
        }
        if (removed != null)
        {
            listener.removeTable(removed);
            removed.save(null, then);
        }
    }

    /**
     * Re-runs the list of scripts to re-insert a set of transformations.
     *
     * They will be re-run in order, so it is important to pass them in
     * an order where each item only depends on those before it in the list.
     */
    @OnThread(Tag.Simulation)
    private void reAddAll(List<String> scripts) throws UserException, InternalException
    {
        for (String script : scripts)
        {
            Utility.alertOnError_(() -> {
                Transformation transformation = transformationLoader.loadOne(this, script);
            });

        }
    }

    /**
     * Is the given id available?
     * @param tableId
     * @param similarIds If you pass a non-null list, it will be filled with any ids
     *                   deemed similar (e.g. differ only in case)
     * @return
     */
    public synchronized boolean isFreeId(TableId tableId, @Nullable List<TableId> similarIds)
    {
        if (similarIds != null)
        {
            for (TableId t : usedIds.keySet())
            {
                if (t.getRaw().toLowerCase().equals(tableId.getRaw().toLowerCase()))
                    similarIds.add(t);
            }
        }
        return !usedIds.containsKey(tableId);
    }

    public static interface TableManagerListener
    {
        public void removeTable(Table t);

        public void addSource(DataSource dataSource);

        public void addTransformation(Transformation transformation);
    }

    public static interface TransformationLoader
    {
        @OnThread(Tag.Simulation)
        public Transformation loadOne(TableManager mgr, String source) throws InternalException, UserException;

        @OnThread(Tag.Simulation)
        public Transformation loadOne(TableManager mgr, TableContext table) throws UserException, InternalException;
    }

    private static class TablesWithSameId
    {
        private final TableId id;
        private final List<Table> tables;

        public TablesWithSameId(TableId id, Collection<Table> tables)
        {
            this.id = id;
            this.tables = new ArrayList<>(tables);
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TablesWithSameId that = (TablesWithSameId) o;

            return id.equals(that.id);
        }

        @Override
        public int hashCode()
        {
            return id.hashCode();
        }
    }
}
