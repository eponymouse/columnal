package test.gui;

import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testfx.framework.junit.ApplicationTest;
import records.data.Column;
import records.data.ColumnId;
import records.data.EditableColumn;
import records.data.EditableRecordSet;
import records.data.ImmediateDataSource;
import records.data.MemoryBooleanColumn;
import records.data.MemoryNumericColumn;
import records.data.RecordSet;
import records.data.TableManager;
import records.error.InternalException;
import records.error.UserException;
import records.transformations.HideColumns;
import records.transformations.TransformationInfo;
import test.DummyManager;
import test.TestUtil;
import test.gen.ExpressionValue;
import test.gen.GenExpressionValueBackwards;
import test.gen.GenExpressionValueForwards;
import test.gen.GenRandom;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExFunction;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(JUnitQuickcheck.class)
public class TestExpressionEditDialog extends ApplicationTest implements ScrollToTrait, ListUtilTrait, TextFieldTrait
{
    @SuppressWarnings("nullness")
    @OnThread(Tag.Any)
    private Stage windowToUse;

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void start(Stage stage) throws Exception
    {
        windowToUse = stage;
    }

    @Before
    @OnThread(Tag.Simulation)
    public void loadEditDialog() throws Exception
    {
        // Need to load up main window with initial table and then open transformation dialog:
        TableManager mgr = new DummyManager();

        EditableRecordSet recordSet = new EditableRecordSet(Arrays.<ExFunction<RecordSet, EditableColumn>>asList(
            (RecordSet rs) -> new MemoryBooleanColumn(rs, new ColumnId("Alfred the Great"), Collections.emptyList(), false),
            (RecordSet rs) -> new MemoryBooleanColumn(rs, new ColumnId("William I"), Collections.emptyList(), false),
            (RecordSet rs) -> new MemoryBooleanColumn(rs, new ColumnId("William II"), Collections.emptyList(), false)
        ), () -> 0);
        mgr.record(new ImmediateDataSource(mgr, recordSet));
        TestUtil.openDataAsTable(windowToUse, mgr.getTypeManager(), recordSet);

        scrollTo(".id-tableDisplay-menu-button");
        clickOn(".id-tableDisplay-menu-button").clickOn(".id-tableDisplay-menu-addTransformation");
    }

    @Test
    @OnThread(Tag.FX)
    public void testTableName()
    {
        @Nullable ListView<TransformationInfo> list = lookup(".transformation-list").query();
        assertNotNull(list);
        if (list == null) return; // Checker doesn't understand assert
        selectGivenListViewItem(list, (TransformationInfo ti) -> ti instanceof HideColumns.Info);
        clickOn(".transformation-table-id");
        TextField field = selectAllCurrentTextField();
        push(KeyCode.DELETE);
        // Assert that empty field shows red border:
        assertRedBorder(field);
        // TODO assert that error popup is showing
    }

    @OnThread(Tag.FX)
    private void assertRedBorder(Node node)
    {
        Image image = TestUtil.fx(() -> node.snapshot(null, null));
        // For debugging -- but you must paste while program is still running!
        TestUtil.fx_(() -> {
            ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.putImage(image);
            Clipboard.getSystemClipboard().setContent(clipboardContent);
        });
        assertRed(image.getPixelReader().getColor(2, 2));
        assertRed(image.getPixelReader().getColor((int)image.getWidth() - 3, (int)image.getHeight() - 3));
    }

    @OnThread(Tag.FX)
    private void assertRed(Color color)
    {
        // Assert that red is twice average of blue and green:
        assertThat("Color: " + color, color.getRed(), greaterThan(color.getBlue() + color.getGreen()));
    }
}
