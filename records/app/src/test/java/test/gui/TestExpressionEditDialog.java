package test.gui;

import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.scene.Node;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;
import records.data.ColumnId;
import records.data.EditableColumn;
import records.data.EditableRecordSet;
import records.data.ImmediateDataSource;
import records.data.MemoryBooleanColumn;
import records.data.RecordSet;
import records.data.TableId;
import records.data.TableManager;
import records.gui.EditTransformationDialog;
import records.gui.MainWindow;
import records.gui.View;
import records.transformations.HideColumns;
import records.transformations.TransformationInfo;
import test.DummyManager;
import test.TestUtil;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.ExFunction;

import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(JUnitQuickcheck.class)
public class TestExpressionEditDialog extends ApplicationTest implements ScrollToTrait, ListUtilTrait, TextFieldTrait
{
    @SuppressWarnings("nullness")
    @OnThread(Tag.Any)
    private Stage windowToUse;
    @SuppressWarnings("nullness")
    @OnThread(Tag.Any)
    private TableManager mgr;

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
        mgr = new DummyManager();

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
        assertErrorPopupShowing(false);
        clickOn(".transformation-table-id");
        TextField field = selectAllCurrentTextField();
        push(KeyCode.DELETE);
        // Assert that empty field shows red border and error popup:
        assertRedBorder(field, true);
        assertErrorPopupShowing(true);
        write(" Test  Spaces\u00A0And\u2000Tabs \u00A0 (incl \u2001\u2002 \u2003 \u3000 Multiple)\u00A0");
        assertEquals(new TableId("Test Spaces And Tabs (incl Multiple)"),
            TestUtil.<@Nullable TableId>fx(() -> {
                @Nullable EditTransformationDialog editTransformationDialog = MainWindow._test_getViews().keySet().iterator().next()._test_getCurrentlyShowingEditTransformationDialog();
                if (editTransformationDialog != null)
                    return editTransformationDialog._test_getDestTableNameField().valueProperty().get();
                else
                    return null;
            }));

        // Make sure duplicate table name gives error:
        selectAllCurrentTextField();
        push(KeyCode.DELETE);
        assertRedBorder(field, true);
        assertErrorPopupShowing(true);
        String sourceId = TestUtil.fx(() -> MainWindow._test_getViews().keySet().iterator().next().getManager().getAllTables().get(0).getId().getRaw());
        write(sourceId.charAt(0));
        // Wait for error fade-out:
        TestUtil.sleep(200);
        assertRedBorder(field, false);
        assertErrorPopupShowing(false);
        write(sourceId.substring(1));
        assertRedBorder(field, true);
        assertErrorPopupShowing(true);
        write("A");
        // Wait for error fade-out:
        TestUtil.sleep(200);
        assertRedBorder(field, false);
        assertErrorPopupShowing(false);
    }

    @OnThread(Tag.FX)
    private void assertErrorPopupShowing(boolean showing)
    {
        assertEquals(showing, lookup(".errorable-text-field-popup").tryQuery().map(Node::isVisible).orElse(false));
    }

    @OnThread(Tag.FX)
    private void assertRedBorder(Node node, boolean shouldBeRed)
    {
        Image image = TestUtil.fx(() -> node.snapshot(null, null));
        // For debugging -- but you must paste while program is still running!
        /*
        TestUtil.fx_(() -> {
            ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.putImage(image);
            Clipboard.getSystemClipboard().setContent(clipboardContent);
        });
        */
        assertRed(shouldBeRed, image.getPixelReader().getColor(2, 2));
        assertRed(shouldBeRed, image.getPixelReader().getColor((int)image.getWidth() - 3, (int)image.getHeight() - 3));
    }

    @OnThread(Tag.FX)
    private void assertRed(boolean red, Color color)
    {
        // Assert that red is twice average of blue and green:
        if (red)
            assertThat("Color: " + color, color.getRed(), greaterThan(color.getBlue() + color.getGreen()));
        else
            // Not much more red than the other colours:
            assertThat("Color " + color, color.getRed(), lessThan(color.getBlue() + color.getGreen() * 0.6));
    }
}
