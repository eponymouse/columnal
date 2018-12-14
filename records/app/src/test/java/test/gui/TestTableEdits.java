package test.gui;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.stage.Stage;
import log.Log;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.runner.RunWith;
import org.testfx.framework.junit.ApplicationTest;
import records.data.CellPosition;
import records.data.Column;
import records.data.ColumnId;
import records.data.EditableColumn;
import records.data.EditableRecordSet;
import records.data.ImmediateDataSource;
import records.data.MemoryBooleanColumn;
import records.data.MemoryNumericColumn;
import records.data.RecordSet;
import records.data.Table;
import records.data.Table.InitialLoadDetails;
import records.data.TableId;
import records.data.TableManager;
import records.data.datatype.NumberInfo;
import records.error.InternalException;
import records.error.UserException;
import records.gui.EditImmediateColumnDialog.ColumnDetails;
import records.gui.MainWindow.MainWindowActions;
import records.gui.grid.RectangleBounds;
import records.gui.grid.VirtualGrid;
import records.transformations.Sort;
import records.transformations.Sort.Direction;
import test.DummyManager;
import test.TestUtil;
import test.gen.GenColumnId;
import test.gen.GenTableId;
import test.gen.GenTypeAndValueGen;
import test.gen.GenTypeAndValueGen.TypeAndValueGen;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.SimulationFunction;
import utility.Workers;
import utility.Workers.Priority;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

@OnThread(value = Tag.FXPlatform, ignoreParent = true)
@RunWith(JUnitQuickcheck.class)
public class TestTableEdits extends FXApplicationTest implements ClickTableLocationTrait, EnterColumnDetailsTrait, TextFieldTrait
{
    @SuppressWarnings("nullness")
    @OnThread(Tag.Any)
    private @NonNull VirtualGrid virtualGrid;

    @SuppressWarnings("nullness")
    @OnThread(Tag.Any)
    private @NonNull TableManager tableManager;
    
    private final CellPosition originalTableTopLeft = new CellPosition(CellPosition.row(1), CellPosition.col(2));
    private final CellPosition transformTopLeft = new CellPosition(CellPosition.row(3), CellPosition.col(6));
    private final int originalRows = 3;
    private final int originalColumns = 2;
    @OnThread(Tag.Any)
    private final CompletableFuture<Optional<Exception>> finish = new CompletableFuture<>();
    @SuppressWarnings("nullness")
    @OnThread(Tag.Any)
    private TableId srcId;


    @Override
    public void start(Stage stage) throws Exception
    {
        super.start(stage);
        TableManager dummyManager = new DummyManager();
        Workers.onWorkerThread("Making tables", Priority.FETCH, () -> {
            try
            {
                SimulationFunction<RecordSet, EditableColumn> a = rs -> new MemoryBooleanColumn(rs, new ColumnId("A"), ImmutableList.of(true, false, false), false);
                SimulationFunction<RecordSet, EditableColumn> b = rs -> new MemoryNumericColumn(rs, new ColumnId("B"), NumberInfo.DEFAULT, ImmutableList.of(5, 4, 3), 6);
                ImmutableList<SimulationFunction<RecordSet, EditableColumn>> columns = ImmutableList.of(a, b);
                ImmediateDataSource src = new ImmediateDataSource(dummyManager, new InitialLoadDetails(null, originalTableTopLeft, null), new EditableRecordSet(columns, () -> 3));
                srcId = src.getId();
                dummyManager.record(src);
                Sort sort = new Sort(dummyManager, new InitialLoadDetails(new TableId("Sorted"), transformTopLeft, null), src.getId(), ImmutableList.of(new Pair<>(new ColumnId("B"), Direction.ASCENDING), new Pair<>(new ColumnId("A"), Direction.DESCENDING)));
                dummyManager.record(sort);
                @OnThread(Tag.Simulation) Supplier<MainWindowActions> supplier = TestUtil.openDataAsTable(stage, dummyManager);
                new Thread(() -> {
                    MainWindowActions details = supplier.get();
                    this.tableManager = details._test_getTableManager();
                    virtualGrid = details._test_getVirtualGrid();
                    finish.complete(Optional.empty());
                    Platform.runLater(() -> com.sun.javafx.tk.Toolkit.getToolkit().exitNestedEventLoop(finish, finish));
                }).start();
            }
            catch (Exception e)
            {
                Log.log(e);
                finish.complete(Optional.of(e));
                Platform.runLater(() -> com.sun.javafx.tk.Toolkit.getToolkit().exitNestedEventLoop(finish, finish));
            }
        });
        com.sun.javafx.tk.Toolkit.getToolkit().enterNestedEventLoop(finish);
        //assertNull(finish.get(5, TimeUnit.SECONDS).orElse(null));
    }
    
