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

package test.gui.table;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import com.sun.javafx.tk.Toolkit;
import javafx.application.Platform;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.junit.Ignore;
import test.functions.TFunctionUtil;
import test.gui.TAppUtil;
import test.gui.TFXUtil;
import xyz.columnal.id.ColumnId;
import xyz.columnal.id.DataItemPosition;
import xyz.columnal.id.TableId;
import xyz.columnal.log.Log;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.runner.RunWith;
import xyz.columnal.data.*;
import xyz.columnal.data.Table.InitialLoadDetails;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.UserException;
import xyz.columnal.gui.EditImmediateColumnDialog.ColumnDetails;
import xyz.columnal.gui.MainWindow.MainWindowActions;
import xyz.columnal.gui.grid.RectangleBounds;
import xyz.columnal.gui.grid.VirtualGrid;
import xyz.columnal.gui.lexeditor.EditorDisplay;
import xyz.columnal.gui.table.TableDisplay;
import xyz.columnal.transformations.Aggregate;
import xyz.columnal.transformations.Calculate;
import xyz.columnal.transformations.Filter;
import xyz.columnal.transformations.ManualEdit;
import xyz.columnal.transformations.Sort;
import xyz.columnal.transformations.Sort.Direction;
import xyz.columnal.transformations.expression.Expression;
import xyz.columnal.transformations.expression.IdentExpression;
import xyz.columnal.transformations.expression.NumericLiteral;
import xyz.columnal.transformations.expression.type.TypeExpression;
import xyz.columnal.transformations.expression.visitor.ExpressionVisitorFlat;
import xyz.columnal.transformations.function.FunctionList;
import test.DummyManager;
import test.gen.GenColumnId;
import test.gen.GenRandom;
import test.gen.GenTableId;
import test.gen.type.GenDataTypeMaker;
import test.gen.type.GenDataTypeMaker.DataTypeAndValueMaker;
import test.gen.type.GenTypeAndValueGen;
import test.gen.type.GenTypeAndValueGen.TypeAndValueGen;
import test.gui.trait.ClickOnTableHeaderTrait;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.EnterColumnDetailsTrait;
import test.gui.trait.PopupTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.trait.TextFieldTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.function.simulation.SimulationFunction;
import xyz.columnal.utility.function.simulation.SimulationSupplier;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.Workers;
import xyz.columnal.utility.Workers.Priority;
import xyz.columnal.utility.gui.FXUtility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

@OnThread(value = Tag.FXPlatform, ignoreParent = true)
@RunWith(JUnitQuickcheck.class)
public class TestTableEdits extends FXApplicationTest implements ClickTableLocationTrait, EnterColumnDetailsTrait, TextFieldTrait, ScrollToTrait, PopupTrait, ClickOnTableHeaderTrait
{
    /*
     * We glue together all combinations of two transforms, from:
     *  - Sort
     *  - Filter
     *  - Calculate
     *  - ManualEdit
     * 
     * We also make some Concatenates and Aggregates
     */
    
    @OnThread(Tag.Any)
    private final List<Boolean> booleans = Arrays.asList(true, false, false);
    @OnThread(Tag.Any)
    private final List<Integer> numbers = Arrays.asList(5, 4, 3);
    
    @OnThread(Tag.Any)
    private final ImmutableList<Pair<ColumnId, Direction>> sortBy = ImmutableList.of(new Pair<>(new ColumnId("Boolean"), Direction.ASCENDING), new Pair<>(new ColumnId("Number"), Direction.DESCENDING));

    @SuppressWarnings("nullness")
    @OnThread(Tag.Any)
    private @NonNull VirtualGrid virtualGrid;

    @SuppressWarnings("nullness")
    @OnThread(Tag.Any)
    private @NonNull TableManager tableManager;

    @OnThread(Tag.Simulation)
    private final HashMap<TableId, BiConsumer<Table, ColumnId>> postRenameChecks = new HashMap<>();
    
    private final CellPosition originalTableTopLeft = new CellPosition(CellPosition.row(2), CellPosition.col(2));
    @OnThread(Tag.Simulation)
    private final HashMap<TableId, CellPosition> transformPositions = new HashMap<>();
    private final int originalRows = 3;
    private final int originalColumns = 2;
    @OnThread(Tag.Any)
    private int tableCount = 0;
    @SuppressWarnings("nullness")
    @OnThread(Tag.Any)
    private TableId srcId;

