package test.gui;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.stage.Stage;
import log.Log;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testfx.robot.Motion;
import org.testfx.service.query.impl.NodeQueryImpl;
import org.testfx.util.WaitForAsyncUtils;
import records.data.CellPosition;
import records.data.RecordSet;
import records.data.TableId;
import records.data.TableManager;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.InvalidImmediateValueException;
import records.error.UserException;
import records.gui.MainWindow;
import records.gui.MainWindow.MainWindowActions;
import records.gui.grid.RectangleBounds;
import records.gui.flex.FlexibleTextField;
import records.transformations.expression.type.TypeExpression;
import test.TestUtil;
import test.gen.GenDataType;
import test.gen.GenDataType.DataTypeAndManager;
import test.gen.GenNumber;
import test.gen.GenRandom;
import test.gen.GenTypeAndValueGen;
import test.gen.GenTypeAndValueGen.TypeAndValueGen;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.ComboUtilTrait;
import test.gui.trait.EnterStructuredValueTrait;
import test.gui.trait.EnterTypeTrait;
import test.gui.trait.FocusOwnerTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.trait.TextFieldTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Either;
import utility.Pair;
import utility.UnitType;
import utility.Utility;
import utility.gui.FXUtility;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import static org.junit.Assert.*;

/**
 * Created by neil on 10/06/2017.
 */
@OnThread(value = Tag.FXPlatform, ignoreParent = true)
@RunWith(JUnitQuickcheck.class)
public class TestBlankMainWindow extends FXApplicationTest implements ComboUtilTrait, ScrollToTrait, ClickTableLocationTrait, EnterTypeTrait, EnterStructuredValueTrait, FocusOwnerTrait, TextFieldTrait
{
    public static final CellPosition NEW_TABLE_POS = new CellPosition(CellPosition.row(1), CellPosition.col(1));
    
    @SuppressWarnings("nullness")
    @OnThread(Tag.Any)
    private @NonNull MainWindowActions mainWindowActions;

    @Override
    public void start(Stage stage) throws Exception
    {
        super.start(stage);
        File dest = File.createTempFile("blank", "rec");
        dest.deleteOnExit();
        mainWindowActions = MainWindow.show(stage, dest, null);
    }

    @After
    @OnThread(Tag.Any)
    public void hide()
    {
        Platform.runLater(() -> {
            // Take a copy to avoid concurrent modification:
            new ArrayList<>(MainWindow._test_getViews().values()).forEach(Stage::hide);
        });
    }