    @Property(trials = 3)
    @OnThread(Tag.Simulation)
    public void testRenameTable(@From(GenTableId.class) TableId newTableId) throws Exception
    {
        // 2 tables, 2 columns:
        assertEquals(2, lookup(".table-display-table-title").queryAll().size());
        assertEquals(4, lookup(".table-display-column-title").queryAll().size());
        RectangleBounds rectangleBounds = new RectangleBounds(originalTableTopLeft, originalTableTopLeft.offsetByRowCols(0, originalColumns));
        clickOnItemInBounds(lookup(".table-display-table-title .text-field"), virtualGrid, rectangleBounds);
        selectAllCurrentTextField();
        write(newTableId.getRaw());
        // Different ways of exiting:

        // Click on right-hand end of table header:
        Bounds headerBox = TestUtil.fx(() -> virtualGrid.getRectangleBoundsScreen(rectangleBounds));
        clickOn(new Point2D(headerBox.getMaxX() - 2, headerBox.getMinY() + 2));

        // Renaming involves thread hopping, so wait for a bit:
        TestUtil.sleep(1000);

        assertEquals(ImmutableSet.of("Sorted", newTableId.getRaw()), tableManager.getAllTables().stream().map(t -> t.getId().getRaw()).sorted().collect(Collectors.toSet()));
        // Check we haven't amassed multiple tables during the rename re-runs:
        assertEquals(2, lookup(".table-display-table-title").queryAll().size());
        assertEquals(4, lookup(".table-display-column-title").queryAll().size());
    }

    @Property(trials = 3)
    @OnThread(Tag.Simulation)
    public void testRenameColumn(@From(GenColumnId.class) ColumnId newColumnId) throws Exception
    {
        // 2 tables, 2 columns:
        assertEquals(2, lookup(".table-display-table-title").queryAll().size());
        assertEquals(4, lookup(".table-display-column-title").queryAll().size());
        RectangleBounds rectangleBounds = new RectangleBounds(originalTableTopLeft, originalTableTopLeft.offsetByRowCols(1, 0));
        clickOnItemInBounds(lookup(".table-display-column-title"), virtualGrid, rectangleBounds);
        TestUtil.delay(1000);
        clickOn(".column-name-text-field");
        selectAllCurrentTextField();
        write(newColumnId.getRaw());
        clickOn(".ok-button");

        // Renaming involves thread hopping, so wait for a bit:
        TestUtil.sleep(1000);

        // Fetch the sorted transformation and check the column names (checks rename, and propagation):
        assertEquals(ImmutableSet.<ColumnId>of(new ColumnId("B"), newColumnId), ImmutableSet.copyOf(tableManager.getSingleTableOrThrow(new TableId("Sorted")).getData().getColumnIds()));
        // Check we haven't amassed multiple tables during the rename re-runs:
        assertEquals(2, lookup(".table-display-table-title").queryAll().size());
        assertEquals(4, lookup(".table-display-column-title").queryAll().size());
    }
    
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
        clickOnItemInBounds(lookup(".expand-arrow"), virtualGrid, new RectangleBounds(originalTableTopLeft.offsetByRowCols(row, 0), originalTableTopLeft.offsetByRowCols(row, originalColumns)));
        
        // We borrow n as a seed:
        ColumnDetails columnDetails = new ColumnDetails(null, name, typeAndValueGen.getType(), typeAndValueGen.makeValue());
        enterColumnDetails(columnDetails, new Random(n + 100));
        
        TestUtil.sleep(500);
        
        // Check neither table moved:
        assertEquals(originalTableTopLeft, TestUtil.tablePosition(tableManager, srcId));
        assertEquals(transformTopLeft, TestUtil.tablePosition(tableManager, new TableId("Sorted")));
        
