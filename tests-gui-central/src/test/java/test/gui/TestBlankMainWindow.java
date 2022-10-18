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

package test.gui;

import annotation.identifier.qual.ExpressionIdentifier;
import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.stage.Stage;
import xyz.columnal.log.Log;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testfx.robot.Motion;
import org.testfx.service.query.impl.NodeQueryImpl;
import org.testfx.util.WaitForAsyncUtils;
import xyz.columnal.data.CellPosition;
import xyz.columnal.data.TBasicUtil;
import xyz.columnal.data.RecordSet;
import xyz.columnal.id.TableId;
import xyz.columnal.data.TableManager;
import xyz.columnal.data.datatype.DataType;
import xyz.columnal.data.datatype.DataType.FlatDataTypeVisitor;
import xyz.columnal.data.datatype.DataType.TagType;
import xyz.columnal.data.datatype.DataTypeUtility;
import xyz.columnal.data.datatype.DataTypeValue;
import xyz.columnal.data.datatype.NumberInfo;
import xyz.columnal.data.datatype.TypeId;
import xyz.columnal.data.datatype.TypeManager;
import xyz.columnal.data.unit.Unit;
import xyz.columnal.error.InternalException;
import xyz.columnal.error.InvalidImmediateValueException;
import xyz.columnal.error.UserException;
import xyz.columnal.gui.MainWindow;
import xyz.columnal.gui.MainWindow.MainWindowActions;
import xyz.columnal.gui.dtf.DocumentTextField;
import xyz.columnal.gui.grid.RectangleBounds;
import xyz.columnal.transformations.expression.type.TypeExpression;
import test.MatcherUtil;
import test.gen.GenNumber;
import test.gen.GenRandom;
import test.gen.type.GenDataTypeMaker;
import test.gen.type.GenDataTypeMaker.MustHaveValues;
import test.gen.type.GenTypeAndValueGen;
import test.gen.type.GenTypeAndValueGen.TypeAndValueGen;
import test.gui.trait.ClickTableLocationTrait;
import test.gui.trait.ComboUtilTrait;
import test.gui.trait.EnterStructuredValueTrait;
import test.gui.trait.EnterTypeTrait;
import test.gui.trait.FocusOwnerTrait;
import test.gui.trait.PopupTrait;
import test.gui.trait.ScrollToTrait;
import test.gui.trait.TextFieldTrait;
import test.gui.util.FXApplicationTest;
import threadchecker.OnThread;
import threadchecker.Tag;
import xyz.columnal.utility.adt.Either;
import xyz.columnal.utility.IdentifierUtility;
import xyz.columnal.utility.adt.Pair;
import xyz.columnal.utility.Utility;
import xyz.columnal.utility.gui.FXUtility;

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
public class TestBlankMainWindow extends FXApplicationTest implements ComboUtilTrait, ScrollToTrait, ClickTableLocationTrait, EnterTypeTrait, EnterStructuredValueTrait, FocusOwnerTrait, TextFieldTrait, PopupTrait
{
    public static final CellPosition NEW_TABLE_POS = new CellPosition(CellPosition.row(1), CellPosition.col(1));
    
    @SuppressWarnings("nullness")
    @OnThread(Tag.Any)
    private @NonNull MainWindowActions mainWindowActions;