    @OnThread(Tag.Any)
    private Expression makeFilterCalcExpression(TypeManager typeManager)
    {
        // This is the original expression.  But to prevent type errors we adjust it to the below which
        // will only give runtime errors, not type-checker errors:
        //String src = "(column\\\\Number >= 4) & column\\\\Boolean";
        String src = //"@if (@call function\\\\type of(column\\\\Number) = type{Number}) & (@call function\\\\type of(column\\\\Boolean) = type{Boolean}) @then " +
            "(column\\\\Number >= @call function\\\\from text(\"4\")) & (column\\\\Boolean = @call function\\\\from text(\"true\"))"
            //+ "@else true @endif";
            ;
        try
        {
            return TFunctionUtil.parseExpression(src, typeManager, FunctionList.getFunctionLookup(typeManager.getUnitManager()));
        }
        catch (InternalException | UserException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void start(Stage _stage) throws Exception
    {
        super.start(_stage);
        Stage stage = windowToUse;
        final CompletableFuture<Optional<Throwable>> finish = new CompletableFuture<>();
        TableManager dummyManager = new DummyManager();
        Workers.onWorkerThread("Making tables", Priority.FETCH, () -> {
            try
            {
                SimulationFunction<RecordSet, EditableColumn> a = rs -> new MemoryBooleanColumn(rs, new ColumnId("Boolean"), booleans.stream().map(b -> Either.<String, Boolean>right(b)).collect(Collectors.toList()), false);
                SimulationFunction<RecordSet, EditableColumn> b = rs -> new MemoryNumericColumn(rs, new ColumnId("Number"), NumberInfo.DEFAULT, numbers.stream().map(n -> Either.<String, Number>right(n)).collect(Collectors.toList()), 6);
                ImmutableList<SimulationFunction<RecordSet, EditableColumn>> columns = ImmutableList.of(a, b);
                ImmediateDataSource src = new ImmediateDataSource(dummyManager, new InitialLoadDetails(null, null, originalTableTopLeft, null), new EditableRecordSet(columns, () -> 3));
                srcId = src.getId();
                dummyManager.record(src);
                
                addTransforms(dummyManager, srcId, 0, nextPos(src), Multimaps.newMultimap(new HashMap<>(), ArrayList::new), new Random(10L));
                tableCount = dummyManager.getAllTables().size();
                
                Supplier<MainWindowActions> supplier = TAppUtil.openDataAsTable(stage, dummyManager);
                new Thread(() -> {
                    MainWindowActions details = supplier.get();
                    this.tableManager = details._test_getTableManager();
                    virtualGrid = details._test_getVirtualGrid();
                    // Make columns thinner so everything fits on screen easily:
                    TFXUtil.fx_(() -> {
                        for (int c = 0; c < 12; c++)
                        {
                            virtualGrid._test_setColumnWidth(c, 60.0);
                        }
                    });
                    finish.complete(Optional.empty());
                    Platform.runLater(() -> Toolkit.getToolkit().exitNestedEventLoop(finish, finish));
                }).start();
            }
            catch (Throwable e)
            {
                Log.log(e);
                finish.complete(Optional.of(e));
                Platform.runLater(() -> com.sun.javafx.tk.Toolkit.getToolkit().exitNestedEventLoop(finish, finish));
            }
        });
        com.sun.javafx.tk.Toolkit.getToolkit().enterNestedEventLoop(finish);
        assertNull(finish.get(30, TimeUnit.SECONDS).orElse(null));
    }

    // Returns next target position
    // unavailableTables points from a table to all its directly derived tables that shouldn't be available to it 
    @SuppressWarnings("identifier")
    @OnThread(Tag.Simulation)
    private CellPosition addTransforms(TableManager dummyManager, TableId srcId, int depth, CellPosition targetPos, Multimap<TableId, TableId> unavailableTables, Random r) throws InternalException, UserException
    {
        TableId sortId = new TableId(ch(r) + srcId.getRaw() + " then Sort");
        Sort sort = new Sort(dummyManager, new InitialLoadDetails(sortId, null, targetPos, null), srcId, sortBy);
        dummyManager.record(sort);
        transformPositions.put(sortId, targetPos);
        targetPos = nextPos(sort);
        postRenameChecks.put(sortId, (t, newCol) -> {
            assertEquals(((Sort)t).getSortBy().get(0).getFirst(), newCol);
        });

        TableId filterId = new TableId(ch(r) + srcId.getRaw() + " then Filter");
        Filter filter = new Filter(dummyManager, new InitialLoadDetails(filterId, null, targetPos, null), srcId, makeFilterCalcExpression(dummyManager.getTypeManager()));
        dummyManager.record(filter);
        transformPositions.put(filterId, targetPos);
        targetPos = nextPos(filter);

        TableId calculateId = new TableId(ch(r) + srcId.getRaw() + " then Calculate");
        Calculate calc = new Calculate(dummyManager, new InitialLoadDetails(calculateId, null, targetPos, null), srcId, ImmutableMap.of(new ColumnId("Boolean"), IdentExpression.column(new ColumnId("Boolean"))));
        dummyManager.record(calc);
        transformPositions.put(calculateId, targetPos);
        targetPos = nextPos(calc);
        postRenameChecks.put(calculateId, (t, c) -> {
            assertEquals(IdentExpression.column(c), ((Calculate)t).getCalculatedColumns().values().iterator().next());
        });

        TableId manualId = new TableId(ch(r) + srcId.getRaw() + " then Edit");
        ManualEdit manualEdit = new ManualEdit(dummyManager, new InitialLoadDetails(manualId, null, targetPos, null), srcId, null, ImmutableMap.of());
        dummyManager.record(manualEdit);
        transformPositions.put(manualId, targetPos);
        targetPos = nextPos(manualEdit);

        TableId agg1Id = new TableId(ch(r) + srcId.getRaw() + " then Aggregate1");
        // Must have two columns to make counts right
        Aggregate agg1 = new Aggregate(dummyManager, new InitialLoadDetails(agg1Id, null, targetPos, null), srcId, ImmutableList.of(new Pair<>(new ColumnId("AggCol"), new NumericLiteral(0L, null))), ImmutableList.of(new ColumnId("Boolean")));
        dummyManager.record(agg1);
        transformPositions.put(agg1Id, targetPos);
        targetPos = nextPos(agg1);
        postRenameChecks.put(agg1Id, (t, newCol) -> {
            assertEquals(((Aggregate)t).getSplitBy().get(0), newCol);
        });

        TableId agg2Id = new TableId(ch(r) + srcId.getRaw() + " then Aggregate2");
        // Must have two columns to make counts right
        Aggregate agg2 = new Aggregate(dummyManager, new InitialLoadDetails(agg2Id, null, targetPos, null), srcId, ImmutableList.of(new Pair<>(new ColumnId("AggCol"), IdentExpression.column(new ColumnId("Boolean")))), ImmutableList.of(new ColumnId("Number")));
        dummyManager.record(agg2);
        transformPositions.put(agg2Id, targetPos);
        targetPos = nextPos(agg2);
        postRenameChecks.put(agg2Id, (t, newCol) -> {
            assertSameLastIdent(newCol, ((Aggregate)t).getColumnExpressions().get(0).getSecond());
        });
        
        // TODO concatenate some of the others

        ImmutableList<TableId> derived = ImmutableList.of(sortId, filterId, calculateId, manualId, agg1Id, agg2Id);
        for (TableId tableId : derived)
        {
            unavailableTables.put(srcId, tableId);
        }
        checkAvailability(dummyManager, unavailableTables);

        depth += 1;
        if (depth < 3)
        {
            for (TableId tableId : derived)
            {
                // Can't build on Aggregate as different columns: 
                if (!tableId.equals(agg1Id) && !tableId.equals(agg2Id))
                    targetPos = addTransforms(dummyManager, tableId, depth, targetPos, unavailableTables, r);
            }
        }
        
        return targetPos;
    }

    @OnThread(Tag.Any)
    private void assertSameLastIdent(ColumnId column, Expression expression)
    {
        assertEquals(column.getRaw(), expression.visit(new ExpressionVisitorFlat<@Nullable @ExpressionIdentifier String>()
        {
            @Override
            protected @Nullable @ExpressionIdentifier String makeDef(Expression expression)
            {
                return null;
            }

            @Override
            public @Nullable @ExpressionIdentifier String ident(IdentExpression self, @Nullable @ExpressionIdentifier String namespace, ImmutableList<@ExpressionIdentifier String> idents, boolean isVariable)
            {
                return idents.get(idents.size() - 1);
            }
        }));
    }

    @OnThread(Tag.Any)
    private String ch(Random r)
    {
        return "" + (char)('A' + (char)(r.nextInt(26)));
    }

    // unavailableTables points from a table to all its directly derived tables that shouldn't be available to it
    @OnThread(Tag.Simulation)
    private void checkAvailability(TableManager manager, Multimap<TableId, TableId> unavailableTables)
    {
        ImmutableList<TableId> allTables = manager.getAllTables().stream().map(t -> t.getId()).collect(ImmutableList.toImmutableList());
        // I think this is only O(N^2) but N is small anyway
        for (TableId tableId : allTables)
        {
            HashSet<TableId> available = new HashSet<>(allTables);
            removeAllMapped(unavailableTables, available, tableId);
            assertEquals(available, new HashSet<>(manager.getAllTablesAvailableTo(tableId, true).stream().map(t -> t.getId()).collect(Collectors.toList())));
            available.remove(tableId);
            assertEquals(available, new HashSet<>(manager.getAllTablesAvailableTo(tableId, false).stream().map(t -> t.getId()).collect(Collectors.toList())));
        }
    }

    @OnThread(Tag.Any)
    private void removeAllMapped(Multimap<TableId, TableId> unavailableTables, HashSet<TableId> available, TableId tableId)
    {
        for (TableId id : unavailableTables.get(tableId))
        {
            available.remove(id);
            removeAllMapped(unavailableTables, available, id);
        }
    }

    @OnThread(Tag.Simulation)
    private CellPosition nextPos(Table table) throws InternalException, UserException
    {
        CellPosition right = table._test_getPrevPosition().offsetByRowCols(0, 5);
        // Wrap beyond certain point:
        if (right.columnIndex > 20)
        {
            CellPosition below = new CellPosition(table._test_getPrevPosition().offsetByRowCols(8, 0).rowIndex, CellPosition.col(2));
            return below;
        }
        else
            return right;
    }

    @Property(trials = 3)
    @OnThread(Tag.Simulation)
    public void testRenameTable(@From(GenTableId.class) TableId newTableId) throws Exception
    {
        // N tables, 2 columns each:
        assertEquals(tableCount, count(".table-display-table-title"));
        assertEquals(tableCount * 2, count(".table-display-column-title"));
        RectangleBounds rectangleBounds = new RectangleBounds(originalTableTopLeft, originalTableTopLeft.offsetByRowCols(0, originalColumns));
        clickOnItemInBounds(".table-display-table-title .text-field", virtualGrid, rectangleBounds);
        selectAllCurrentTextField();
        write(newTableId.getRaw());
        // Different ways of exiting:

        // Click on right-hand end of table header:
        Bounds headerBox = TFXUtil.fx(() -> virtualGrid.getRectangleBoundsScreen(rectangleBounds));
        clickOn(new Point2D(headerBox.getMaxX() - 2, headerBox.getMinY() + 2));

        // Renaming involves thread hopping, so wait for a bit:
        TFXUtil.sleep(1000);

        assertEquals(ImmutableSet.copyOf(Utility.prependToList(newTableId.getRaw(), transformPositions.keySet().stream().map(t -> t.getRaw()).collect(ImmutableList.toImmutableList()))), tableManager.getAllTables().stream().map(t -> t.getId().getRaw()).sorted().collect(Collectors.toSet()));
        // Check we haven't amassed multiple tables during the rename re-runs:
        assertEquals(tableCount, count(".table-display-table-title"));
        assertEquals(tableCount * 2, count(".table-display-column-title"));
    }

    @Property(trials = 3)
    @OnThread(Tag.Simulation)
    public void testRenameColumn(@From(GenColumnId.class) ColumnId newColumnId) throws Exception
    {
        // 3 tables, 2 columns each:
        assertEquals(tableCount, count(".table-display-table-title"));
        assertEquals(tableCount * 2, count(".table-display-column-title"));
        RectangleBounds rectangleBounds = new RectangleBounds(originalTableTopLeft, originalTableTopLeft.offsetByRowCols(1, 0));
        clickOnItemInBounds(".table-display-column-title", virtualGrid, rectangleBounds);
        TFXUtil.sleep(1000);
        clickOn(".column-name-text-field");
        selectAllCurrentTextField();
        write(newColumnId.getRaw());
        clickOn(".ok-button");

        // Renaming involves thread hopping, so wait for a bit:
        TFXUtil.sleep(1000);

        // Fetch the sorted transformation and check the column names (checks rename, and propagation):
        for (Entry<TableId, CellPosition> entry : transformPositions.entrySet())
        {
            Table table = tableManager.getSingleTableOrThrow(entry.getKey());
            if (!(table instanceof Aggregate))
                assertEquals(entry.getKey().getRaw(), ImmutableSet.<ColumnId>of(new ColumnId("Number"), newColumnId), ImmutableSet.copyOf(table.getData().getColumnIds()));
        }

        for (Entry<TableId, BiConsumer<Table, ColumnId>> e : postRenameChecks.entrySet())
        {
            e.getValue().accept(tableManager.getSingleTableOrThrow(e.getKey()), newColumnId);
        }
        
        // Check we haven't amassed multiple tables during the rename re-runs:
        assertEquals(tableCount, count(".table-display-table-title"));
        assertEquals(tableCount * 2, count(".table-display-column-title"));
    }
    
    @Property(trials = 2)
    public void testDeleteColumn(@From(GenRandom.class) Random r) throws UserException, InternalException
    {
        // Pick a table and column:
        ImmutableList<Pair<TableId, ColumnId>> columns = tableManager.getAllTables().stream().<Pair<TableId, ColumnId>>flatMap(t -> {
            try
            {
                return t.getData().getColumnIds().stream().map(c -> new Pair<TableId, ColumnId>(t.getId(), c));
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }).collect(ImmutableList.<Pair<TableId, ColumnId>>toImmutableList());
        Pair<TableId, ColumnId> picked = columns.get(r.nextInt(columns.size()));
        assertNotNull(tableManager.getSingleTableOrThrow(picked.getFirst()).getData().getColumnOrNull(picked.getSecond()));
        // Scroll to it
        keyboardMoveTo(virtualGrid, tableManager, picked.getFirst(), picked.getSecond(), DataItemPosition.row(0));
        push(KeyCode.UP);
        push(KeyCode.UP);
        // Either keyboard or context menu:
        if (r.nextBoolean())
        {
            push(r.nextBoolean() ? KeyCode.BACK_SPACE : KeyCode.DELETE);
        }
        else
        {
            withItemInBounds(lookup(".column-title"), virtualGrid, virtualGrid._test_getSelection().orElseThrow(RuntimeException::new).getSelectionDisplayRectangle(), this::showContextMenu);
            lookup(".id-virtGrid-column-delete").tryQuery().ifPresent(this::clickOn);
        }
        
        sleep(500);
        Column columnAfter = tableManager.getSingleTableOrThrow(picked.getFirst()).getData().getColumnOrNull(picked.getSecond());
        if (tableManager.getSingleTableOrThrow(picked.getFirst()) instanceof ImmediateDataSource)
            assertNull(columnAfter);
        else
            assertNotNull(columnAfter); // Can't delete from sort
    }

    @Property(trials = 2)
    public void testDeleteTable(@From(GenRandom.class) Random r) throws UserException, InternalException
    {
        // Pick a table and column:
        Table picked = tableManager.getAllTables().get(r.nextInt(tableManager.getAllTables().size()));
        assertNotNull(tableManager.getSingleTableOrNull(picked.getId()));
        // Scroll to it
        keyboardMoveTo(virtualGrid, TBasicUtil.checkNonNull(picked.getDisplay()).getMostRecentPosition());
        // Either keyboard or context menu:
        if (r.nextBoolean())
        {
            push(r.nextBoolean() ? KeyCode.BACK_SPACE : KeyCode.DELETE);
        }
        else
        {
            triggerTableHeaderContextMenu(virtualGrid, tableManager, picked.getId());
            clickOn(".id-tableDisplay-menu-delete");
        }

        sleep(500);
        assertNull(tableManager.getSingleTableOrNull(picked.getId()));
        assertTrue("New button showing", lookup(".create-table-grid-button").tryQuery().isPresent());
        assertTrue("New button showing", lookup(".create-table-grid-button").query().isVisible());
    }

    @Ignore // TODO restore
    @Property(trials = 2, shrink = false)
    @OnThread(Tag.Simulation)
    public void testAddColumnAtRight(int n, @From(GenColumnId.class) ColumnId name, @From(GenTypeAndValueGen.class) TypeAndValueGen typeAndValueGen) throws InternalException, UserException
    {
        tableManager.getTypeManager()._test_copyTaggedTypesFrom(typeAndValueGen.getTypeManager());
        for (Table table : tableManager.getAllTables())
        {
            assertEquals(originalColumns, table.getData().getColumns().size());
        }
        int row = 1 + (Math.abs(n) % (originalRows + 2)); 
        clickOnItemInBounds(".expand-arrow", virtualGrid, new RectangleBounds(originalTableTopLeft.offsetByRowCols(row, 0), originalTableTopLeft.offsetByRowCols(row, originalColumns)));
        
        // We borrow n as a seed:
        ColumnDetails columnDetails = new ColumnDetails(null, name, typeAndValueGen.getType(), typeAndValueGen.makeValue());
        enterColumnDetails(columnDetails, new Random(n + 100));
        
        TFXUtil.sleep(500);
        
        // Check no tables moved:
        assertEquals(originalTableTopLeft, TFXUtil.tablePosition(tableManager, srcId));
        for (Entry<TableId, CellPosition> entry : transformPositions.entrySet())
        {
            assertEquals(entry.getValue(), TFXUtil.tablePosition(tableManager, entry.getKey()));
        }
        
        for (Table table : tableManager.getAllTables())
        {
            if (table instanceof Aggregate)
                continue;
            
            // Check that the column count is now right on all tables:
            List<Column> columns = table.getData().getColumns();
            assertEquals(originalColumns + 1, columns.size());
            // Check that the original two columns have the right names:
            assertEquals(new ColumnId("Boolean"), columns.get(0).getName());
            assertEquals(new ColumnId("Number"), columns.get(1).getName());
            // Check that the third column has right details:
            assertEquals(columnDetails.columnId, columns.get(2).getName());
            assertEquals(columnDetails.dataType, columns.get(2).getType().getType());
            TBasicUtil.assertValueEqual("", columns.get(2) instanceof EditableColumn ? columnDetails.defaultValue : null, columns.get(2).getDefaultValue());
        }
    }

    @Ignore // TODO restore
    @Property(trials=4, shrink = false)
    @OnThread(Tag.Simulation)
    public void testAddColumnBeforeAfter(
        @When(seed=3237919701795101471L)
        int positionIndicator,
        @When(seed=5841154326228353674L)
        @From(GenColumnId.class) ColumnId name,
        @When(seed=6562332032147499639L)
        @From(GenTypeAndValueGen.class) TypeAndValueGen typeAndValueGen) throws InternalException, UserException
    {
        tableManager.getTypeManager()._test_copyTaggedTypesFrom(typeAndValueGen.getTypeManager());
        // N tables, 2 columns each:
        assertEquals(tableCount, count(".table-display-table-title"));
        assertEquals(tableCount * 2, count(".table-display-column-title"));
        // 2 columns which you can add to, 3 rows plus 2 column headers:
        //assertEquals(originalColumns + originalRows + 2, lookup(".expand-arrow").queryAll().stream().filter(Node::isVisible).count());

        // If the position is negative, we use add-before.  If it's zero or positive, we use add-after.
        String targetColumnName = Arrays.asList("Boolean", "Number").get(Math.abs(positionIndicator) % originalColumns);
        // Bring up context menu and click item:
        RectangleBounds rectangleBounds = new RectangleBounds(originalTableTopLeft, originalTableTopLeft.offsetByRowCols(1, originalColumns));
        withItemInBounds(TFXUtil.fx(() -> lookup(".column-title").lookup((Label t) -> t.getText().equals(targetColumnName))), 
            virtualGrid, rectangleBounds,
                this::showContextMenu);
        clickOn(positionIndicator < 0 ? ".id-virtGrid-column-addBefore" : ".id-virtGrid-column-addAfter");

        // We borrow n as a seed:
        ColumnDetails columnDetails = new ColumnDetails(null, name, typeAndValueGen.getType(), typeAndValueGen.makeValue());
        enterColumnDetails(columnDetails, new Random(positionIndicator + 100));
        TFXUtil.sleep(500);

        // Should now be one more column in each table:
        ImmutableList<Table> allTables = tableManager.getAllTables();
        assertEquals(tableCount, allTables.size());
        assertEquals(tableCount * 3, TFXUtil.fx(() -> allTables.stream().mapToInt(t -> {
            @SuppressWarnings("nullness")
            @NonNull TableDisplay display = (TableDisplay) t.getDisplay();
            return display.getDisplayColumns().size();
        }).sum()).intValue());
        // One extra column:
        //assertEquals(originalColumns + 1 + originalRows + 2, lookup(".expand-arrow").queryAll().stream().filter(Node::isVisible).count());
        
        int newPosition = (Math.abs(positionIndicator) % originalColumns) + (positionIndicator < 0 ? 0 : 1);
        
        // Check that the existing columns are at right indexes:
        for (Table table : allTables)
        {
            // Check that the column count is now right on all tables:
            List<Column> columns = table.getData().getColumns();
            assertEquals(originalColumns + 1, columns.size());
            // Check that the original two columns have the right names:
            int pos = newPosition > 0 ? 0 : 1;
            assertEquals("Position: " + pos, new ColumnId("Boolean"), columns.get(pos).getName());
            pos = newPosition > 1 ? 1 : 2;
            assertEquals("Position " + pos, new ColumnId("Number"), columns.get(pos).getName());
            // Check that the new column has right details:
            assertEquals(columnDetails.columnId, columns.get(newPosition).getName());
            assertEquals(columnDetails.dataType, columns.get(newPosition).getType().getType());
            TBasicUtil.assertValueEqual("", columns.get(newPosition) instanceof EditableColumn ? columnDetails.defaultValue : null, columns.get(newPosition).getDefaultValue());
        }
    }
    
    @Ignore // TODO restore
    @Property(trials = 15)
    @OnThread(Tag.Simulation)
    public void testTableDrag(@From(GenRandom.class) Random r)
    {
        checkNoOverlap();
        // Pick a table:
        Table table = tableManager.getAllTables().get(r.nextInt(tableManager.getAllTables().size()));
        @SuppressWarnings("nullness")
        @NonNull TableDisplay tableDisplay = (TableDisplay) TFXUtil.fx(() -> table.getDisplay());
        
        CellPosition original = TFXUtil.fx(() -> tableDisplay.getPosition());
        keyboardMoveTo(virtualGrid, original.offsetByRowCols(-1, -1));
        CellPosition dest = original.offsetByRowCols(r.nextInt(4) - 1, r.nextInt(7) - 1);
        Rectangle2D start;
        BoundingBox windowBoundsOnScreen = TFXUtil.fx(() -> FXUtility.getWindowBounds(windowToUse));
        Rectangle2D end;
        int attempts = 0;
        do
        {
            sleep(400);
            start = TFXUtil.fx(() -> FXUtility.boundsToRect(virtualGrid.getRectangleBoundsScreen(new RectangleBounds(original, original))));
            end = TFXUtil.fx(() -> FXUtility.boundsToRect(virtualGrid.getRectangleBoundsScreen(new RectangleBounds(dest, dest))));
            final double scrollX;
            final double scrollY;
            if (end.getMinX() < windowBoundsOnScreen.getMinX())
            {
                scrollX = windowBoundsOnScreen.getMinX() - end.getMinX() - 50;
                scrollY = 0;
            }
            else if (end.getMaxX() >= windowBoundsOnScreen.getMaxX())
            {
                scrollX = end.getMaxX() - windowBoundsOnScreen.getMaxX() + 50;
                scrollY = 0;
            }
            else if (end.getMinY() < windowBoundsOnScreen.getMinY())
            {
                scrollX = 0;
                scrollY = windowBoundsOnScreen.getMinY() - end.getMinY() - 50;
            }
            else if (end.getMaxY() >= windowBoundsOnScreen.getMaxY())
            {
                scrollX = 0;
                scrollY = end.getMaxY() - windowBoundsOnScreen.getMaxY() + 50;
            }
            else
                break;
            TFXUtil.fx_(() -> virtualGrid.getScrollGroup().requestScrollBy(-scrollX, -scrollY));
        }
        while (++attempts < 10);
        assertTrue(attempts < 10);
        
        
        Point2D dragFrom = new Point2D(Math.round(start.getMinX() + 2), Math.round(start.getMinY() + 2));
        Point2D dragTo = new Point2D(Math.round(end.getMinX() + 2),  Math.round(end.getMinY() + 2));
        clickOn(dragFrom);
        //TestUtil.debugEventRecipient_(this, dragFrom, MouseEvent.DRAG_DETECTED, () -> {
            drag(dragFrom);
            dropTo(dragTo);
        //});
        
        
        sleep(1000);
        
        // Check table actually moved:
        if (dest.columnIndex >= original.columnIndex && dest.rowIndex >= original.rowIndex && !dest.equals(original))
            assertNotEquals("Dragged from " + dragFrom + " to " + dragTo, original, TFXUtil.fx(() -> tableDisplay.getPosition()));
        /*
        TextField tableNameTextField = (TextField)withItemInBounds(lookup(".table-name-text-field"), virtualGrid, new RectangleBounds(dest, dest), (n, p) -> {});
        assertNotNull(tableNameTextField);
        assertEquals(table.getId().getRaw(), TFXUtil.fx(() -> tableNameTextField.getText()));
        */
        // TODO check drag overlays
        checkNoOverlap();

    }

    @OnThread(Tag.Simulation)
    private void checkNoOverlap()
    {
        // Check tables aren't overlapping:
        ImmutableList<Table> allTables = tableManager.getAllTables();
        for (Table a : allTables)
        {
            RectangleBounds aBounds = TFXUtil.fx(() -> getBounds(a));
            for (Table b : allTables)
            {
                if (a != b)
                {
                    RectangleBounds bBounds = TFXUtil.fx(() -> getBounds(b));

                    assertFalse("Bounds " + a.getId() + aBounds + " and " + b.getId() + bBounds + " touching", aBounds.touches(bBounds));
                }
            }
        }

        Rectangle2D screenRect = Screen.getPrimary().getBounds();
        BoundingBox screenBounds = new BoundingBox(screenRect.getMinX(), screenRect.getMinY(), screenRect.getWidth(), screenRect.getHeight());
        
        // Check no cells overlap:
        // We don't test column headers because they can overlap through floating when scrolling.
        // Instead, just check fixed cell items:
        ImmutableList<Pair<Node, BoundingBox>> cells = TFXUtil.fx(() -> Stream.<Node>concat(
            lookup(".table-data-cell").match(Node::isVisible).<Node>queryAll().stream(),
            lookup(".expand-arrow").match(Node::isVisible).<Node>queryAll().stream()
        ).map(n -> new Pair<>(n, shave(n.localToScreen(n.getBoundsInLocal()))))
            // Put on-screen first as that's easier to debug:
            .sorted(Comparator.comparing(p -> !p.getSecond().intersects(screenBounds)))
            .collect(ImmutableList.toImmutableList()));

        for (Pair<Node, BoundingBox> a : cells)
        {
            for (Pair<Node, BoundingBox> b : cells)
            {
                if (a.getFirst() != b.getFirst())
                {
                    assertFalse(a.toString() + " is touching " + b.toString(), a.getSecond().intersects(b.getSecond()));
                }
            }
        }
    }

    // Removes 2 pixels from each side:
    private BoundingBox shave(Bounds bounds)
    {
        return new BoundingBox(bounds.getMinX() + 2, bounds.getMinY() + 2, bounds.getWidth() - 4, bounds.getHeight() - 4);
    }

    @SuppressWarnings("nullness")
    private RectangleBounds getBounds(Table table)
    {
        return new RectangleBounds(table.getDisplay().getMostRecentPosition(), table.getDisplay().getBottomRightIncl());
    }

    @Ignore // TODO restore
    @Property(trials = 3)
    @OnThread(Tag.Simulation)
    public void testChangeColumnType(@From(GenDataTypeMaker.class) GenDataTypeMaker.DataTypeMaker dataTypeMaker, @From(GenRandom.class) Random r) throws UserException, InternalException
    {
        DataTypeAndValueMaker swappedType = dataTypeMaker.makeType();
        tableManager.getTypeManager()._test_copyTaggedTypesFrom(dataTypeMaker.getTypeManager());
        boolean changeBoolean = r.nextBoolean(); // Otherwise, numeric B

        // Put a value of new type in as error before changing.
        @Value Object swappedValue = swappedType.makeValue();
        int swappedValueIndex = r.nextInt(originalRows);
        CellPosition swappedValuePos = originalTableTopLeft.offsetByRowCols(3 + swappedValueIndex, changeBoolean ? 0 : 1);
        TFXUtil.collapseAllTableHats(tableManager, virtualGrid);
        sleep(500);
        clickOnItemInBounds(".document-text-field", virtualGrid, new RectangleBounds(swappedValuePos, swappedValuePos));
        push(KeyCode.ENTER);
        enterStructuredValue(swappedType.getDataType(), swappedValue, r, true, true);
        push(KeyCode.ENTER);
        
        
        TFXUtil.collapseAllTableHats(tableManager, virtualGrid);
        assertNotShowing("Type editor", ".type-editor");
        clickOnItemInBounds(
            r.nextBoolean() ? ".table-display-column-title" : ".table-display-column-type",
            virtualGrid, new RectangleBounds(originalTableTopLeft.offsetByRowCols(1, changeBoolean ? 0 : 1), originalTableTopLeft.offsetByRowCols(2, changeBoolean ? 0 : 1))
        );
        sleep(300);
        assertShowing("Type editor", ".type-editor");
        sleep(300);
        clickOn(".type-editor");
        sleep(300);
        push(TFXUtil.ctrlCmd(), KeyCode.A);
        sleep(300);
        push(KeyCode.DELETE);
        sleep(300);
        // Clicking ok while blank type shouldn't work, dialog should stay showing:
        moveAndDismissPopupsAtPos(point(".ok-button"));
        clickOn();
        assertShowing("Type editor", ".type-editor");
        
        clickOn(".type-editor");
        sleep(300);
        enterType(TypeExpression.fromDataType(swappedType.getDataType()), r);
        // Should be fine this time with valid type:
        moveAndDismissPopupsAtPos(point(".ok-button"));
        clickOn();
        sleep(500);
        assertNotShowing("Type editor", ".type-editor");

        SimulationSupplier<DataSource> findOriginal = () -> tableManager.getAllTables().stream().filter(t -> t instanceof DataSource).map(t -> (DataSource)t).findFirst().orElseThrow(() -> new RuntimeException("Could not find data source"));

        // Check the type propagated to the transformations
        ColumnId changedColumnId = new ColumnId(changeBoolean ? "Boolean" : "Number");
        for (Table table : tableManager.getAllTables())
        {
            if (!(table instanceof Aggregate))
                assertEquals(table.getId().getRaw(), swappedType.getDataType(), table.getData().getColumn(changedColumnId).getType().getType());
        }
        
        // Work out what values we now expect in that column:
        List<Either<String, @Value Object>> expectedAfterChange;
        boolean sameTypeAfterSwap;
        if (changeBoolean)
        {
            // true/false never valid as another type so unless we changed to boolean, all errors:
            sameTypeAfterSwap = swappedType.getDataType().equals(DataType.BOOLEAN);
            if (sameTypeAfterSwap)
            {
                expectedAfterChange = Utility.<Boolean, Either<String, @Value Object>>mapList(booleans, b -> Either.<String, @Value Object>right(DataTypeUtility.value(b)));
            }
            else
            {
                expectedAfterChange = Utility.<Boolean, Either<String, @Value Object>>mapList(booleans, b -> Either.left(Boolean.toString(b)));
            }
        }
        else
        {
            // Numbers not valid unless we swapped to another numeric type:
            sameTypeAfterSwap = DataTypeUtility.isNumber(swappedType.getDataType());
            if (sameTypeAfterSwap)
            {
                expectedAfterChange = Utility.<Integer, Either<String, @Value Object>>mapList(numbers, n -> Either.right(DataTypeUtility.value(n)));
            }
            else
            {
                expectedAfterChange = Utility.<Integer, Either<String, @Value Object>>mapList(numbers, n -> Either.left(Integer.toString(n)));
            }
        }
        expectedAfterChange = new ArrayList<>(expectedAfterChange);
        expectedAfterChange.set(swappedValueIndex, Either.right(swappedValue));
        
        TBasicUtil.assertValueListEitherEqual("After first type change", expectedAfterChange, TBasicUtil.getAllCollapsedData(findOriginal.get().getData().getColumn(changedColumnId).getType(), originalRows));
        
        // Now change type back:
        TFXUtil.collapseAllTableHats(tableManager, virtualGrid);
        clickOnItemInBounds(
                r.nextBoolean() ? ".table-display-column-title" : ".table-display-column-type",
                virtualGrid, new RectangleBounds(originalTableTopLeft.offsetByRowCols(1, changeBoolean ? 0 : 1), originalTableTopLeft.offsetByRowCols(2, changeBoolean ? 0 : 1))
        );
        sleep(300);
        clickOn(".type-editor");
        MatcherAssert.assertThat("Clicked on: " + point(".type-editor").query(), getFocusOwner(), Matchers.instanceOf(EditorDisplay.class));
        sleep(300);
        push(TFXUtil.ctrlCmd(), KeyCode.A);
        sleep(300);
        push(KeyCode.DELETE);
        sleep(300);
        enterType(TypeExpression.fromDataType(changeBoolean ? DataType.BOOLEAN : DataType.NUMBER), r);
        moveAndDismissPopupsAtPos(point(".ok-button"));
        clickOn();
        sleep(500);
        
        // Now check the values:
        List<@NonNull Either<String, @Value Object>> expectedAtEnd = new ArrayList<>(changeBoolean ? Utility.<Boolean, Either<String, @Value Object>>mapList(booleans, b -> Either.right(DataTypeUtility.value(b))) : Utility.<Integer, Either<String, @Value Object>>mapList(numbers, n -> Either.right(DataTypeUtility.value(n))));
        expectedAtEnd.set(swappedValueIndex, sameTypeAfterSwap ? Either.<String, @Value Object>right(swappedValue) : Either.<String, @Value Object>left(DataTypeUtility.valueToString(swappedValue)));
        TBasicUtil.assertValueListEitherEqual("", expectedAtEnd, TBasicUtil.getAllCollapsedData(findOriginal.get().getData().getColumn(changedColumnId).getType(), booleans.size()));
    }
}
