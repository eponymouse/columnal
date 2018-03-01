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
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.stage.Stage;
import log.Log;
import org.checkerframework.checker.nullness.qual.KeyForBottom;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.units.qual.UnitsBottom;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
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
import records.data.datatype.DataType;
import records.data.datatype.NumberInfo;
import records.error.InternalException;
import records.error.UserException;
import records.gui.grid.RectangleBounds;
import records.gui.grid.VirtualGrid;
import records.transformations.Sort;
import test.DummyManager;
import test.TestUtil;
import test.gen.GenColumnId;
import test.gen.GenTableId;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExFunction;
import utility.Pair;
import utility.Utility;
import utility.Workers;
import utility.Workers.Priority;
import utility.gui.FXUtility;

import java.awt.Toolkit;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

@OnThread(value = Tag.FXPlatform, ignoreParent = true)
@RunWith(JUnitQuickcheck.class)
public class TestTableEdits extends ApplicationTest implements ClickTableLocationTrait
{
    @SuppressWarnings("nullness")
    private @NonNull Stage mainWindow;

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
        mainWindow = stage;
        TableManager dummyManager = new DummyManager();
        Workers.onWorkerThread("Making tables", Priority.FETCH, () -> {
            try
            {
                ExFunction<RecordSet, ? extends EditableColumn> a = rs -> new MemoryBooleanColumn(rs, new ColumnId("A"), ImmutableList.of(true, false, false), false);
                ExFunction<RecordSet, ? extends EditableColumn> b = rs -> new MemoryNumericColumn(rs, new ColumnId("B"), NumberInfo.DEFAULT, ImmutableList.of(5, 4, 3), 6);
                @SuppressWarnings({"units", "keyfor"})
                @KeyForBottom @UnitsBottom ImmutableList<ExFunction<RecordSet, ? extends EditableColumn>> columns = ImmutableList.of(a, b);
                ImmediateDataSource src = new ImmediateDataSource(dummyManager, new InitialLoadDetails(null, originalTableTopLeft, null), new EditableRecordSet(columns, () -> 3));
                srcId = src.getId();
                dummyManager.record(src);
                Sort sort = new Sort(dummyManager, new InitialLoadDetails(new TableId("Sorted"), transformTopLeft, null), src.getId(), ImmutableList.of(new ColumnId("B"), new ColumnId("A")));
                dummyManager.record(sort);
                @OnThread(Tag.Simulation) Supplier<Pair<TableManager, VirtualGrid>> supplier = TestUtil.openDataAsTable(stage, dummyManager);
                new Thread(() -> {
                    Pair<TableManager, VirtualGrid> details = supplier.get();
                    this.tableManager = details.getFirst();
                    virtualGrid = details.getSecond();
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
    public void testRenameTable(@From(GenTableId.class) TableId newTableId) throws Exception
    {
        // 2 tables, 2 columns, each with 2 header rows:
        assertEquals(2, lookup(".table-display-table-title").queryAll().size());
        assertEquals(8, lookup(".table-display-column-title").queryAll().size());
        RectangleBounds rectangleBounds = new RectangleBounds(originalTableTopLeft, originalTableTopLeft.offsetByRowCols(0, originalColumns));
        clickOnItemInBounds(lookup(".table-display-table-title .text-field"), virtualGrid, rectangleBounds);
        deleteAll();
        write(newTableId.getRaw());
        // Different ways of exiting:

        // Click on right-hand end of table header:
        Bounds headerBox = virtualGrid._test_getRectangleBoundsScreen(rectangleBounds);
        clickOn(new Point2D(headerBox.getMaxX() - 2, headerBox.getMinY() + 2));

        // Renaming involves thread hopping, so wait for a bit:
        TestUtil.sleep(1000);

        assertEquals(ImmutableSet.of("Sorted", newTableId.getRaw()), tableManager.getAllTables().stream().map(t -> t.getId().getRaw()).sorted().collect(Collectors.toSet()));
        // Check we haven't amassed multiple tables during the rename re-runs:
        assertEquals(2, lookup(".table-display-table-title").queryAll().size());
        assertEquals(8, lookup(".table-display-column-title").queryAll().size());
    }

    @Property(trials = 3)
    public void testRenameColumn(@From(GenColumnId.class) ColumnId newColumnId) throws Exception
    {
        // 2 tables, 2 columns, each with 2 header rows:
        assertEquals(2, lookup(".table-display-table-title").queryAll().size());
        assertEquals(8, lookup(".table-display-column-title").queryAll().size());
        RectangleBounds rectangleBounds = new RectangleBounds(originalTableTopLeft, originalTableTopLeft.offsetByRowCols(1, originalColumns));
        clickOnItemInBounds(lookup(".table-display-column-title .text-field").lookup((Predicate<Node>) n -> ((TextField)n).getText().equals("A")), virtualGrid, rectangleBounds);
        deleteAll();
        write(newColumnId.getRaw());
        // Different ways of exiting:

        // Click on right-hand end of table header:
        Bounds headerBox = virtualGrid._test_getRectangleBoundsScreen(rectangleBounds);
        clickOn(new Point2D(headerBox.getMaxX() - 2, headerBox.getMinY() + 2));

        // Renaming involves thread hopping, so wait for a bit:
        TestUtil.sleep(1000);

        // Fetch the sorted transformation and check the column names (checks rename, and propagation):
        assertEquals(ImmutableSet.<ColumnId>of(new ColumnId("B"), newColumnId), ImmutableSet.copyOf(tableManager.getSingleTableOrThrow(new TableId("Sorted")).getData().getColumnIds()));
        // Check we haven't amassed multiple tables during the rename re-runs:
        assertEquals(2, lookup(".table-display-table-title").queryAll().size());
        assertEquals(8, lookup(".table-display-column-title").queryAll().size());
    }
    
    @Property(trials=2)
    public void testAddColumnAtRight(int n) throws InternalException, UserException
    {
        for (Table table : tableManager.getAllTables())
        {
            assertEquals(originalColumns, table.getData().getColumns().size());
        }
        int row = Math.abs(n) % (originalRows + 2); 
        clickOnItemInBounds(lookup(".expand-arrow"), virtualGrid, new RectangleBounds(originalTableTopLeft.offsetByRowCols(row, 0), originalTableTopLeft.offsetByRowCols(row, originalColumns)));
        TestUtil.sleep(500);
        
        // Check neither table moved:
        assertEquals(originalTableTopLeft, tableManager.getSingleTableOrThrow(srcId).getMostRecentPosition());
        assertEquals(transformTopLeft, tableManager.getSingleTableOrThrow(new TableId("Sorted")).getMostRecentPosition());
        
        for (Table table : tableManager.getAllTables())
        {
            // Check that the column count is now right on all tables:
            List<Column> columns = table.getData().getColumns();
            assertEquals(originalColumns + 1, columns.size());
            // Check that the original two columns have the right names:
            assertEquals(new ColumnId("A"), columns.get(0).getName());
            assertEquals(new ColumnId("B"), columns.get(1).getName());
            // Check that the third column has automatic type:
            assertEquals(DataType.toInfer(), columns.get(2).getType());
        }
    }
    
    @Property(trials=4)
    public void testAddColumnBeforeAfter(int positionIndicator) throws InternalException, UserException
    {
        // 2 tables, 2 columns, each with 2 header rows:
        assertEquals(2, lookup(".table-display-table-title").queryAll().size());
        assertEquals(8, lookup(".table-display-column-title").queryAll().size());
        // 2 columns which you can add to, 3 rows plus 2 column headers:
        assertEquals(originalColumns + originalRows + 2, lookup(".expand-arrow").queryAll().stream().filter(Node::isVisible).count());

        // If the position is negative, we use add-before.  If it's zero or positive, we use add-after.
        String targetColumnName = Arrays.asList("A", "B").get(Math.abs(positionIndicator) % originalColumns);
        // Bring up context menu and click item:
        RectangleBounds rectangleBounds = new RectangleBounds(originalTableTopLeft, originalTableTopLeft.offsetByRowCols(1, originalColumns));
        clickOnItemInBounds(lookup(".text-field").lookup((TextField t) -> t.getText().equals(targetColumnName)), 
            virtualGrid, rectangleBounds,
            MouseButton.SECONDARY);
        clickOn(lookup(positionIndicator < 0 ? ".id-virtGrid-column-addBefore" : ".id-virtGrid-column-addAfter").<Node>tryQuery().get());
        
        TestUtil.sleep(500);

        // Should now be one more column in each table:
        assertEquals(2, lookup(".table-display-table-title").queryAll().size());
        assertEquals(12, lookup(".table-display-column-title").queryAll().size());
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
            // Check that the third column has automatic type:
            assertEquals(DataType.toInfer(), columns.get(newPosition).getType());
        }
    }
    
    
         

    private void deleteAll()
    {
        press(KeyCode.END);
        // TODO: ain't gonna work on Mac
        press(KeyCode.CONTROL, KeyCode.A);
        press(KeyCode.DELETE);
    }
}
