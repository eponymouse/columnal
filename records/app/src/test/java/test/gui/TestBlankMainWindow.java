package test.gui;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.service.query.NodeQuery;
import org.testfx.service.query.impl.NodeQueryImpl;
import org.testfx.util.WaitForAsyncUtils;
import records.data.datatype.DataType;
import records.data.datatype.DataType.DataTypeVisitor;
import records.data.datatype.DataType.DateTimeInfo;
import records.data.datatype.DataType.TagType;
import records.data.datatype.NumberInfo;
import records.data.datatype.TypeId;
import records.error.InternalException;
import records.error.UserException;
import records.gui.MainWindow;
import test.gen.GenDataType;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.FXPlatformRunnable;
import utility.gui.FXUtility;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * Created by neil on 10/06/2017.
 */
@OnThread(value = Tag.FXPlatform, ignoreParent = true)
@RunWith(JUnitQuickcheck.class)
public class TestBlankMainWindow extends ApplicationTest
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
    public void testStartState()
    {
        assertTrue(mainWindow.isShowing());
        assertEquals(1, MainWindow._test_getViews().size());
        assertTrue(MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().isEmpty());
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
    public void testNewEntryTable() throws InternalException, UserException
    {
        testStartState();
        clickOn("#id-menu-data").clickOn(".id-menu-data-new");
        assertEquals(1, MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().size());
        assertTrue(MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().get(0).getData().getColumns().isEmpty());
    }

    // TODO test that you can't click ok in new column dialog when it's not yet valid
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
    }

    @Property(trials = 10)
    public void propAddColumnToEntryTable(@When(seed=-7087735576975592678L) @From(GenDataType.class) DataType dataType) throws UserException, InternalException
    {
        testNewEntryTable();
        clickOn(".add-column");
        String newColName = "Column " + new Random().nextInt();
        write(newColName);
        clickForDataType(rootNode(window(Window::isFocused)), dataType);
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(1, MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().get(0).getData().getColumns().size());
        assertEquals(newColName, MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().get(0).getData().getColumns().get(0).getName().getRaw());
        assertEquals(dataType, MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().get(0).getData().getColumns().get(0).getType());
    }

    // Important to use the query here, as we may have nested dialogs with items with the same class
    private void clickForDataType(Node root, DataType dataType) throws InternalException, UserException
    {
        dataType.apply(new DataTypeVisitor<Void>()
        {
            private void clickOnSub(String subQuery)
            {
                clickOn(new NodeQueryImpl().from(root).lookup(subQuery).<Node>query());
            }

            @Override
            public Void number(NumberInfo numberInfo) throws InternalException, UserException
            {
                clickOnSub(".id-type-number");
                return null;
            }

            @Override
            public Void text() throws InternalException, UserException
            {
                clickOnSub(".id-type-text");
                return null;
            }

            @Override
            public Void date(DateTimeInfo dateTimeInfo) throws InternalException, UserException
            {
                clickOnSub(".id-type-datetime");
                clickOnSub(".type-datetime-combo");
                // Note clickOn, not clickOnSub, for combo children:
                clickOn(dataType.toString());
                return null;
            }

            @Override
            public Void bool() throws InternalException, UserException
            {
                clickOnSub(".id-type-boolean");
                return null;
            }

            @Override
            public Void tagged(TypeId typeName, List<TagType<DataType>> tags) throws InternalException, UserException
            {
                clickOnSub(".id-type-tagged");
                return null;
            }

            @Override
            public Void tuple(List<DataType> inner) throws InternalException, UserException
            {
                clickOnSub(".id-type-tuple");
                return null;
            }

            @Override
            public Void array(@Nullable DataType inner) throws InternalException, UserException
            {
                clickOnSub(".id-type-list-of");
                clickOnSub(".type-list-of-set");
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
        clickOn(new NodeQueryImpl().from(rootNode(window(Window::isFocused))).lookup(".ok-button").<Node>query());
    }
}
