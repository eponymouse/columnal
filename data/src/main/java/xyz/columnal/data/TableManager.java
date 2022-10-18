/*
 * Columnal: Safer, smoother data table processing.
 * Copyright (c) Neil Brown, 2016-2020, 2022.
 *
 * This file is part of Columnal.
 *
 * Columnal is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * Columnal is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
 * more details.
 *
 * You should have received a copy of the GNU General Public License along 
 * with Columnal. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.columnal.data;

import annotation.units.AbsColIndex;
import annotation.units.AbsRowIndex;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import xyz.columnal.grammar.DisplayLexer;
import xyz.columnal.grammar.DisplayParser;
import xyz.columnal.grammar.GrammarUtility;
import xyz.columnal.grammar.MainLexer;
import xyz.columnal.grammar.MainLexer2;
import xyz.columnal.grammar.MainParser;
import xyz.columnal.grammar.MainParser2;
import xyz.columnal.grammar.TableLexer2;
import xyz.columnal.grammar.TableParser2;
import xyz.columnal.grammar.Versions;
import xyz.columnal.id.ColumnId;
import xyz.columnal.id.SaveTag;
import xyz.columnal.id.TableAndColumnRenames;
import xyz.columnal.id.TableId;
import xyz.columnal.log.ErrorHandler;
import xyz.columnal.log.Log;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.PolyNull;
import org.checkerframework.dataflow.qual.Pure;
import xyz.columnal.data.Table.BlankSaver;
import xyz.columnal.data.Table.Saver;
import xyz.columnal.data.Table.TableDisplayBase;
import xyz.columnal.data.TableOperations.RenameTable;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.UnitManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.grammar.DisplayParser.ColumnWidthContext;
import xyz.columnal.grammar.DisplayParser.GlobalDisplayDetailsContext;
import xyz.columnal.grammar.MainParser.CommentContext;
import xyz.columnal.grammar.MainParser.FileContext;
import xyz.columnal.grammar.MainParser.TableContext;
import xyz.columnal.grammar.MainParser.TopLevelItemContext;
import xyz.columnal.grammar.MainParser2.ContentContext;
import xyz.columnal.grammar.TableParser2.TableDataContext;
import xyz.columnal.grammar.TableParser2.TableTransformationContext;
import xyz.columnal.grammar.Versions.ExpressionVersion;
import xyz.columnal.grammar.Versions.MainVersion;
import xyz.columnal.grammar.Versions.OverallVersion;
import xyz.columnal.styled.StyledString;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.GraphUtility;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.function.simulation.SimulationConsumer;
import xyz.columnal.utility.function.simulation.SimulationFunctionInt;
import xyz.columnal.utility.TranslationUtility;
import xyz.columnal.utility.Utility;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Created by neil on 14/11/2016.
 */
@OnThread(Tag.Any)
public class TableManager
{
    private static final Random RANDOM = new Random();
    @OnThread(value = Tag.Any, requireSynchronized = true)
    private static @MonotonicNonNull Settings settings;
    
    // We use a TreeMap here to have reliable ordering for tables, especially when saving:
    @OnThread(value = Tag.Any,requireSynchronized = true)
    private final Map<TableId, Set<Table>> usedIds = new TreeMap<>();
    @OnThread(value = Tag.Any,requireSynchronized = true)
    private final Set<DataSource> sources = Sets.newIdentityHashSet();
    @OnThread(value = Tag.Any,requireSynchronized = true)
    private final Set<Transformation> transformations = Sets.newIdentityHashSet();
    private final List<GridComment> comments = new ArrayList<>();
    private final UnitManager unitManager;
    private final TypeManager typeManager;
    private final ArrayList<TableManagerListener> listeners = new ArrayList<>();
    private final TransformationLoader transformationLoader;
    private final PluggedContentHandler pluginManager;
    
    // R expressions are automatically run, which is dangerous as they could be modified by an external program, or especially
    // could be sent from an untrusted source then opened.  So we keep track of files we made and their hashes, and those files
    // are trusted.  Everything else is untrusted, which is done by banning R expressions during load, and keeping track of all
    // the banned ones.  They remain banned for the whole session until manually re-run or modified.
    @OnThread(Tag.Simulation)
    private boolean banningAllRExpressions = false;
    @OnThread(Tag.Simulation)
    private final HashSet<String> bannedRExpressions = new HashSet<>();