    // Both a test, and used as utility method.
    @Test
    @OnThread(Tag.Any)
    public void testStartState()
    {
        assertTrue(TestUtil.fx(() -> windowToUse.isShowing()));
        assertEquals(1, (int) TestUtil.fx(() -> MainWindow._test_getViews().size()));
        assertTrue(TestUtil.fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().isEmpty()));
        assertEquals(1, (int) TestUtil.fx(() -> listWindows().size()));
    }

    @Test
    public void testNewClick()
    {
        testStartState();
        clickOn("#id-menu-project").clickOn(".id-menu-project-new");
        assertEquals(2, MainWindow._test_getViews().size());
        assertTrue(MainWindow._test_getViews().values().stream().allMatch(Stage::isShowing));
    }

    @Test
    public void testCloseMenu()
    {
        testStartState();
        clickOn("#id-menu-project");
        System.err.println("Windows after menu click: " + getWindowList());
        clickOn(".id-menu-project-close");
        assertTrue(MainWindow._test_getViews().isEmpty());
    }

    @Test
    @OnThread(Tag.Any)
    public void testNewEntryTable() throws InternalException, UserException
    {
        testStartState();
        CellPosition targetPos = NEW_TABLE_POS;
        makeNewDataEntryTable(targetPos);

        TestUtil.sleep(1000);
        assertEquals(1, (int) TestUtil.fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().size()));
        assertEquals(1, (int)TestUtil.fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().get(0).getData().getColumns().size()));
    }

    @OnThread(Tag.Any)
    private void makeNewDataEntryTable(CellPosition targetPos) throws InternalException, UserException
    {
        makeNewDataEntryTable(null, targetPos, null);
    }

    @OnThread(Tag.Any)
    private void makeNewDataEntryTable(@Nullable String tableName, CellPosition targetPos, @Nullable Pair<DataType, @Value Object> dataTypeAndDefault) throws UserException, InternalException
    {
        keyboardMoveTo(mainWindowActions._test_getVirtualGrid(), targetPos);
        // Only need to click once as already selected by keyboard:
        clickOnItemInBounds(lookup(".create-table-grid-button"), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
        correctTargetWindow().clickOn(".id-new-data");
        correctTargetWindow();
        if (tableName != null)
            write(tableName, DELAY);
        push(KeyCode.TAB);
        push(KeyCode.TAB);
        if (dataTypeAndDefault == null)
        {
            write("Text", 1);
        }
        else
        {
            enterType(TypeExpression.fromDataType(dataTypeAndDefault.getFirst()), new Random(1));
            push(KeyCode.ESCAPE);
            push(KeyCode.ESCAPE);
            push(KeyCode.TAB);
            enterStructuredValue(dataTypeAndDefault.getFirst(), dataTypeAndDefault.getSecond(), new Random(1), false);
            defocusSTFAndCheck(true, () -> push(KeyCode.TAB));
        }
        clickOn(".ok-button");
        TestUtil.delay(200);
    }

    @Test
    @OnThread(Tag.Any)
    public void testUndoNewEntryTable() throws InternalException, UserException
    {
        testStartState();
        makeNewDataEntryTable(NEW_TABLE_POS);
        assertEquals(1, (int) TestUtil.fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().size()));
        assertEquals(1, lookup(".table-display-table-title").queryAll().size());
        clickOn("#id-menu-edit").moveBy(5, 0).clickOn(".id-menu-edit-undo", Motion.VERTICAL_FIRST);
        TestUtil.sleep(1000);
        assertEquals(0, (int) TestUtil.fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().size()));
        assertEquals(0, lookup(".table-display-table-title").queryAll().size());
    }

    @Property(trials = 2)
    @OnThread(Tag.Any)
    public void propUndoNewEntryTable(@From(GenRandom.class) Random r) throws InternalException, UserException
    {
        // We make new tables and undo them at random, with
        // slight preference for making.
        
        ArrayList<TableId> tableIds = new ArrayList<>();

        for (int i = 0; i < 8; i++)
        {
            if (r.nextInt(5) <= 2 || tableIds.isEmpty())
            {
                String name = "Table " + i;
                //System.out.println("###\n# Adding " + name + " " + Instant.now() + "\n###\n");
                makeNewDataEntryTable(name, NEW_TABLE_POS.offsetByRowCols(4 * tableIds.size(), 0), null);
                tableIds.add(new TableId(name));
            }
            else
            {
                //System.out.println("###\n# Undoing " + Instant.now() + "\n###\n");
                clickOn("#id-menu-edit").moveBy(5, 0).clickOn(".id-menu-edit-undo", Motion.VERTICAL_FIRST);
                TestUtil.sleep(1000);
                tableIds.remove(tableIds.size() - 1);
            }
            assertEquals(ImmutableSet.copyOf(tableIds), TestUtil.fx(() -> mainWindowActions._test_getTableManager().getAllTables().stream().map(t -> t.getId()).collect(ImmutableSet.toImmutableSet())));
            assertEquals(tableIds.size(), lookup(".table-display-table-title").queryAll().size());
        }
    }

    @Test
    @OnThread(Tag.Simulation)
    public void testUndoAddRow() throws InternalException, UserException
    {
        testStartState();
        makeNewDataEntryTable(NEW_TABLE_POS);
        TableManager tableManager = mainWindowActions._test_getTableManager();
        assertEquals(1, (int) TestUtil.fx(() -> tableManager.getAllTables().size()));
        assertEquals(0, tableManager.getAllTables().get(0).getData().getLength());
        CellPosition arrowPos = NEW_TABLE_POS.offsetByRowCols(3, 0);
        clickOnItemInBounds(lookup(".expand-arrow"), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(arrowPos, arrowPos));
        assertEquals(1, tableManager.getAllTables().get(0).getData().getLength());
        assertEquals(1, lookup(".table-display-table-title").queryAll().size());
        assertEquals(1, lookup(".flexible-text-field").queryAll().size());
        
        clickOn("#id-menu-edit").moveBy(5, 0).clickOn(".id-menu-edit-undo", Motion.VERTICAL_FIRST);
        TestUtil.sleep(1000);
        assertEquals(1, (int) TestUtil.fx(() -> tableManager.getAllTables().size()));
        assertEquals(1, lookup(".table-display-table-title").queryAll().size());
        assertEquals(0, tableManager.getAllTables().get(0).getData().getLength());
        // STF will be retained invisible for re-use, so
        // must check visibility:
        assertEquals(0, lookup(".flexible-text-field").match(Node::isVisible).queryAll().size());
    }

    @Property(trials = 5)
    @OnThread(Tag.Simulation)
    public void propUndoAddAndEditData(@From(GenRandom.class) Random r) throws InternalException, UserException
    {
        GenNumber gen = new GenNumber();
        Supplier<@Value Number> makeNumber = () -> DataTypeUtility.value(gen.generate(new SourceOfRandomness(r), null));
        @Value Number def = makeNumber.get();
        makeNewDataEntryTable(null, NEW_TABLE_POS, new Pair<>(DataType.NUMBER, def));
        TestUtil.sleep(200);
        TableManager tableManager = mainWindowActions._test_getTableManager();
        assertEquals(1, (int) TestUtil.fx(() -> tableManager.getAllTables().size()));

        // We make new rows and edit the data, and undo at random.

        ArrayList<ArrayList<@Value Number>> dataHistory = new ArrayList<ArrayList<@Value Number>>(ImmutableList.of(new ArrayList<@Value Number>()));

        for (int i = 0; i < 10; i++)
        {
            ArrayList<@Value Number> latest = new ArrayList<>(dataHistory.get(dataHistory.size() - 1));
            int choice = r.nextInt(10); //0 - 9
            if (choice <= 2 || latest.isEmpty()) // 0 - 2
            {
                Log.debug("@@@ Adding row");
                // Add a new row
                CellPosition arrowPos = NEW_TABLE_POS.offsetByRowCols(3 + latest.size(), 0);
                latest.add(def);
                clickOnItemInBounds(lookup(".expand-arrow"), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(arrowPos, arrowPos));
            }
            else if (choice <= 5 && dataHistory.size() > 1) // 3 - 5
            {
                Log.debug("@@@ Undoing " + Utility.listToString(latest) + " to " + dataHistory.get(dataHistory.size() - 2));
                // Undo
                latest = dataHistory.get(dataHistory.size() - 2);
                // Pop twice as our state will get re-added as latest after this if/else:
                dataHistory.remove(dataHistory.size() - 1);
                dataHistory.remove(dataHistory.size() - 1);
                clickOn("#id-menu-edit").moveBy(5, 0).clickOn(".id-menu-edit-undo", Motion.VERTICAL_FIRST);
                TestUtil.sleep(500);
            }
            else // 6 - 9
            {
                // Edit a random row
                int row = r.nextInt(latest.size());
                @Value Number newVal;
                int attempts = 0;
                do
                {
                    newVal = makeNumber.get();
                }
                while (Utility.compareNumbers(latest.get(row), newVal) == 0 && ++attempts < 100);
                Log.debug("@@@ Editing row " + row + " to be: " + newVal + " attempts: " + attempts);
                enterValue(NEW_TABLE_POS.offsetByRowCols(3 + row, 0), Either.right(new Pair<DataType, @Value Object>(DataType.NUMBER, newVal)), new Random(1));
                latest.set(row, newVal);
            }
            RecordSet recordSet = tableManager.getAllTables().get(0).getData();
            assertEquals(latest.size(), recordSet.getLength());
            for (int j = 0; j < latest.size(); j++)
            {
                TestUtil.assertValueEqual("Index " + j, latest.get(j), recordSet.getColumns().get(0).getType().getCollapsed(j));
            }
            
            dataHistory.add(latest);
        }
    }

    @Property(trials = 5)
    @OnThread(Tag.Any)
    public void propAddColumnToEntryTable(@From(GenDataType.class) GenDataType.DataTypeAndManager dataTypeAndManager) throws UserException, InternalException, Exception
    {
        TestUtil.printSeedOnFail(() -> {
            mainWindowActions._test_getTableManager().getTypeManager()._test_copyTaggedTypesFrom(dataTypeAndManager.typeManager);
            addNewTableWithColumn(dataTypeAndManager.dataType, null);
        });
    }

    @OnThread(Tag.Any)
    private void addNewTableWithColumn(DataType dataType, @Nullable @Value Object value) throws InternalException, UserException
    {
        testNewEntryTable();
        Node expandRight = lookup(".expand-arrow").match(n -> TestUtil.fx(() -> FXUtility.hasPseudoclass(n, "expand-right"))).<Node>query();
        assertNotNull(expandRight);
        // Won't happen, assertion will fail:
        if (expandRight == null) return;
        clickOn(expandRight);
        String newColName = "Column " + Math.abs(new Random().nextInt());
        write(newColName);
        push(KeyCode.TAB);
        enterType(TypeExpression.fromDataType(dataType), new Random(1));
        // Dismiss popups:
        push(KeyCode.ESCAPE);
        if (value != null)
        {
            clickOn(".default-value");
            enterStructuredValue(dataType, value, new Random(1), false);
        }
        clickOn(".ok-button");
        WaitForAsyncUtils.waitForFxEvents();
        try
        {
            Thread.sleep(2000);
        }
        catch (InterruptedException e) { }
        assertEquals(2, (int) TestUtil.fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().get(0).getData().getColumns().size()));
        assertEquals(newColName, TestUtil.fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().get(0).getData().getColumns().get(1).getName().getRaw()));
        assertEquals(dataType, TestUtil.fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().get(0).getData().getColumns().get(1).getType()));
    }

    private void clickOnSub(Node root, String subQuery)
    {
        assertTrue(subQuery.startsWith("."));
        @Nullable Node sub = new NodeQueryImpl().from(root).lookup(subQuery).<Node>query();
        assertNotNull(subQuery, sub);
        if (sub != null)
            clickOn(sub);
    }

    @Property(trials = 5)
    @OnThread(Tag.Any)
    public void propDefaultValue(@When(seed=-3291684708783208768L) @From(GenTypeAndValueGen.class) TypeAndValueGen typeAndValueGen) throws InternalException, UserException, Exception
    {
        TestUtil.printSeedOnFail(() -> {
            mainWindowActions._test_getTableManager().getTypeManager()._test_copyTaggedTypesFrom(typeAndValueGen.getTypeManager());
            @Value Object initialVal = typeAndValueGen.makeValue();
            addNewTableWithColumn(typeAndValueGen.getType(), initialVal);
            List<@Value Object> values = new ArrayList<>();
            for (int i = 0; i < 3; i++)
            {
                addNewRow();
                values.add(initialVal);
                // Now test for equality:
                @OnThread(Tag.Any) RecordSet recordSet = TestUtil.fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().get(0).getData());
                DataTypeValue column = recordSet.getColumns().get(1).getType();
                assertEquals(values.size(), (int) TestUtil.sim(() -> recordSet.getLength()));
                for (int j = 0; j < values.size(); j++)
                {
                    int jFinal = j;
                    TestUtil.assertValueEqual("Index " + j, values.get(j), TestUtil.<@Value Object>sim(() -> column.getCollapsed(jFinal)));
                }
            }
        });
    }

    @Property(trials = 3)
    @OnThread(Tag.Simulation)
    public void propEnterColumn(@When(seed=2L) @From(GenTypeAndValueGen.class) TypeAndValueGen typeAndValueGen, @When(seed=2L) @From(GenRandom.class) Random r) throws InternalException, UserException, Exception
    {
        propAddColumnToEntryTable(new DataTypeAndManager(typeAndValueGen.getTypeManager(), typeAndValueGen.getType()));
        // Now set the values
        List<Either<String, @Value Object>> values = new ArrayList<>();
        for (int i = 0; i < 10;i ++)
        {
            addNewRow();
            TestUtil.sleep(500);
            Either<String, Pair<DataType, @Value Object>> entry;
            if (r.nextInt(8) != 1)
            {
                @Value Object value = typeAndValueGen.makeValue();
                entry = Either.right(new Pair<DataType, @Value Object>(typeAndValueGen.getType(), value));
            }
            else
            {
                // Need to not have a valid input by accident!
                // Ways to make an error: either use invalid char by itself,
                // or add an invalid char before or after real value.
                ImmutableList<String> invalidChars = ImmutableList.of("@", "#", "%");
                String invalidChar = invalidChars.get(r.nextInt(invalidChars.size()));
                if (r.nextInt(3) == 1)
                {
                    // By itself:
                    entry = Either.left(invalidChar);
                }
                else
                {
                    String content = DataTypeUtility.valueToString(typeAndValueGen.getType(), typeAndValueGen.makeValue(), null, false);
                    if (r.nextBoolean())
                        entry = Either.left(invalidChar + content);
                    else
                        entry = Either.left(content + invalidChar);
                }
            }

            values.add(entry.<@Value Object>map(p -> p.getSecond()));
            enterValue(NEW_TABLE_POS.offsetByRowCols(3 + i, 1), entry, new Random(1));
        }
        // Now test for equality:
        @OnThread(Tag.Any) RecordSet recordSet = TestUtil.fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().get(0).getData());
        DataTypeValue column = recordSet.getColumns().get(1).getType();
        assertEquals(values.size(), (int) TestUtil.sim(() -> recordSet.getLength()));
        for (int i = 0; i < values.size(); i++)
        {
            int iFinal = i;
            TestUtil.assertValueEitherEqual("Index " + i, values.get(i), TestUtil.<Either<String, @Value Object>>sim(() -> {
                try
                {
                    return Either.right(column.getCollapsed(iFinal));
                }
                catch (InvalidImmediateValueException e)
                {
                    return Either.left(e.getInvalid());
                }
                // Deliberately do not catch InternalException or other exceptions.
            }));
        }
    }

    @OnThread(Tag.Any)
    private void enterValue(CellPosition position, Either<String, Pair<DataType, @Value Object>> value, Random random) throws UserException, InternalException
    {
        for (int i = 0; i < 2; i++)
            clickOnItemInBounds(lookup(".flexible-text-field"), mainWindowActions._test_getVirtualGrid(), new RectangleBounds(position, position));

        Node focused = getFocusOwner();
        assertNotNull(focused);
        if (focused == null)
            return; // To satisfy checker
        assertTrue("Focus not STF: " + focused.getClass().toString() + "; " + focused, focused instanceof FlexibleTextField);
        push(KeyCode.HOME);
        value.eitherEx(s -> {
            push(TestUtil.ctrlCmd(), KeyCode.A);
            push(KeyCode.DELETE);
            push(KeyCode.HOME);
            write(s);
            return UnitType.UNIT;
        }, p -> {
            enterStructuredValue(p.getFirst(), p.getSecond(), random, false);
            return UnitType.UNIT;
        });
        defocusSTFAndCheck(value.either(s -> true, p -> !p.getFirst().hasNumber()), () -> {
            // One to get rid of any code completion:
            push(KeyCode.ESCAPE);
            // Escape to finish editing:
            push(KeyCode.ESCAPE);
            push(KeyCode.ESCAPE);
        });
    }

    @OnThread(Tag.Any)
    private void addNewRow()
    {
        Node expandDown = lookup(".expand-arrow").match(n -> TestUtil.fx(() -> FXUtility.hasPseudoclass(n, "expand-down"))).<Node>query();
        assertNotNull(expandDown);
        // Won't happen, assertion will fail:
        if (expandDown == null) return;
        clickOn(expandDown);
        WaitForAsyncUtils.waitForFxEvents();
    }
/*
    @OnThread(Tag.Any)
    private boolean actuallyVisible(String query)
    {
        Node original = lookup(query).<Node>query();
        if (original == null)
            return false;
        return TestUtil.fx(() -> {
            return actuallyVisible(original);
        });
    }

    @NonNull
    @OnThread(Tag.FXPlatform)
    private Boolean actuallyVisible(Node original)
    {
        Bounds b = original.getBoundsInLocal();
        for (Node n = original, parent = original.getParent(); n != null && parent != null; n = parent, parent = parent.getParent())
        {
            b = n.localToParent(b);
            //System.err.println("Bounds in parent: " + b.getMinY() + "->"  + b.getMaxY());
            //System.err.println("  Parent bounds: " + parent.getBoundsInLocal().getMinY() + "->" + parent.getBoundsInLocal().getMaxY());
            if (!parent.getBoundsInLocal().contains(getCentre(b)))
                return false;
        }
        // If we get to the top and all is well, it is visible
        return true;
    }*/

    private static Point2D getCentre(Bounds b)
    {
        return new Point2D(b.getMinX(), b.getMinY()).midpoint(b.getMaxX(), b.getMaxY());
    }
}
