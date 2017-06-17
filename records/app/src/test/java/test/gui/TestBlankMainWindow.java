package test.gui;

import annotation.qual.Value;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.VerticalDirection;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.fxmisc.richtext.GenericStyledArea;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.service.query.impl.NodeQueryImpl;
import org.testfx.util.WaitForAsyncUtils;
import records.data.RecordSet;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeId;
import records.error.InternalException;
import records.error.UserException;
import records.gui.MainWindow;
import records.gui.View;
import test.TestUtil;
import test.gen.GenDataType;
import test.gen.GenTypeAndValueGen;
import test.gen.GenTypeAndValueGen.TypeAndValueGen;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformRunnable;
import utility.FXPlatformSupplier;
import utility.SimulationSupplier;
import utility.Utility;
import utility.Workers;
import utility.Workers.Priority;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Created by neil on 10/06/2017.
 */
@OnThread(value = Tag.FXPlatform, ignoreParent = true)
@RunWith(JUnitQuickcheck.class)
public class TestBlankMainWindow extends ApplicationTest implements ComboUtilTrait
{
    @SuppressWarnings("nullness")
    private @NonNull Stage mainWindow;

    @Override
    public void start(Stage stage) throws Exception
    {
        stage.initStyle(StageStyle.DECORATED);
        File dest = File.createTempFile("blank", "rec");
        dest.deleteOnExit();
        MainWindow.show(stage, dest, null);
        mainWindow = stage;
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
        assertTrue(fx(() -> mainWindow.isShowing()));
        assertEquals(1, (int)fx(() -> MainWindow._test_getViews().size()));
        assertTrue(fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().isEmpty()));
    }

    private static interface FXPlatformSupplierEx<T>
    {
        @OnThread(Tag.FXPlatform)
        public T get() throws InternalException, UserException;
    }

    @OnThread(Tag.Any)
    private static <T> T fx(FXPlatformSupplierEx<T> action)
    {
        try
        {
            return WaitForAsyncUtils.asyncFx(action::get).get();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    @OnThread(Tag.Any)
    private static void fx_(FXPlatformRunnable action)
    {
        try
        {
            WaitForAsyncUtils.asyncFx(action::run).get();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    @OnThread(Tag.Any)
    private static <T> T sim(SimulationSupplier<T> action)
    {
        try
        {
            CompletableFuture<T> f = new CompletableFuture<>();
            Workers.onWorkerThread("Test.sim", Priority.FETCH, () -> {
                try
                {
                    f.complete(action.get());
                }
                catch (Exception e)
                {
                    Utility.log(e);
                }
            });
            return f.get(3, TimeUnit.SECONDS);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
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
        clickOn("#id-menu-project").clickOn(".id-menu-project-close");
        assertTrue(MainWindow._test_getViews().isEmpty());
    }

    @Test
    @OnThread(Tag.Any)
    public void testNewEntryTable() throws InternalException, UserException
    {
        testStartState();
        clickOn("#id-menu-data").clickOn(".id-menu-data-new");
        assertEquals(1, (int)fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().size()));
        assertTrue(fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().get(0).getData().getColumns().isEmpty()));
    }

    @Test
    @SuppressWarnings("nullness")
    public void testAddColumnRequiresName() throws UserException, InternalException
    {
        testNewEntryTable();
        clickOn(".add-column");
        Window dialog = lookup(".ok-button").<Node>query().getScene().getWindow();
        assertTrue(dialog.isShowing());
        assertTrue(lookup(".new-column-name").<TextField>query().getText().isEmpty());
        assertTrue(lookup(".error-label").<Text>query().getText().isEmpty());
        clickOn(".ok-button");
        assertTrue(dialog.isShowing());
        assertFalse(lookup(".error-label").<Text>query().getText().isEmpty());
        clickOn(".new-column-name");
        write("C");
        clickOn(".ok-button");
        assertFalse(dialog.isShowing());
    }

    @Property(trials = 10)
    @OnThread(Tag.Any)
    public void propAddColumnToEntryTable(@From(GenDataType.class) DataType dataType) throws UserException, InternalException
    {
        testNewEntryTable();
        clickOn(".add-column");
        String newColName = "Column " + new Random().nextInt();
        write(newColName);
        clickForDataType(rootNode(window(Window::isFocused)), dataType);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(1, (int)fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().get(0).getData().getColumns().size()));
        assertEquals(newColName, fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().get(0).getData().getColumns().get(0).getName().getRaw()));
        assertEquals(dataType, fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().get(0).getData().getColumns().get(0).getType()));
    }

    private void clickOnSub(Node root, String subQuery)
    {
        assertTrue(subQuery.startsWith("."));
        @Nullable Node sub = new NodeQueryImpl().from(root).lookup(subQuery).<Node>query();
        assertNotNull(subQuery, sub);
        clickOn(sub);
    }

    // Important to use the query here, as we may have nested dialogs with items with the same class
    @OnThread(Tag.Any)
    private void clickForDataType(Node root, DataType dataType) throws InternalException, UserException
    {
        dataType.apply(new DataTypeVisitor<Void>()
        {
            @SuppressWarnings("nullness") // We'll accept the exception as it will fail the test
            private void clickOnSubOfDataTypeDialog(String subQuery)
            {
                @Nullable Node sub = lookupSubOfDataTypeDialog(subQuery);
                assertNotNull("Looked up: " + subQuery, sub);
                clickOn(sub);
            }

            private @Nullable Node lookupSubOfDataTypeDialog(String subQuery)
            {
                return new NodeQueryImpl().from(root).lookup(subQuery).<Node>query();
            }

            @Override
            @SuppressWarnings("nullness")
            public Void number(NumberInfo numberInfo) throws InternalException, UserException
            {
                clickOnSubOfDataTypeDialog(".id-type-number");
                ((TextField) lookupSubOfDataTypeDialog(".type-number-units")).setText(numberInfo.getUnit().toString());
                return null;
            }

            @Override
            public Void text() throws InternalException, UserException
            {
                clickOnSubOfDataTypeDialog(".id-type-text");
                return null;
            }

            @Override
            public Void date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                clickOnSubOfDataTypeDialog(".id-type-datetime");
                @Nullable ComboBox<DataType> combo = (ComboBox<DataType>) lookupSubOfDataTypeDialog(".type-datetime-combo");
                if (combo != null)
                    selectGivenComboBoxItem(combo, dataType);
                return null;
            }

            @Override
            public Void bool() throws InternalException, UserException
            {
                clickOnSubOfDataTypeDialog(".id-type-boolean");
                return null;
            }

            @Override
            public Void tagged(TypeId typeName, List<TagType<DataType>> tags) throws InternalException, UserException
            {
                clickOnSubOfDataTypeDialog(".id-type-tagged");
                ComboBox<DataType> combo = (ComboBox<DataType>) lookupSubOfDataTypeDialog(".type-tagged-combo");
                if (combo == null)
                    return null; // Should then fail test
                @Nullable DataType target = combo.getItems().stream()
                        .filter(dt ->
                        {
                            try
                            {
                                return dt.isTagged() && dt.getTagTypes().equals(tags);
                            }
                            catch (InternalException e)
                            {
                                throw new RuntimeException(e);
                            }
                        })
                        .findFirst().orElse(null);
                if (target == null)
                {
                    clickOnSubOfDataTypeDialog(".id-type-tagged-new");
                    Node tagDialogRoot = rootNode(window(Window::isFocused));
                    // Fill in tag types details:
                    clickOnSub(tagDialogRoot, ".taggedtype-type-name");
                    write(typeName.getRaw());
                    // First add all the tags
                    for (int i = 1; i < tags.size(); i++)
                    {
                        clickOnSub(tagDialogRoot, ".id-taggedtype-addTag");
                    }
                    // Then go fill them in:
                    for (int i = 0; i < tags.size(); i++)
                    {
                        clickOnSub(tagDialogRoot, ".taggedtype-tag-name-" + i);
                        write(tags.get(i).getName());
                        @Nullable DataType inner = tags.get(i).getInner();
                        if (inner != null)
                        {
                            clickOnSub(tagDialogRoot, ".taggedtype-tag-set-subType-" + i);
                            clickForDataType(rootNode(window(Window::isFocused)), inner);
                        }
                    }
                    // Click OK in that dialog:
                    clickOnSub(tagDialogRoot, ".ok-button");

                    target = combo.getItems().stream()
                        .filter(dt ->
                        {
                            try
                            {
                                return dt.isTagged() && dt.getTagTypes().equals(tags);
                            }
                            catch (InternalException e)
                            {
                                throw new RuntimeException(e);
                            }
                        })
                        .findFirst().orElse(null);
                }
                if (target != null)
                {
                    selectGivenComboBoxItem(combo, target);
                }
                return null;
            }

            @Override
            public Void tuple(List<DataType> inner) throws InternalException, UserException
            {
                clickOnSubOfDataTypeDialog(".id-type-tuple");
                // Should start as pair:
                assertNotNull(lookupSubOfDataTypeDialog(".type-tuple-element-0"));
                assertNotNull(lookupSubOfDataTypeDialog(".type-tuple-element-1"));
                assertNull(lookupSubOfDataTypeDialog(".type-tuple-element-2"));
                for (int i = 2; i < inner.size(); i++)
                {
                    clickOnSubOfDataTypeDialog(".id-type-tuple-more");
                }
                // TODO check you can't click OK yet
                for (int i = 0; i < inner.size(); i++)
                {
                    assertNotNull(lookupSubOfDataTypeDialog(".type-tuple-element-" + i));
                    clickOnSubOfDataTypeDialog(".type-tuple-element-" + i);
                    WaitForAsyncUtils.waitForFxEvents();
                    clickForDataType(rootNode(window(Window::isFocused)), inner.get(i));
                }
                return null;
            }

            @Override
            public Void array(@Nullable DataType inner) throws InternalException, UserException
            {
                clickOnSubOfDataTypeDialog(".id-type-list-of");
                // TODO check that clicking OK is not valid at this point

                clickOnSubOfDataTypeDialog(".type-list-of-set");
                WaitForAsyncUtils.waitForFxEvents();
                //targetWindow(Window::isFocused);
                if (inner != null)
                {
                    clickForDataType(rootNode(window(Window::isFocused)), inner);
                }
                return null;
            }
        });

        // We press OK in this method because if we've recursed, we have one dialog per recursion to dismiss:
        Window window = window(Window::isFocused);
        Text errorLabel = new NodeQueryImpl().from(rootNode(window)).lookup(".error-label").<Text>query();
        clickOn(new NodeQueryImpl().from(rootNode(window)).lookup(".ok-button").<Node>query());
        if (errorLabel != null)
            assertEquals("", fx(() -> errorLabel.getText()));
        assertFalse(fx(() -> window.isShowing()));
    }

    @Property
    @OnThread(Tag.Any)
    public void testEnterColumn(@From(GenTypeAndValueGen.class) @When(seed=1526344544435264499L) TypeAndValueGen typeAndValueGen) throws InternalException, UserException
    {
        propAddColumnToEntryTable(typeAndValueGen.getType());
        // Now set the values
        List<@Value Object> values = new ArrayList<>();
        for (int i = 0; i < 10;i ++)
        {
            addNewRow();
            @Value Object value = typeAndValueGen.makeValue();
            values.add(value);

            try
            {
                Thread.sleep(400);
            }
            catch (InterruptedException e)
            {

            }
            clickOn(".stable-view-row-cell");
            try
            {
                Thread.sleep(400);
            }
            catch (InterruptedException e)
            {

            }
            push(KeyCode.END);
            setValue(typeAndValueGen.getType(), value);
        }
        // Now test for equality:
        @OnThread(Tag.Any) RecordSet recordSet = fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().get(0).getData());
        DataTypeValue column = recordSet.getColumns().get(0).getType();
        assertEquals(values.size(), (int)sim(() -> recordSet.getLength()));
        for (int i = 0; i < values.size(); i++)
        {
            int iFinal = i;
            TestUtil.assertValueEqual("" + i, values.get(i), sim(() -> column.getCollapsed(iFinal)));
        }
    }

    @OnThread(Tag.Any)
    private void setValue(DataType dataType, @Value Object value) throws UserException, InternalException
    {
        targetWindow(lookup(".stable-view-row-cell").<Node>query());
        //TODO check colour of focused cell (either check background, or take snapshot)
        Node prevFocused = fx(() -> targetWindow().getScene().getFocusOwner());
        push(KeyCode.ENTER);
        WaitForAsyncUtils.waitForFxEvents();

        Node focused = fx(() -> targetWindow().getScene().getFocusOwner());
        assertNotNull(focused);
        dataType.apply(new DataTypeVisitor<Void>()
        {
            @Override
            public Void number(NumberInfo numberInfo) throws InternalException, UserException
            {
                assertTrue("Was " + prevFocused.getClass() + " then pressed ENTER and was " + focused.getClass(), focused instanceof GenericStyledArea);
                write(DataTypeUtility.valueToString(value));
                return null;
            }

            @Override
            public Void text() throws InternalException, UserException
            {
                write(DataTypeUtility.valueToString(value));
                return null;
            }

            @Override
            public Void date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                return null;
            }

            @Override
            public Void bool() throws InternalException, UserException
            {
                return null;
            }

            @Override
            public Void tagged(TypeId typeName, List<TagType<DataType>> tags) throws InternalException, UserException
            {
                return null;
            }

            @Override
            public Void tuple(List<DataType> inner) throws InternalException, UserException
            {
                return null;
            }

            @Override
            public Void array(@Nullable DataType inner) throws InternalException, UserException
            {
                return null;
            }
        });
    }

    @OnThread(Tag.Any)
    private void addNewRow()
    {
        // Click button to scroll to end, to ensure append is visible:
        clickOn(".stable-view-button-bottom");
        WaitForAsyncUtils.waitForFxEvents();
        clickOn(".stable-view-row-append-button");
    }

    @OnThread(Tag.Any)
    private boolean actuallyVisible(String query)
    {
        Node original  = lookup(query).<Node>query();
        if (original == null)
            return false;
        return fx(() -> {
            Bounds b = original.getBoundsInLocal();
            for (Node n = original, parent = original.getParent(); n != null && parent != null; n = parent, parent = parent.getParent())
            {
                b = n.localToParent(b);
                System.err.println("Bounds in parent: " + b.getMinY() + "->"  + b.getMaxY());
                System.err.println("  Parent bounds: " + parent.getBoundsInLocal().getMinY() + "->" + parent.getBoundsInLocal().getMaxY());
                if (!parent.getBoundsInLocal().contains(getCentre(b)))
                    return false;
            }
            // If we get to the top and all is well, it is visible
            return true;
        });
    }

    private static Point2D getCentre(Bounds b)
    {
        return new Point2D(b.getMinX(), b.getMinY()).midpoint(b.getMaxX(), b.getMaxY());
    }
}