    @Override
    public void start(Stage _stage) throws Exception
    {
        super.start(_stage);
        File dest = File.createTempFile("blank", "rec");
        dest.deleteOnExit();
        mainWindowActions = MainWindow.show(windowToUse, dest, null, null);
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
        assertTrue(TFXUtil.fx(() -> windowToUse.isShowing()));
        assertEquals(1, (int) TFXUtil.fx(() -> MainWindow._test_getViews().size()));
        assertTrue(TFXUtil.fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().isEmpty()));
        assertEquals(1, (int) TFXUtil.fx(() -> listWindows().size()));
    }

    @Test
    @OnThread(Tag.Any)
    public void testNewClick()
    {
        testStartState();
        clickOn("#id-menu-project").clickOn(".id-menu-project-new");
        assertEquals(2, TFXUtil.fx(() -> MainWindow._test_getViews()).size());
        assertTrue(TFXUtil.fx(() -> MainWindow._test_getViews()).values().stream().allMatch(Stage::isShowing));
    }

    @Test
    @OnThread(Tag.Any)
    public void testCloseMenu()
    {
        testStartState();
        clickOn("#id-menu-project");
        System.err.println("Windows after menu click: " + getWindowList());
        clickOn(".id-menu-project-close");
        assertTrue(TFXUtil.fx(() -> MainWindow._test_getViews()).isEmpty());
    }

    @Test
    @OnThread(Tag.Any)
    public void testNewEntryTable() throws InternalException, UserException
    {
        testStartState();
        CellPosition targetPos = NEW_TABLE_POS;
        makeNewDataEntryTable(targetPos);

        TFXUtil.sleep(1000);
        assertEquals(1, mainWindowActions._test_getTableManager().getAllTables().size());
        assertNotEquals("", mainWindowActions._test_getTableManager().getAllTables().get(0).getId().getRaw());
        assertEquals(1, mainWindowActions._test_getTableManager().getAllTables().get(0).getData().getColumns().size());
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
        clickOnItemInBounds(".create-table-grid-button", mainWindowActions._test_getVirtualGrid(), new RectangleBounds(targetPos, targetPos), MouseButton.PRIMARY);
        correctTargetWindow().clickOn(".id-new-data");
        correctTargetWindow();
        if (tableName != null)
        {
            write(tableName, DELAY);
        }
        push(KeyCode.TAB);
        write("A");
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
            String expectedAfter = enterStructuredValue(dataTypeAndDefault.getFirst(), dataTypeAndDefault.getSecond(), new Random(1), true, true);
            defocusSTFAndCheck(expectedAfter, () -> push(KeyCode.TAB));
        }
        moveAndDismissPopupsAtPos(point(".ok-button"));
        clickOn(".ok-button");
        TFXUtil.sleep(200);
    }

    @Test
    @OnThread(Tag.Any)
    public void testUndoNewEntryTable() throws InternalException, UserException
    {
        testStartState();
        makeNewDataEntryTable(NEW_TABLE_POS);
        assertEquals(1, (int) TFXUtil.fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().size()));
        assertEquals(1, count(".table-display-table-title"));
        clickOn("#id-menu-edit").moveBy(5, 0).clickOn(".id-menu-edit-undo", Motion.VERTICAL_FIRST);
        TFXUtil.sleep(1000);
        assertEquals(0, (int) TFXUtil.fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().size()));
        assertEquals(0, count(".table-display-table-title"));
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
                @ExpressionIdentifier String name = IdentifierUtility.identNum("Table", i);
                //System.out.println("###\n# Adding " + name + " " + Instant.now() + "\n###\n");
                makeNewDataEntryTable(name, NEW_TABLE_POS.offsetByRowCols(4 * tableIds.size(), 0), null);
                tableIds.add(new TableId(name));
            }
            else
            {
                //System.out.println("###\n# Undoing " + Instant.now() + "\n###\n");
                clickOn("#id-menu-edit").moveBy(5, 0).clickOn(".id-menu-edit-undo", Motion.VERTICAL_FIRST);
                TFXUtil.sleep(1000);
                tableIds.remove(tableIds.size() - 1);
            }
            assertEquals(ImmutableSet.copyOf(tableIds), TFXUtil.fx(() -> mainWindowActions._test_getTableManager().getAllTables().stream().map(t -> t.getId()).collect(ImmutableSet.toImmutableSet())));
            assertEquals(tableIds.size(), count(".table-display-table-title"));
        }
    }

    @Test
    @OnThread(Tag.Simulation)
    public void testUndoAddRow() throws InternalException, UserException
    {
        testStartState();
        makeNewDataEntryTable(NEW_TABLE_POS);
        TableManager tableManager = mainWindowActions._test_getTableManager();
        assertEquals(1, (int) TFXUtil.fx(() -> tableManager.getAllTables().size()));
        assertEquals(0, tableManager.getAllTables().get(0).getData().getLength());
        CellPosition arrowPos = NEW_TABLE_POS.offsetByRowCols(3, 0);
        clickOnItemInBounds(".expand-arrow", mainWindowActions._test_getVirtualGrid(), new RectangleBounds(arrowPos, arrowPos));
        assertEquals(1, tableManager.getAllTables().get(0).getData().getLength());
        assertEquals(1, count(".table-display-table-title"));
        assertEquals(1, count(".document-text-field"));
        
        clickOn("#id-menu-edit").moveBy(5, 0).clickOn(".id-menu-edit-undo", Motion.VERTICAL_FIRST);
        TFXUtil.sleep(2000);
        assertEquals(1, (int) TFXUtil.fx(() -> tableManager.getAllTables().size()));
        assertEquals(1, count(".table-display-table-title"));
        assertEquals(0, tableManager.getAllTables().get(0).getData().getLength());
        // STF will be retained invisible for re-use, so
        // must check visibility:
        assertEquals(0, TFXUtil.fx(() -> lookup(".document-text-field").match(Node::isVisible).queryAll().size()).intValue());
    }

    @Property(trials = 5)
    @OnThread(Tag.Simulation)
    public void propUndoAddAndEditData(@From(GenRandom.class) Random r) throws InternalException, UserException
    {
        GenNumber gen = new GenNumber();
        Supplier<@Value Number> makeNumber = () -> gen.generate(new SourceOfRandomness(r), null);
        @Value Number def = makeNumber.get();
        makeNewDataEntryTable(null, NEW_TABLE_POS, new Pair<>(DataType.NUMBER, def));
        TFXUtil.sleep(200);
        TableManager tableManager = mainWindowActions._test_getTableManager();
        assertEquals(1, (int) TFXUtil.fx(() -> tableManager.getAllTables().size()));
        
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
                clickOnItemInBounds(".expand-arrow", mainWindowActions._test_getVirtualGrid(), new RectangleBounds(arrowPos, arrowPos));
                TFXUtil.sleep(4000);
            }
            else if (choice <= 5 && dataHistory.size() > 1) // 3 - 5
            {
                Log.debug("@@@ Undoing " + Utility.listToString(latest) + " to " + dataHistory.get(dataHistory.size() - 2));
                // Undo
                latest = dataHistory.get(dataHistory.size() - 2);
                // Pop twice as our state will get re-added as latest after this if/else:
                dataHistory.remove(dataHistory.size() - 1);
                dataHistory.remove(dataHistory.size() - 1);
                clickOn("#id-menu-edit").moveBy(5, 0);
                TFXUtil.sleep(500);
                clickOn(".id-menu-edit-undo", Motion.VERTICAL_FIRST);
                TFXUtil.sleep(4000);
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
                enterValue(NEW_TABLE_POS.offsetByRowCols(3 + row, 0), Either.right(new Pair<DataType, @Value Object>(DataType.NUMBER, newVal)), r);
                latest.set(row, newVal);
                TFXUtil.sleep(4000);
            }
            assertEquals(1, tableManager.getAllTables().size());
            RecordSet recordSet = tableManager.getAllTables().get(0).getData();
            assertEquals(latest.size(), recordSet.getLength());
            for (int j = 0; j < latest.size(); j++)
            {
                TBasicUtil.assertValueEqual("Index " + j, latest.get(j), recordSet.getColumns().get(0).getType().getCollapsed(j));
            }
            
            dataHistory.add(latest);
            TFXUtil.sleep(500);
        }
    }

    @Property(trials = 5)
    @OnThread(Tag.Any)
    public void propAddColumnToEntryTable(@From(GenDataTypeMaker.class) @MustHaveValues GenDataTypeMaker.DataTypeMaker dataTypeMaker) throws UserException, InternalException, Exception
    {
        TBasicUtil.printSeedOnFail(() -> {
            addColumnToEntryTable(dataTypeMaker.getTypeManager(), dataTypeMaker.makeType().getDataType());
        });
    }

    @OnThread(Tag.Any)
    private void addColumnToEntryTable(TypeManager typeManager, DataType dataType) throws InternalException, UserException
    {
        mainWindowActions._test_getTableManager().getTypeManager()._test_copyTaggedTypesFrom(typeManager);
        addNewTableWithColumn(dataType, null);
    }

    @OnThread(Tag.Any)
    private void addNewTableWithColumn(DataType dataType, @Nullable @Value Object value) throws InternalException, UserException
    {
        testNewEntryTable();
        Node expandRight = TFXUtil.fx(() -> lookup(".expand-arrow").match(n -> FXUtility.hasPseudoclass(n, "expand-right")).<Node>query());
        assertNotNull(expandRight);
        // Won't happen, assertion will fail:
        if (expandRight == null) return;
        clickOn(expandRight);
        String newColName = "Column " + Math.abs(new Random().nextInt());
        write(newColName);
        push(KeyCode.TAB);
        Log.debug("Entering type: " + dataType);
        enterType(TypeExpression.fromDataType(dataType), new Random(1));
        // Dismiss popups:
        push(KeyCode.ESCAPE);
        if (value != null)
        {
            clickOn(".default-value");
            enterStructuredValue(dataType, value, new Random(1), true, true);
        }
        moveAndDismissPopupsAtPos(point(".ok-button"));
        clickOn(".ok-button");
        WaitForAsyncUtils.waitForFxEvents();
        try
        {
            Thread.sleep(2000);
        }
        catch (InterruptedException e) { }
        assertEquals(2, (int) TFXUtil.fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().get(0).getData().getColumns().size()));
        assertEquals(newColName, TFXUtil.fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().get(0).getData().getColumns().get(1).getName().getRaw()));
        assertEquals(dataType, TFXUtil.fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().get(0).getData().getColumns().get(1).getType().getType()));
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
    public void propDefaultValue(@From(GenTypeAndValueGen.class) TypeAndValueGen typeAndValueGen) throws InternalException, UserException, Exception
    {
        TBasicUtil.printSeedOnFail(() -> {
            mainWindowActions._test_getTableManager().getTypeManager()._test_copyTaggedTypesFrom(typeAndValueGen.getTypeManager());
            @Value Object initialVal = typeAndValueGen.makeValue();
            addNewTableWithColumn(typeAndValueGen.getType(), initialVal);
            List<@Value Object> values = new ArrayList<>();
            for (int i = 0; i < 3; i++)
            {
                addNewRow();
                values.add(initialVal);
                // Now test for equality:
                RecordSet recordSet = TFXUtil.fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().get(0).getData());
                DataTypeValue column = recordSet.getColumns().get(1).getType();
                assertEquals(values.size(), (int) TFXUtil.sim(() -> recordSet.getLength()));
                for (int j = 0; j < values.size(); j++)
                {
                    int jFinal = j;
                    TBasicUtil.assertValueEqual("Index " + j, values.get(j), TFXUtil.<@Value Object>sim(() -> column.getCollapsed(jFinal)));
                }
            }
        });
    }

    @Property(trials = 3)
    @OnThread(Tag.Simulation)
    public void propEnterColumn(@From(GenTypeAndValueGen.class) TypeAndValueGen typeAndValueGen, @From(GenRandom.class) Random r) throws InternalException, UserException, Exception
    {
        addColumnToEntryTable(typeAndValueGen.getTypeManager(), typeAndValueGen.getType());
        // Now set the values
        List<Either<String, @Value Object>> values = new ArrayList<>();
        for (int i = 0; i < 10;i ++)
        {
            addNewRow();
            TFXUtil.sleep(500);
            Either<String, Pair<DataType, @Value Object>> entry;
            final String invalidChar;
            final int invalidPos;
            if (r.nextInt(8) != 1 || typeAndValueGen.getType().equals(DataType.TEXT))
            {
                @Value Object value = typeAndValueGen.makeValue();
                entry = Either.right(new Pair<DataType, @Value Object>(typeAndValueGen.getType(), value));
                invalidPos = - 1;
                invalidChar = "";
            }
            else
            {
                // Need to not have a valid input by accident!
                // Ways to make an error: either use invalid char by itself,
                // or add an invalid char before or after real value.
                ImmutableList<String> invalidChars = ImmutableList.of("@", "#", "%");
                invalidChar = invalidChars.get(r.nextInt(invalidChars.size()));
                if (r.nextInt(3) == 1)
                {
                    // By itself:
                    entry = Either.left(invalidChar);
                    invalidPos = 0;
                }
                else
                {
                    String content = DataTypeUtility.valueToString(typeAndValueGen.makeValue());
                    if (r.nextBoolean())
                    {
                        entry = Either.left(invalidChar + content);
                        invalidPos = 0;
                    }
                    else
                    {
                        entry = Either.left(content + invalidChar);
                        invalidPos = content.length();
                    }
                }
            }

            values.add(entry.<@Value Object>map(p -> p.getSecond()));
            DocumentTextField field = enterValue(NEW_TABLE_POS.offsetByRowCols(3 + i, 1), entry, r);
            if (entry.isLeft())
                MatcherAssert.assertThat(TFXUtil.fx(() -> field._test_getStyleSpans(invalidPos, invalidPos + invalidChar.length())), Matchers.everyItem(MatcherUtil.matcherOn(Matchers.hasItem("input-error"), s -> s.getFirst())));
        }
        // Now test for equality:
        RecordSet recordSet = TFXUtil.fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().get(0).getData());
        DataTypeValue column = recordSet.getColumns().get(1).getType();
        assertEquals(values.size(), (int) TFXUtil.sim(() -> recordSet.getLength()));
        for (int i = 0; i < values.size(); i++)
        {
            int iFinal = i;
            TBasicUtil.assertValueEitherEqual("Index " + i, values.get(i), TFXUtil.<Either<String, @Value Object>>sim(() -> {
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

    @OnThread(Tag.Simulation)
    private DocumentTextField enterValue(CellPosition position, Either<String, Pair<DataType, @Value Object>> value, Random random) throws UserException, InternalException
    {
        // Make sure we aren't already selecting that cell:
        push(KeyCode.ESCAPE);
        push(KeyCode.ESCAPE);
        push(KeyCode.SHORTCUT, KeyCode.HOME);
        final @NonNull DocumentTextField textField = (DocumentTextField) clickOnItemInBounds(".document-text-field", mainWindowActions._test_getVirtualGrid(), new RectangleBounds(position, position));
        // Three ways to enter value:
        // click twice, click once and press enter, click once and start typing
        int choice = random.nextInt(3);
        boolean needDeleteAll = choice == 0;
        if (choice == 0)
        {
            // Click again
            sleep(500);
            clickOnItemInBounds(".document-text-field", mainWindowActions._test_getVirtualGrid(), new RectangleBounds(position, position));
        }
        if (choice == 1)
            push(KeyCode.ENTER);

        if (choice != 2)
        {
            assertFocusOwner("Choice: " + choice, textField);
        }
        String expectedAfter = value.eitherEx(s -> {
            if (needDeleteAll)
            {
                push(TFXUtil.ctrlCmd(), KeyCode.A);
                push(KeyCode.DELETE);
                push(KeyCode.HOME);
            }
            write(s);
            assertFocusOwner("Writing complete invalid", textField);
            return s;
        }, p -> {
            String expAfter = enterStructuredValue(p.getFirst(), p.getSecond(), random, needDeleteAll, true);
            assertFocusOwner("Written structured value: " + DataTypeUtility.valueToString(p.getSecond()) + " after choice " + choice, textField);
            return expAfter;
        });
        defocusSTFAndCheck(expectedAfter, () -> {
            // One to get rid of any code completion:
            //push(KeyCode.ESCAPE);
            // Enter to finish editing:
            push(KeyCode.ENTER);
        });
        return textField;
    }

    @OnThread(Tag.Any)
    private boolean hasNumber(DataType type) throws InternalException
    {
        return type.apply(new FlatDataTypeVisitor<Boolean>(false) {
            @Override
            public Boolean number(NumberInfo numberInfo) throws InternalException, InternalException
            {
                return true;
            }

            @Override
            public Boolean tagged(TypeId typeName, ImmutableList<Either<Unit, DataType>> typeVars, ImmutableList<TagType<DataType>> tags) throws InternalException, InternalException
            {
                for (TagType<DataType> tag : tags)
                {
                    if (tag.getInner() != null && hasNumber(tag.getInner()))
                        return true;
                }
                return false;
            }

            @Override
            public Boolean record(ImmutableMap<@ExpressionIdentifier String, DataType> fields) throws InternalException, InternalException
            {
                for (DataType dataType : fields.values())
                {
                    if (hasNumber(dataType))
                        return true;
                }
                return false;
            }

            @Override
            public Boolean array(DataType inner) throws InternalException, InternalException
            {
                return hasNumber(inner);
            }
        });
    }

    @OnThread(Tag.Any)
    private void assertFocusOwner(String description, @NonNull DocumentTextField textField)
    {
        Node focused = getFocusOwner();
        assertNotNull(focused);
        if (focused == null) // Satisfy checker
            return;
        assertTrue(description + " focus not STF: " + focused.getClass().toString() + "; " + focused, focused instanceof DocumentTextField);
    }

    @OnThread(Tag.Any)
    private void addNewRow()
    {
        Node expandDown = TFXUtil.fx(() -> lookup(".expand-arrow").match(n -> FXUtility.hasPseudoclass(n, "expand-down")).<Node>query());
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
        return TFXUtil.fx(() -> {
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