    public TableManager(TransformationLoader transformationLoader, PluggedContentHandler pluggedContentHandler) throws UserException, InternalException
    {
        this.transformationLoader = transformationLoader;
        this.unitManager = new UnitManager();
        this.typeManager = new TypeManager(unitManager);
        this.pluginManager = pluggedContentHandler;
    }
    
    private static final String SETTINGS_FILE_NAME = "settings.txt";

    public static synchronized Settings getSettings()
    {
        if (settings == null)
        {
            String pathToRExecutable = Utility.getProperty(SETTINGS_FILE_NAME, "pathToRExecutable");
            String useColumnalRLibs = Utility.getProperty(SETTINGS_FILE_NAME, "useColumnalRLibs");
            settings = new Settings(pathToRExecutable == null || pathToRExecutable.trim().isEmpty() ? null : new File(pathToRExecutable),
                // Default is true:
                useColumnalRLibs == null || "true".equals(useColumnalRLibs));
        }
        return settings;
    }

    public static synchronized void setSettings(Settings settings)
    {
        TableManager.settings = settings;
        // If we have a lot more properties, we may want to batch-set:
        Utility.setProperty(SETTINGS_FILE_NAME, "pathToRExecutable", settings.pathToRExecutable == null ? "" : settings.pathToRExecutable.getAbsolutePath());
        Utility.setProperty(SETTINGS_FILE_NAME, "useColumnalRLibs", Boolean.toString(settings.useColumnalRLibs));
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
            id = new TableId(IdentifierUtility.identNum("T", RANDOM.nextInt(1_000_000)));
        }
        while (usedIds.containsKey(id));
        // Reserve the spot for now:
        usedIds.put(id, Sets.newIdentityHashSet());
        return id;
    }

    @OnThread(Tag.Simulation)
    public synchronized <T extends Table> T record(T table)
    {
        Set<Table> tablesForId = usedIds.computeIfAbsent(table.getId(), x -> Sets.<Table>newIdentityHashSet());
        if (!tablesForId.isEmpty())
        {
            Log.logStackTrace("Adding duplicate table for table ID: " + table.getId());
        }
        tablesForId.add(table);
        if (table instanceof DataSource)
        {
            if (sources.add((DataSource) table))
                listeners.forEach(l -> l.addSource((DataSource) table));
        }
        else if (table instanceof Transformation)
        {
            if (transformations.add((Transformation) table))
                listeners.forEach(l -> l.addTransformation((Transformation) table));
        }

        // Re-run any dependents which might have been missing this table:
        try
        {
            this.<Table>editImpl(table.getId(), null, TableAndColumnRenames.EMPTY);
        }
        catch (InternalException e)
        {
            Log.log(e);
        }
        return table;
    }

    public UnitManager getUnitManager()
    {
        return unitManager;
    }

    public static OverallVersion detectVersion(String completeSrc) throws UserException
    {
        // Bit of manual parsing to find file version:
        Iterator<String> lines = Utility.<String>iterableStream(Pattern.compile("\\r?\\n").splitAsStream(completeSrc).map(s -> s.trim()).filter(s -> !s.isEmpty())).iterator();
        if (lines.hasNext())
        {
            String top = lines.next();
            if (top.equals("COLUMNAL") && lines.hasNext())
            {
                String version = lines.next();
                if (version.startsWith("VERSION"))
                {
                    try
                    {
                        int v = Integer.parseInt(version.substring("COLUMNAL".length()).trim());
                        switch (v)
                        {
                            case 1: return OverallVersion.ONE; 
                            case 2: return OverallVersion.TWO;
                            case 3: return OverallVersion.THREE;
                            default:
                                throw new UserException("Unknown file version: " + version + "; you may need to update Columnal to the latest version to read this file.");
                        }
                    }
                    catch (NumberFormatException e)
                    {
                        // Not a number, fall through to error
                    }
                }
            }
        }
        throw new UserException("Cannot locate file version; corrupt file or not a Columnal file?");
    }

    // Throws exception if not OK
    @OnThread(Tag.Simulation)
    public void checkROKToRun(String rExpression) throws UserException
    {
        // This is called while loading the R transformation, so if we're in banning mode, ban this one too:
        if (banningAllRExpressions)
        {
            bannedRExpressions.add(rExpression);
        }
        
        // It's ok if it's not explicitly banned
        if (bannedRExpressions.contains(rExpression))
        {
            throw new UserException("R expression in untrusted file; click the circular arrow above to run");
        }
    }

    @OnThread(Tag.Simulation)
    public void setBanAllR(boolean banAllR)
    {
        this.banningAllRExpressions = banAllR;
    }

    @OnThread(Tag.Simulation)
    public void unban(String rExpression)
    {
        bannedRExpressions.remove(rExpression);
    }

    @OnThread(Tag.Simulation)
    public boolean isBannedRExpression(String rExpression)
    {
        return bannedRExpressions.contains(rExpression);
    }

    public static class Loaded
    {
        public final ImmutableList<StyledString> errors;
        public final ImmutableList<? extends Table> loadedTables;
        public final ImmutableList<GridComment> gridComments;

        public Loaded(ImmutableList<StyledString> errors, ImmutableList<? extends Table> loadedTables, ImmutableList<GridComment> gridComments)
        {
            this.errors = errors;
            this.loadedTables = loadedTables;
            this.gridComments = gridComments;
        }

        @Override
        public boolean equals(@Nullable Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Loaded loaded = (Loaded) o;
            return errors.equals(loaded.errors) &&
                    loadedTables.equals(loaded.loadedTables) &&
                    gridComments.equals(loaded.gridComments);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(errors, loadedTables, gridComments);
        }
    }
    
    @OnThread(Tag.Simulation)
    public synchronized Loaded loadAll(String completeSrc,  SimulationConsumer<ImmutableList<Pair<Integer, Double>>> setColumnWidths) throws UserException, InternalException
    {
        OverallVersion version = detectVersion(completeSrc);
        ImmutableList.Builder<Table> loaded = ImmutableList.builder();
        ImmutableList.Builder<StyledString> errors = ImmutableList.builder();
        List<Exception> exceptions = new ArrayList<>();
        if (Versions.getMainVersion(version) == MainVersion.ONE)
        {
            FileContext file = Utility.parseAsOne(completeSrc, MainLexer::new, MainParser::new, p -> p.file());
            unitManager.clearAllUser();
            unitManager.loadUserUnits(Utility.getDetail(file.units().detail()));
            typeManager.clearAllUser();
            typeManager.loadTypeDecls(Utility.getDetail(file.types().detail()));
            List<TableId> ids = new ArrayList<>(usedIds.keySet());
            for (TableId id : ids)
            {
                remove(id);
            }
            

            for (TopLevelItemContext topLevelItemContext : file.topLevelItem())
            {
                if (topLevelItemContext.table() != null)
                    loadOneTable(topLevelItemContext.table(), Versions.getExpressionVersion(version)).either_(exceptions::add, loaded::add);
                if (topLevelItemContext.comment() != null)
                    loadComment(SaveTag.generateRandom(), topLevelItemContext.comment()).either_(exceptions::add, c -> {
                        comments.add(c);
                        listeners.forEach(l -> l.addComment(c));
                    });
            }

            if (file.display() != null)
            {
                String displayDetail = Utility.getDetail(file.display().detail());
                parseDisplayDetail(setColumnWidths, displayDetail);
            }
        }
        else if (Versions.getMainVersion(version) == MainVersion.TWO)
        {
            MainParser2.FileContext file = Utility.parseAsOne(completeSrc, MainLexer2::new, MainParser2::new, p -> p.file());
            
            HashMap<String, SimulationConsumer<Pair<SaveTag, String>>> contentHandlers = new HashMap<>();
            typeManager.clearAllUser();
            unitManager.clearAllUser();
            List<TableId> ids = new ArrayList<>(usedIds.keySet());
            for (TableId id : ids)
            {
                remove(id);
            }
            contentHandlers.put("TYPES", tagAndContent -> {
                typeManager.loadTypeDecls(tagAndContent.getSecond());
            });
            contentHandlers.put("UNITS", tagAndContent -> {
                unitManager.loadUserUnits(tagAndContent.getSecond());
            });
            
            contentHandlers.put("DATA", tagAndContent -> {
                Table data = loadOneDataTable(tagAndContent);
                loaded.add(data);
            });
            
            contentHandlers.put("TRANSFORMATION", tagAndContent -> {
                Transformation trans = loadOneTransformation(tagAndContent, Versions.getExpressionVersion(version));
                loaded.add(trans);
            });
            
            contentHandlers.put("COMMENT", tagAndContent -> {
                loadComment(tagAndContent.getFirst(), Utility.<TableParser2.CommentContext, TableParser2>parseAsOne(tagAndContent.getSecond(), TableLexer2::new, TableParser2::new, p -> p.comment())).either_(exceptions::add, c -> {
                    comments.add(c);
                    listeners.forEach(l -> l.addComment(c));
                });
            });
            contentHandlers.put("DISPLAY", tagAndContent -> {
                parseDisplayDetail(setColumnWidths, tagAndContent.getSecond());
            });

            for (Entry<String, SimulationConsumer<Pair<SaveTag, String>>> e : pluginManager.getHandledContent(errors::add).entrySet())
            {
                if (contentHandlers.putIfAbsent(e.getKey(), e.getValue()) != null)
                {
                    errors.add(StyledString.s("Plugin error: handled content " + e.getKey() + " clashes with built-in content."));
                }
            }
            
            for (ContentContext contentContext : file.content())
            {
                String contentType = contentContext.ATOM(0).getText();
                SimulationConsumer<Pair<SaveTag, String>> handler = contentHandlers.get(contentType);
                if (handler == null)
                {
                    // TODO give warning, but keep content for writing again
                }
                else
                {
                    try
                    {
                        handler.consume(new Pair<>(new SaveTag(contentContext.detail()), Utility.getDetail(contentContext.detail())));
                    }
                    catch (Exception e)
                    {
                        exceptions.add(e);
                    }
                }
            }
        }
        else
        {
            throw new UserException("Unknown file version: " + version + "; corrupt file or not a Columnal file?");
        }

        if (exceptions.isEmpty())
            return new Loaded(errors.build(), loaded.build(), ImmutableList.copyOf(comments));
        else if (exceptions.get(0) instanceof UserException)
            throw new UserException("Loading problem", exceptions.get(0));
        else if (exceptions.get(0) instanceof InternalException)
            throw new InternalException("Loading problem", exceptions.get(0));
        else
            throw new InternalException("Unrecognised exception", exceptions.get(0));
    }

    @OnThread(Tag.Simulation)
    private Transformation loadOneTransformation(Pair<SaveTag, String> tagAndContent, ExpressionVersion expressionVersion) throws InternalException, UserException
    {
        TableTransformationContext trans = Utility.parseAsOne(tagAndContent.getSecond(), TableLexer2::new, TableParser2::new, p -> p.tableTransformation());
        return transformationLoader.loadOne(this, tagAndContent.getFirst(), trans, expressionVersion);
    }

    @OnThread(Tag.Simulation)
    private Table loadOneDataTable(Pair<SaveTag, String> tagAndContent) throws InternalException, UserException
    {
        TableDataContext table = Utility.parseAsOne(tagAndContent.getSecond(), TableLexer2::new, TableParser2::new, p -> p.tableData());
        return DataSource.loadOne(this, tagAndContent.getFirst(), table);
    }

    @OnThread(Tag.Simulation)
    public void parseDisplayDetail(SimulationConsumer<ImmutableList<Pair<Integer, Double>>> setColumnWidths, String displayDetail) throws InternalException, UserException
    {
        ImmutableList.Builder<Pair<Integer, Double>> widths = ImmutableList.builder();
        GlobalDisplayDetailsContext displayDetails = Utility.parseAsOne(displayDetail, DisplayLexer::new, DisplayParser::new, p -> p.globalDisplayDetails());
        for (ColumnWidthContext columnWidthContext : displayDetails.columnWidth())
        {
            try
            {
                int columnIndex = Integer.parseInt(columnWidthContext.item(0).getText());
                double columnWidth = Double.parseDouble(columnWidthContext.item(1).getText());
                widths.add(new Pair<>(columnIndex, columnWidth));
            }
            catch (NumberFormatException e)
            {
                // Not a big deal, log but don't worry user
                Log.log(e);
            }
        }
        setColumnWidths.consume(widths.build());
    }

    @OnThread(Tag.Simulation)
    public Either<Exception, GridComment> loadComment(SaveTag saveTag, CommentContext commentContext) throws UserException, InternalException
    {
        String comment = GrammarUtility.processEscapes(Utility.getDetail(commentContext.detail()).trim(), false);
        
        int[] items = Utility.parseAsOne(Utility.getDetail(commentContext.display().detail()), DisplayLexer::new, DisplayParser::new, p -> {
            try
            {
                return p.commentDisplayDetails().item().stream().mapToInt(i -> Integer.parseInt(i.getText())).toArray();
            }
            catch (NumberFormatException e)
            {
                throw new UserException("Problem parsing comment position", e);
            }
        });
        
        return Either.right(new GridComment(saveTag, comment, new CellPosition(items[1] * AbsRowIndex.ONE, items[0] * AbsColIndex.ONE), items[2], items[3]));
    }

    @OnThread(Tag.Simulation)
    public Either<Exception, GridComment> loadComment(SaveTag saveTag, TableParser2.CommentContext commentContext) throws UserException, InternalException
    {
        String comment = GrammarUtility.processEscapes(Utility.getDetail(commentContext.detail()).trim(), false);

        int[] items = Utility.parseAsOne(Utility.getDetail(commentContext.display().detail()), DisplayLexer::new, DisplayParser::new, p -> {
            try
            {
                return p.commentDisplayDetails().item().stream().mapToInt(i -> Integer.parseInt(i.getText())).toArray();
            }
            catch (NumberFormatException e)
            {
                throw new UserException("Problem parsing comment position", e);
            }
        });

        return Either.right(new GridComment(saveTag, comment, new CellPosition(items[1] * AbsRowIndex.ONE, items[0] * AbsColIndex.ONE), items[2], items[3]));
    }


    @OnThread(Tag.Simulation)
    public Either<Exception, Table> loadOneTable(TableContext tableContext, ExpressionVersion expressionVersion)
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
                return Either.right(transformationLoader.loadOne(this, tableContext, expressionVersion));
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
    public void save(@Nullable File destination, Saver saver) //throws InternalException, UserException
    {
        unitManager.save().forEach(saver::saveUnit);
        typeManager.save().forEach(saver::saveType);
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


        List<TablesWithSameId> ordered = GraphUtility.<TablesWithSameId>lineariseDAG(values.values(), incomingEdges, Collections.<TablesWithSameId>emptyList());
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

        for (GridComment comment : comments)
        {
            comment.save(saver);
        }
    }
    
    public synchronized ImmutableList<Table> getAllTablesAvailableTo(@Nullable TableId tableId, boolean includeSelf)
    {
        // Sources are always available.  Transformations require that they do not
        // (transitively) depend on us.
        Map<TableId, Collection<TableId>> edges = new HashMap<>();
        HashSet<TableId> allIds = new HashSet<>();
        fetchIdsAndEdges(edges, allIds);
        List<TableId> linearised = GraphUtility.<@NonNull TableId>lineariseDAG(allIds, edges, tableId == null ? ImmutableList.<@NonNull TableId>of() : ImmutableList.<@NonNull TableId>of(tableId));
        int lastIndexIncl = tableId != null ? linearised.indexOf(tableId) : linearised.size() - 1;
        if (lastIndexIncl == -1)
            lastIndexIncl = linearised.size() - 1;
        // Shouldn't be any nulls but must satisfy type checker:
        return Utility.filterOutNulls(linearised.subList(0, lastIndexIncl + 1)
            .stream()
            .filter(id -> includeSelf || !id.equals(tableId))
            .<@Nullable Table>map(t -> getSingleTableOrNull(t)))
            .collect(ImmutableList.<Table>toImmutableList());
    }

    public synchronized ImmutableList<Table> getAllTables()
    {
        return Stream.<Table>concat(sources.stream(), transformations.stream()).collect(ImmutableList.<Table>toImmutableList());
    }

    /**
     * Finds a suitable position for a table inserted to the
     * right of the given position, or at the far right-hand end
     */
    @OnThread(Tag.FXPlatform)
    public CellPosition getNextInsertPosition(@Nullable TableId toRightOf)
    {
        @Nullable TableDisplayBase toRightOfDisplay = toRightOf == null ? null : Optional.ofNullable(getSingleTableOrNull(toRightOf)).map(Table::getDisplay).orElse(null);
        if (toRightOfDisplay == null)
        {
            return Utility.filterOutNulls(getAllTables().stream().<@Nullable TableDisplayBase>map(t -> t.getDisplay())).map(d -> getTopRight(d)).max(Comparator.comparing(p -> p.columnIndex)).orElse(CellPosition.ORIGIN).offsetByRowCols(1, 1);
        }
        else
        {
            // We usually leave a blank space to the right
            // of the table, unless there's another table beginning in that column:
            @Nullable TableDisplayBase tableToRight = toRightOfDisplay;
            do
            {
                toRightOfDisplay = tableToRight;
                @AbsColIndex int nextCol = toRightOfDisplay.getBottomRightIncl().offsetByRowCols(0, 1).columnIndex;
                tableToRight = getAllTables().stream().<@Nullable TableDisplayBase>map(t -> t.getDisplay()).filter(d -> d != null && d.getMostRecentPosition().columnIndex == nextCol).findFirst().orElse(null);
            }
            while (tableToRight != null);
            
            return getTopRight(toRightOfDisplay).offsetByRowCols(0, 1);
        }
    }

    @OnThread(Tag.FXPlatform)
    private CellPosition getTopRight(TableDisplayBase display)
    {
        return new CellPosition(display.getMostRecentPosition().rowIndex, display.getBottomRightIncl().columnIndex);
    }

    @OnThread(Tag.Simulation)
    public void addComment(GridComment comment)
    {
        comments.add(comment);
        listeners.forEach(l -> l.addComment(comment));
    }

    @OnThread(Tag.Simulation)
    public void removeComment(GridComment comment)
    {
        comments.remove(comment);
        listeners.forEach(l -> l.removeComment(comment));
    }

    public static interface TableMaker<T extends Table>
    {
        @OnThread(Tag.Simulation)
        @NonNull T make() throws InternalException;
    }

    @OnThread(Tag.Simulation)
    public <@NonNull T extends Table> T edit(Table oldTable, SimulationFunctionInt<TableId, T> makeReplacement, RenameOnEdit renameOnEdit) throws InternalException
    {
        TableId newTableId = renameOnEdit.rename(oldTable);
        return editImpl(oldTable.getId(), () -> makeReplacement.apply(newTableId), oldTable.getId().equals(newTableId) ? TableAndColumnRenames.EMPTY : new TableAndColumnRenames(ImmutableMap.of(oldTable.getId(), new Pair<>(newTableId, ImmutableMap.of()))));
    }

    @OnThread(Tag.Simulation)
    public void reRun(Table table) throws InternalException
    {
        this.<Table>editImpl(table.getId(), null, TableAndColumnRenames.EMPTY);
    }

    @OnThread(Tag.Simulation)
    public <@NonNull T extends Table> @PolyNull T editData(@Nullable TableId affectedTableId, @PolyNull TableMaker<T> makeReplacement, TableAndColumnRenames renames) throws InternalException
    {
        return editImpl(affectedTableId, makeReplacement, renames);
    }

    /**
     * When you edit a table, we must update all dependent tables.  The way we do this
     * is to work out what all the dependent tables *are*, then save them as a script.
     * Then all dependent tables are removed, and the edited table is replaced.
     * The saved scripts are then re-run with the new data (which may mean they
     * contain errors where they did not before)
     *
     * @param affectedTableId The TableId which is affected, i.e. the table for which all dependents will need to be re-run/
     *                        If makeReplacement is non-null, this table will be removed
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
    private <T extends @NonNull Table> @PolyNull T editImpl(@Nullable TableId affectedTableId, @PolyNull TableMaker<T> makeReplacement, TableAndColumnRenames renames) throws InternalException
    {
        HashSet<TableId> affected = new HashSet<>();
        // If it is null, new table, so nothing should be affected:
        if (affectedTableId != null)
            affected.add(affectedTableId);
        Map<TableId, Collection<TableId>> edges = new HashMap<>();
        HashSet<TableId> allIds = new HashSet<>();
        fetchIdsAndEdges(edges, allIds);
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
                if (!affected.contains(linearised.get(i)) || (makeReplacement == null && renames.isRenamingTableId(linearised.get(i))))
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

        @SuppressWarnings("nullness")
        @PolyNull T newTable = null;
        if (makeReplacement != null)
        {
            synchronized (this)
            {
                if (affectedTableId != null)
                    removeAndSerialise(affectedTableId, new Table.BlankSaver(), renames);
            }
            newTable = makeReplacement.make();
            record(newTable);
        }

        savedToReRun.thenAccept(ss -> {
            reAddAll(ss);
        });
        
        return newTable;
    }

    public void fetchIdsAndEdges(Map<TableId, Collection<TableId>> edges, HashSet<TableId> allIds)
    {
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
    }

    /**
     * Removes the given table, saving a script to reproduce it
     * in the given Saver.
     */
    @OnThread(Tag.Simulation)
    private void removeAndSerialise(TableId tableId, @Nullable Saver then, TableAndColumnRenames renames)
    {
        //Log.normalStackTrace("Removing table " + tableId + (then == null ? " permanently" : " as part of edit"), 3);
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
                {
                    tables.removeIf(t -> t == removedFinal);
                    if (tables.isEmpty())
                        usedIds.remove(tableId);
                }
            }
            for (TableManagerListener l : listeners)
            {
                l.removeTable(removed, remainingCount);
            }
            if (then != null)
                removed.save(null, then, renames);
        }
    }
    
    // Removes the table from the data and from the display.
    @OnThread(Tag.Simulation)
    public void remove(TableId tableId)
    {
        removeAndSerialise(tableId, new BlankSaver() {
            @Override
            public @OnThread(Tag.Simulation) void saveTable(String tableSrc)
            {
                // Re-run dependents:
                try
                {
                    TableManager.this.<Table>editImpl(tableId, null, TableAndColumnRenames.EMPTY /* no rename when removing */);
                }
                catch (InternalException e)
                {
                    Log.log(e);
                }
            }
        }, TableAndColumnRenames.EMPTY);
    }

    /**
     * Re-runs the list of scripts to re-insert a set of transformations.
     *
     * They will be re-run in order, so it is important to pass them in
     * an order where each item only depends on those before it in the list.
     */
    @OnThread(Tag.Simulation)
    private void reAddAll(List<String> scripts)
    {
        for (String script : scripts)
        {
            Log.debug("Reloading:\n" + script);
            ErrorHandler.getErrorHandler().alertOnError_(TranslationUtility.getString("error.rerunning.transformations"), () -> {
                ContentContext ctxt = Utility.parseAsOne(script, MainLexer2::new, MainParser2::new, (MainParser2 p) -> p.content());
                if (ctxt.ATOM(0).getText().equals("DATA"))
                    loadOneDataTable(new Pair<SaveTag, String>(new SaveTag(ctxt.detail()), Utility.getDetail(ctxt.detail())));
                else if (ctxt.ATOM(0).getText().equals("TRANSFORMATION"))
                    loadOneTransformation(new Pair<>(new SaveTag(ctxt.detail()), Utility.getDetail(ctxt.detail())), ExpressionVersion.latest());
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
            ErrorHandler.getErrorHandler().alertOnError_(TranslationUtility.getString("error.renaming.table"), () -> {
                this.<Table>editImpl(table.getId(), null, new TableAndColumnRenames(ImmutableMap.of(table.getId(), new Pair<@Nullable TableId, ImmutableMap<ColumnId, ColumnId>>(newName, ImmutableMap.of()))));
            });
        };
    }
    
    public void addListener(TableManagerListener listener)
    {
        listeners.add(listener);
    }

    public static interface TableManagerListener
    {
        public void removeTable(Table t, int tablesRemaining);

        public void addSource(DataSource dataSource);

        public void addTransformation(Transformation transformation);
        
        public void addComment(GridComment gridComment);
        
        public void removeComment(GridComment gridComment);
    }

    public static interface TransformationLoader
    {
        @OnThread(Tag.Simulation)
        public Transformation loadOne(TableManager mgr, TableContext table, ExpressionVersion expressionVersion) throws UserException, InternalException;

        @OnThread(Tag.Simulation)
        public Transformation loadOne(TableManager mgr, SaveTag saveTag, TableTransformationContext table, ExpressionVersion expressionVersion) throws UserException, InternalException;
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