        for (Table table : tableManager.getAllTables())
        {
            // Check that the column count is now right on all tables:
            List<Column> columns = table.getData().getColumns();
            assertEquals(originalColumns + 1, columns.size());
            // Check that the original two columns have the right names:
            assertEquals(new ColumnId("A"), columns.get(0).getName());
            assertEquals(new ColumnId("B"), columns.get(1).getName());
            // Check that the third column has right details:
            assertEquals(columnDetails.columnId, columns.get(2).getName());
            assertEquals(columnDetails.dataType, columns.get(2).getType());
            TestUtil.assertValueEqual("", columns.get(2) instanceof EditableColumn ? columnDetails.defaultValue : null, columns.get(2).getDefaultValue());
        }
    }

    @Property(trials=4, shrink = false)
    @OnThread(Tag.Simulation)
    public void testAddColumnBeforeAfter(int positionIndicator, @From(GenColumnId.class) ColumnId name, @From(GenTypeAndValueGen.class) TypeAndValueGen typeAndValueGen) throws InternalException, UserException
    {
        tableManager.getTypeManager()._test_copyTaggedTypesFrom(typeAndValueGen.getTypeManager());
        // 2 tables, 2 columns:
        assertEquals(2, lookup(".table-display-table-title").queryAll().size());
        assertEquals(4, lookup(".table-display-column-title").queryAll().size());
        // 2 columns which you can add to, 3 rows plus 2 column headers:
        assertEquals(originalColumns + originalRows + 2, lookup(".expand-arrow").queryAll().stream().filter(Node::isVisible).count());

        // If the position is negative, we use add-before.  If it's zero or positive, we use add-after.
        String targetColumnName = Arrays.asList("A", "B").get(Math.abs(positionIndicator) % originalColumns);
        // Bring up context menu and click item:
        RectangleBounds rectangleBounds = new RectangleBounds(originalTableTopLeft, originalTableTopLeft.offsetByRowCols(1, originalColumns));
        withItemInBounds(lookup(".column-title").lookup((Label t) -> TestUtil.fx(() -> t.getText()).equals(targetColumnName)), 
            virtualGrid, rectangleBounds,
                this::showContextMenu);
        clickOn(lookup(positionIndicator < 0 ? ".id-virtGrid-column-addBefore" : ".id-virtGrid-column-addAfter").<Node>query());

        // We borrow n as a seed:
        ColumnDetails columnDetails = new ColumnDetails(null, name, typeAndValueGen.getType(), typeAndValueGen.makeValue());
        enterColumnDetails(columnDetails, new Random(positionIndicator + 100));
        TestUtil.sleep(500);

        // Should now be one more column in each table:
        assertEquals(2, lookup(".table-display-table-title").queryAll().size());
        assertEquals(6, lookup(".table-display-column-title").queryAll().size());
        // One extra column:
        assertEquals(originalColumns + 1 + originalRows + 2, lookup(".expand-arrow").queryAll().stream().filter(Node::isVisible).count());
        
        int newPosition = (Math.abs(positionIndicator) % originalColumns) + (positionIndicator < 0 ? 0 : 1);
        
        // Check that the existing columns are at right indexes:
        for (Table table : tableManager.getAllTables())
        {
            // Check that the column count is now right on all tables:
            List<Column> columns = table.getData().getColumns();
            assertEquals(originalColumns + 1, columns.size());
            // Check that the original two columns have the right names:
            int pos = newPosition > 0 ? 0 : 1;
            assertEquals("Position: " + pos, new ColumnId("A"), columns.get(pos).getName());
            pos = newPosition > 1 ? 1 : 2;
            assertEquals("Position " + pos, new ColumnId("B"), columns.get(pos).getName());
            // Check that the new column has right details:
            assertEquals(columnDetails.columnId, columns.get(newPosition).getName());
            assertEquals(columnDetails.dataType, columns.get(newPosition).getType());
            TestUtil.assertValueEqual("", columns.get(newPosition) instanceof EditableColumn ? columnDetails.defaultValue : null, columns.get(newPosition).getDefaultValue());
        }
    }
}
