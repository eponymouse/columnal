package records.data;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import log.Log;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.Pure;
import records.data.Table.Saver;
import records.data.TableOperations.RenameColumn;
import records.data.TableOperations.RenameTable;
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
import utility.Either;
import utility.FXPlatformConsumer;
import utility.GraphUtility;
import utility.Pair;
import utility.SimulationConsumer;
import utility.SimulationSupplier;
import utility.Utility;
import utility.gui.FXUtility;

import java.io.File;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by neil on 14/11/2016.
 */
@OnThread(Tag.Any)
public class TableManager
{
    private static final Random RANDOM = new Random();
    
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
            id = new TableId("T" + RANDOM.nextInt(1_000_000));
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
        int total = file.table().size();
        for (TableContext tableContext : file.table())
        {
            loadOneTable(tableContext).either_(exceptions::add, loaded::add);
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

    @OnThread(Tag.Simulation)
    public Either<Exception, Table> loadOneTable(TableContext tableContext)
    {
        if (tableContext.dataSource() != null)
        {
            try
            {
                return Either.right(DataSource.loadOne(this, tableContext));
            }
            catch (InternalException | UserException e)
            {
                Log.log(e);
                return Either.left(e);
            }
        }
        else if (tableContext.transformation() != null)
        {
            try
            {
                return Either.right(transformationLoader.loadOne(this, tableContext));
            }
            catch (InternalException | UserException e)
            {
                Log.log(e);
                return Either.left(e);
            }
        }
        return Either.left(new UserException("Unknown table type"));
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
            for (Entry<@KeyFor("usedIds") TableId, Set<Table>> tables : usedIds.entrySet())
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
                        // Avoid any that have a circular edge:
                        if (dest != null && !srcId.equals(tablesWithSameId.id))
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
                table.save(destination, saver, TableAndColumnRenames.EMPTY);
            }
        }
    }

    // Faster than getAllTables if you only need to process a stream.
    public synchronized Stream<Table> streamAllTables()
    {
        return Stream.<Table>concat(sources.stream(), transformations.stream());
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
     *                        left untouched (apart from renames) and only its dependents re-run.  If non-null,
     *                        the table will be replaced by output
     *                        of this supplier (and its dependents re-run).
     * @param renames         The tables to rename, and columns within them to rename if you use a reference to them.
     *
     * @throws InternalException
     * @throws UserException
     */
    @OnThread(Tag.Simulation)
    public void edit(@Nullable TableId affectedTableId, @Nullable SimulationSupplier<? extends Table> makeReplacement, @Nullable TableAndColumnRenames renames) throws InternalException, UserException
    {
        if (renames == null)
            renames = TableAndColumnRenames.EMPTY;
        
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
                // Don't include the original changed transformation itself, unless it got renamed:
                if (!affected.contains(linearised.get(i)) || renames.isRenamingTableId(linearised.get(i)))
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
                    }, renames);
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
                    removeAndSerialise(affectedTableId, new Table.BlankSaver(), renames);
            }
            record(makeReplacement.get());
        }

        savedToReRun.thenAccept(ss -> {
            FXUtility.alertOnError_(() -> reAddAll(ss));
        });
    }

    /**
     * Removes the given table, saving a script to reproduce it
     * in the given Saver.
     */
    @OnThread(Tag.Simulation)
    private void removeAndSerialise(TableId tableId, @Nullable Saver then, TableAndColumnRenames renames)
    {
        Log.normalStackTrace("Removing table " + tableId + (then == null ? " permanently" : " as part of edit"), 3);
        Table removed = null;
        int remainingCount;
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
            // Otherwise check transformations:
            if (removed == null)
            {
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

            remainingCount = sources.size() + transformations.size();
        }
        if (removed != null)
        {
            Table removedFinal = removed;
            synchronized (this)
            {
                Set<Table> tables = usedIds.get(tableId);
                if (tables != null)
                    tables.removeIf(t -> t == removedFinal);
            }
            listener.removeTable(removed, remainingCount);
            if (then != null)
                removed.save(null, then, renames);
        }
    }
    
    // Removes the table from the data and from the display.
    @OnThread(Tag.Simulation)
    public void remove(TableId tableId)
    {
        removeAndSerialise(tableId, null, TableAndColumnRenames.EMPTY);
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
            Log.debug("Reloading:\n" + script);
            FXUtility.alertOnError_(() -> {
                loadOneTable(Utility.parseAsOne(script, MainLexer::new, MainParser::new, p -> p.table()));
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

    public RenameTable getRenameTableOperation(Table table)
    {
        return newName -> {
            FXUtility.alertOnError_(() -> {
                edit(table.getId(), null, new TableAndColumnRenames(ImmutableMap.of(table.getId(), new Pair<@Nullable TableId, ImmutableMap<ColumnId, ColumnId>>(newName, ImmutableMap.of()))));
            });
        };
    }

    public RenameColumn getRenameColumnOperation(Table table, ColumnId oldColumnId)
    {
        return newColumnId -> {
            FXUtility.alertOnError_(() -> {
                edit(table.getId(), null, new TableAndColumnRenames(ImmutableMap.of(table.getId(), new Pair<@Nullable TableId, ImmutableMap<ColumnId, ColumnId>>(null, ImmutableMap.of(oldColumnId, newColumnId)))));
            });
        };
    }

    public static interface TableManagerListener
    {
        public void removeTable(Table t, int tablesRemaining);

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
