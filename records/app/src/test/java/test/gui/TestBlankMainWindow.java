package test.gui;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.service.query.PointQuery;
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
import test.TestUtil;
import test.gen.GenDataType;
import test.gen.GenTypeAndValueGen;
import test.gen.GenTypeAndValueGen.TypeAndValueGen;
import threadchecker.OnThread;
import threadchecker.Tag;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

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
        assertTrue(TestUtil.fx(() -> mainWindow.isShowing()));
        assertEquals(1, (int) TestUtil.fx(() -> MainWindow._test_getViews().size()));
        assertTrue(TestUtil.fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().isEmpty()));
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
        assertEquals(1, (int) TestUtil.fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().size()));
        assertTrue(TestUtil.fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().get(0).getData().getColumns().isEmpty()));
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

    @Property(trials = 5)
    @OnThread(Tag.Any)
    public void propAddColumnToEntryTable(@From(GenDataType.class) DataType dataType) throws UserException, InternalException
    {
        addNewTableWithColumn(dataType, null);
    }

    @OnThread(Tag.Any)
    private void addNewTableWithColumn(DataType dataType, @Nullable @Value Object value) throws InternalException, UserException
    {
        testNewEntryTable();
        clickOn(".add-column");
        String newColName = "Column " + new Random().nextInt();
        write(newColName);
        clickForDataTypeDialog(rootNode(window(Window::isFocused)), dataType, value);
        WaitForAsyncUtils.waitForFxEvents();
        try
        {
            Thread.sleep(2000);
        }
        catch (InterruptedException e) { }
        assertEquals(1, (int) TestUtil.fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().get(0).getData().getColumns().size()));
        assertEquals(newColName, TestUtil.fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().get(0).getData().getColumns().get(0).getName().getRaw()));
        assertEquals(dataType, TestUtil.fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().get(0).getData().getColumns().get(0).getType()));
    }

    private void clickOnSub(Node root, String subQuery)
    {
        assertTrue(subQuery.startsWith("."));
        @Nullable Node sub = new NodeQueryImpl().from(root).lookup(subQuery).<Node>query();
        assertNotNull(subQuery, sub);
        if (sub != null)
            clickOn(sub);
    }

    // Important to use the query here, as we may have nested dialogs with items with the same class
    @OnThread(Tag.Any)
    private void clickForDataTypeDialog(Node root, DataType dataType, @Nullable @Value Object initialValue) throws InternalException, UserException
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
                TestUtil.fx_(() -> ((TextField) lookupSubOfDataTypeDialog(".type-number-units")).setText(numberInfo.getUnit().toString()));
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
                @SuppressWarnings("unchecked")
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
            public Void tagged(TypeId typeName, ImmutableList<TagType<DataType>> tags) throws InternalException, UserException
            {
                clickOnSubOfDataTypeDialog(".id-type-tagged");
                @SuppressWarnings("unchecked")
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
                            clickForDataTypeDialog(rootNode(window(Window::isFocused)), inner, null);
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
            public Void tuple(ImmutableList<DataType> inner) throws InternalException, UserException
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
                    clickForDataTypeDialog(rootNode(window(Window::isFocused)), inner.get(i), null);
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
                    clickForDataTypeDialog(rootNode(window(Window::isFocused)), inner, null);
                }
                return null;
            }
        });
        if (initialValue != null)
        {
            @NonNull @Value Object val = initialValue;
            // Triple click should select all:
            @Nullable Node stf_ = new NodeQueryImpl().from(root).lookup(".new-column-value").<Node>query();
            if (stf_ == null)
            {
                assertNotNull(stf_);
                return;
            }
            @NonNull Node stf = stf_;
            // Click in top left to avoid auto complete:
            PointQuery point = TestUtil.fx(() -> offset(stf, 4 - stf.getBoundsInLocal().getWidth() / 2.0, 4 - stf.getBoundsInLocal().getHeight() / 2.0));
            clickOn(point);
            doubleClickOn(point);
            write(TestUtil.sim(() -> DataTypeUtility.valueToString(dataType, val, null)));
        }

        // We press OK in this method because if we've recursed, we have one dialog per recursion to dismiss:
        Window window = window(Window::isFocused);
        Text errorLabel = new NodeQueryImpl().from(rootNode(window)).lookup(".error-label").<Text>query();
        Node okButton = new NodeQueryImpl().from(rootNode(window)).lookup(".ok-button").<Node>query();
        if (okButton != null)
            clickOn(okButton);
        if (errorLabel != null)
        {
            @NonNull Text errorLabelF = errorLabel;
            assertEquals("", TestUtil.fx(() -> errorLabelF.getText()));
        }
        assertFalse(TestUtil.fx(() -> window.isShowing()));
    }

    @Property(trials = 5)
    @OnThread(Tag.Any)
    public void propDefaultValue(@From(GenTypeAndValueGen.class) TypeAndValueGen typeAndValueGen) throws InternalException, UserException
    {
        @Value Object initialVal = typeAndValueGen.makeValue();
        addNewTableWithColumn(typeAndValueGen.getType(), initialVal);
        List<@Value Object> values = new ArrayList<>();
        for (int i = 0; i < 3; i++)
        {
            addNewRow();
            values.add(initialVal);
            // Now test for equality:
            @OnThread(Tag.Any) RecordSet recordSet = TestUtil.fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().get(0).getData());
            DataTypeValue column = recordSet.getColumns().get(0).getType();
            assertEquals(values.size(), (int) TestUtil.sim(() -> recordSet.getLength()));
            for (int j = 0; j < values.size(); j++)
            {
                int jFinal = j;
                TestUtil.assertValueEqual("Index " + j, values.get(j), TestUtil.<@Value Object>sim(() -> column.getCollapsed(jFinal)));
            }
        }
    }

    @Property(trials = 10)
    @OnThread(Tag.Any)
    public void testEnterColumn(@From(GenTypeAndValueGen.class) @When(seed=-746430439083107785L) TypeAndValueGen typeAndValueGen) throws InternalException, UserException
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
            /*
            clickOn(new Predicate<Node>()
            {
                @Override
                @OnThread(value = Tag.FXPlatform, ignoreParent = true)
                public boolean test(Node n)
                {
                    return n.getStyleClass().contains("stable-view-row-cell") && actuallyVisible(n);
                }
            });
            try
            {
                Thread.sleep(400);
            }
            catch (InterruptedException e)
            {

            }
            push(KeyCode.END);
            */
            setValue(typeAndValueGen.getType(), value);
        }
        // Now test for equality:
        @OnThread(Tag.Any) RecordSet recordSet = TestUtil.fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().get(0).getData());
        DataTypeValue column = recordSet.getColumns().get(0).getType();
        assertEquals(values.size(), (int) TestUtil.sim(() -> recordSet.getLength()));
        for (int i = 0; i < values.size(); i++)
        {
            int iFinal = i;
            TestUtil.assertValueEqual("Index " + i, values.get(i), TestUtil.<@Value Object>sim(() -> column.getCollapsed(iFinal)));
        }
    }

    @OnThread(Tag.Any)
    private void setValue(DataType dataType, @Value Object value) throws UserException, InternalException
    {
        Node row = lookup(new Predicate<Node>()
        {
            @Override
            @OnThread(value = Tag.FXPlatform, ignoreParent = true)
            public boolean test(Node node)
            {
                // Don't click on the last row which has the append button:
                return node.getStyleClass().contains("virt-grid-cell") && node.lookup(".stable-view-row-append-button") == null;
            }

            ;
        }).<Node>query();
        if (row != null)
        {
            targetWindow(row);
            clickOn(row);
        }
        //TODO check colour of focused cell (either check background, or take snapshot)
        Node prevFocused = TestUtil.fx(() -> targetWindow().getScene().getFocusOwner());
        // Go to last row:
        push(KeyCode.END);
        // Enter to start editing:
        push(KeyCode.ENTER);
        WaitForAsyncUtils.waitForFxEvents();

        Node focused = TestUtil.fx(() -> targetWindow().getScene().getFocusOwner());
        assertNotNull(focused);
        write(TestUtil.sim(() -> DataTypeUtility.valueToString(dataType, value, null)));
        // Enter to finish editing:
        push(KeyCode.ENTER);
    }

    @OnThread(Tag.Any)
    private void addNewRow()
    {
        // Click button to scroll to end, to ensure append is visible:
        clickOn(".stable-view-button-bottom");
        WaitForAsyncUtils.waitForFxEvents();
        clickOn(".stable-view-row-append-button");
        try
        {
            Thread.sleep(400);
        }
        catch (InterruptedException e)
        {

        }
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

    @NotNull
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
