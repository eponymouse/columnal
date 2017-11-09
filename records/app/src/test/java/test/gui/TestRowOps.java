package test.gui;

import annotation.qual.Value;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.VerticalDirection;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.apache.commons.lang3.SystemUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.runner.RunWith;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.service.query.NodeQuery;
import records.data.Column;
import records.data.ColumnId;
import records.data.EditableRecordSet;
import records.data.ImmediateDataSource;
import records.data.RecordSet;
import records.data.Table;
import records.data.TableId;
import records.data.TableManager;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import records.gui.MainWindow;
import records.gui.TableDisplay;
import records.gui.stable.VirtScrollStrTextGrid;
import records.gui.stf.StructuredTextField;
import records.transformations.Sort;
import records.transformations.Transform;
import test.DummyManager;
import test.TestUtil;
import test.gen.ExpressionValue;
import test.gen.GenExpressionValueBackwards;
import test.gen.GenExpressionValueForwards;
import test.gen.GenImmediateData;
import test.gen.GenImmediateData.ImmediateData_Mgr;
import test.gen.GenRandom;
import threadchecker.OnThread;
import threadchecker.Tag;
import utility.Pair;
import utility.Utility;
import utility.gui.FXUtility;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

@RunWith(JUnitQuickcheck.class)
public class TestRowOps extends ApplicationTest implements CheckCSVTrait
{
    @OnThread(Tag.Any)
    @SuppressWarnings("nullness")
    private Stage windowToUse;

    @Override
    @OnThread(value = Tag.FXPlatform, ignoreParent = true)
    public void start(Stage stage) throws Exception
    {
        this.windowToUse = stage;
        FXUtility._test_setTestingMode();
    }


    /**
     * Generates a file with some raw data and a transform, then loads it and deletes a row in the source table.
     */
    @Property(trials = 10)
    @OnThread(Tag.Simulation)
    public void propTestDeleteRow(
        @From(GenExpressionValueForwards.class) @From(GenExpressionValueBackwards.class) ExpressionValue expressionValue,
        @From(GenRandom.class) Random r) throws UserException, InternalException, InterruptedException, ExecutionException, InvocationTargetException, IOException
    {
        if (expressionValue.recordSet.getLength() == 0)
            return; // Can't delete if there's no rows!
        if (expressionValue.recordSet.getColumns().isEmpty())
            return; // Likewise if there's no columns

        TableManager manager = new DummyManager();
        manager.getTypeManager()._test_copyTaggedTypesFrom(expressionValue.typeManager);

        Table srcData = new ImmediateDataSource(manager, new EditableRecordSet(expressionValue.recordSet));
        srcData.loadPosition(new BoundingBox(0, 0, 200, 600));
        manager.record(srcData);

        Table calculated = new Transform(manager, null, srcData.getId(), ImmutableList.of(new Pair<>(new ColumnId("Result"), expressionValue.expression)));
        calculated.loadPosition(new BoundingBox(250, 0, 200, 600));
        manager.record(calculated);

        TestUtil.openDataAsTable(windowToUse, manager);

        int randomRow = r.nextInt(expressionValue.recordSet.getLength());

        scrollToRow(calculated.getId(), randomRow);
        scrollToRow(srcData.getId(), randomRow);
        rightClickRowLabel(srcData.getId(), randomRow);
        clickOn(".id-stableView-row-delete");
        TestUtil.delay(500);
        scrollToRow(calculated.getId(), randomRow);

        // There's now several aspects to check:
        // - The row is immediately no longer visible in srcData.
        //     (Well, a row of that number will be, but with diff data).
        // - Ditto for calculated
        // - Exporting srcData does not feature the data.
        // - Ditto for calculated
        if (randomRow == expressionValue.recordSet.getLength() - 1)
        {
            // Row should be gone:
            assertNull(findRowLabel(srcData.getId(), randomRow));
            assertNull(findRowLabel(calculated.getId(), randomRow));
        }
        else
        {
            // Row still there, but data from the row after it:
            checkVisibleRowData("", srcData.getId(), randomRow, getRowVals(expressionValue.recordSet, randomRow + 1));
            checkVisibleRowData("", calculated.getId(), randomRow, getRowVals(expressionValue.recordSet, randomRow + 1));
        }
        List<Pair<String, List<String>>> expectedSrcContent = new ArrayList<>();
        List<Pair<String, List<String>>> expectedCalcContent = new ArrayList<>();
        for (Column column : srcData.getData().getColumns())
        {
            Pair<String, List<String>> colData = new Pair<>(column.getName().getRaw(), CheckCSVTrait.collapse(expressionValue.recordSet.getLength(), column.getType(), randomRow));
            expectedSrcContent.add(colData);
            expectedCalcContent.add(colData);
        }
        ArrayList<String> calcValuesFiltered = new ArrayList<>();
        for (int i = 0; i < expressionValue.value.size(); i++)
        {
            if (i != randomRow)
            {
                calcValuesFiltered.add(DataTypeUtility.valueToString(expressionValue.type, expressionValue.value.get(i), null));
            }
        }
        expectedCalcContent.add(new Pair<>("Result", calcValuesFiltered));
        exportToCSVAndCheck("After deleting " + randomRow, expectedSrcContent, srcData.getId());
        exportToCSVAndCheck("After deleting " + randomRow, expectedCalcContent, calculated.getId());
    }

    @Property(trials = 10)
    @OnThread(Tag.Simulation)
    public void propTestInsertRow(
        @When(seed=2L) @From(GenImmediateData.class)ImmediateData_Mgr srcDataAndMgr,
        @When(seed=2L) @From(GenRandom.class) Random r) throws UserException, InternalException, InterruptedException, ExecutionException, InvocationTargetException, IOException
    {
        if (srcDataAndMgr.data.isEmpty() || srcDataAndMgr.data.get(0).getData().getColumns().isEmpty())
            return; // Can't insert if there's no table or no columns

        TableManager manager = srcDataAndMgr.mgr;
        Table srcData = srcDataAndMgr.data.get(0);
        srcData.loadPosition(new BoundingBox(0, 0, 300, 600));

        Column sortBy = srcData.getData().getColumns().get(r.nextInt(srcData.getData().getColumns().size()));
        Table calculated = new Sort(manager, null, srcData.getId(), ImmutableList.of(sortBy.getName()));
        calculated.loadPosition(new BoundingBox(350, 0, 300, 600));
        manager.record(calculated);

        TestUtil.openDataAsTable(windowToUse, manager);

        int targetNewRow = r.nextInt(srcData.getData().getLength() + 1);
        boolean originalWasEmpty = false;

        // Can't do insert-after if target is first row. Can't do insert-before if it's last row
        if (targetNewRow < srcData.getData().getLength() && (targetNewRow == 0 || r.nextBoolean()))
        {
            // Let's do insert-before to get the new row
            scrollToRow(calculated.getId(), targetNewRow);
            scrollToRow(srcData.getId(), targetNewRow);
            rightClickRowLabel(srcData.getId(), targetNewRow);
            clickOn(".id-stableView-row-insertBefore");
            TestUtil.delay(500);
        }
        else if (targetNewRow > 0)
        {
            // Insert-after:
            scrollToRow(calculated.getId(), targetNewRow - 1);
            scrollToRow(srcData.getId(), targetNewRow - 1);
            rightClickRowLabel(srcData.getId(), targetNewRow - 1);
            clickOn(".id-stableView-row-insertAfter");
            TestUtil.delay(500);
        }
        else
        {
            TestUtil.delay(2000);
            // No rows at all: need to click append button
            clickOn(".id-stableView-append" /*".stable-view-row-append-button"*/);
            TestUtil.delay(500);
            originalWasEmpty = true;
        }

        // There's now several aspects to check:
        // - The row now has the default data in srcData.
        // - Ditto for calculated
        // - The row after in both cases has the old data
        // - Exporting srcData does features the new data and the other data moved.
        // - Ditto for calculated

        // Work out positions in sorted data:
        int beforeDefault = 0;
        @Nullable Pair<Integer, @Value Object> firstAfterDefault = null;
        @Nullable @Value Object sortDefault = sortBy.getDefaultValue();
        if (sortDefault == null)
        {
            fail("Default value null for sortBy column: " + sortBy.getName() + " " + sortBy.getType());
            return;
        }
        for (int i = 0; i < srcData.getData().getLength(); i++)
        {
            @Value Object value = sortBy.getType().getCollapsed(i);
            int cmp = Utility.compareValues(value, sortDefault);
            // It will be before us if either it is strictly lower, or it is equal
            // and it started before us.
            if (cmp < 0 || (i < targetNewRow && cmp == 0))
            {
                beforeDefault += 1;
            }
            else if (firstAfterDefault == null || Utility.compareValues(value, firstAfterDefault.getSecond()) < 0)
            {
                firstAfterDefault = new Pair<>(i, value);
            }
        }
        // Position when sorted is simply the number before it in sort order:
        int positionPostSort = beforeDefault;

        // TODO check for default data in inserted spot

        scrollToRow(srcData.getId(), targetNewRow - 1);
        // Row still there, but data from the row after it:
        if (!originalWasEmpty)
        {
            String prefix = "Sorted by " + sortBy.getName().getRaw() + " inserted at " + targetNewRow + " before default is " + beforeDefault + " first after default " + (firstAfterDefault == null ? "null" : Integer.toString(firstAfterDefault.getFirst())) + ";";
            checkVisibleRowData(prefix, srcData.getId(), targetNewRow + 1, getRowVals(srcData.getData(), targetNewRow));
            if (firstAfterDefault != null)
            {
                scrollToRow(calculated.getId(), beforeDefault + 1);
                checkVisibleRowData(prefix, calculated.getId(), positionPostSort + 1, getRowVals(srcData.getData(), firstAfterDefault.getFirst()));
            }
        }



        List<Pair<String, List<String>>> expectedSrcContent = new ArrayList<>();
        List<Pair<String, List<String>>> expectedCalcContent = new ArrayList<>();
        for (Column column : srcData.getData().getColumns())
        {
            DataTypeValue columnType = column.getType();
            Pair<String, List<String>> colData = new Pair<>(column.getName().getRaw(), CheckCSVTrait.collapse(srcData.getData().getLength(), columnType));
            // Add new default data at right point:
            @Nullable @Value Object defaultValue = column.getDefaultValue();
            if (defaultValue == null)
            {
                fail("Null default value for column " + column.getName().getRaw());
            }
            else
            {
                String defaultAsString = DataTypeUtility.valueToString(columnType, defaultValue, null);
                expectedSrcContent.add(colData.mapSecond(d -> {
                    ArrayList<String> xs = new ArrayList<>(d);
                    xs.add(targetNewRow, defaultAsString);
                    return xs;
                }));
                expectedCalcContent.add(colData.mapSecond(d -> {
                    ArrayList<String> xs = new ArrayList<>(d);
                    xs.add(positionPostSort, defaultAsString);
                    return xs;
                }));
            }
        }
        exportToCSVAndCheck("After inserting " + targetNewRow, expectedSrcContent, srcData.getId());
        // TODO sort expected output
        //exportToCSVAndCheck("After inserting " + targetNewRow, expectedCalcContent, calculated.getId());
    }

    @OnThread(Tag.Simulation)
    private void checkVisibleRowData(String prefix, TableId tableId, int targetRow, List<Pair<DataType, @Value Object>> expected) throws InternalException, UserException
    {
        // This is deliberately low-tech.  We really do want to
        // check the visible data on screen, not what the internals
        // report as they could be wrong.
        // Find the row label.  Should be visible based on previous actions:
        Node rowLabel = findRowLabel(tableId, targetRow);
        if (rowLabel == null)
            throw new RuntimeException("No row label " + targetRow + " in " + findVisRowLabels(tableId));
        @NonNull Node rowLabelFinal = rowLabel;
        double rowLabelTop = TestUtil.fx(() -> rowLabelFinal.localToScene(rowLabelFinal.getBoundsInLocal()).getMinY());
        List<Node> rowCells = queryTableDisplay(tableId).lookup(".virt-grid-cell").match(n -> Math.abs(TestUtil.fx(() -> n.localToScene(n.getBoundsInLocal()).getMinY()) - rowLabelTop) <= 3).queryAll().stream().sorted(Comparator.comparing(n -> TestUtil.fx(() -> n.localToScene(n.getBoundsInLocal()).getMinX()))).collect(Collectors.toList());
        for (int i = 0; i < rowCells.size(); i++)
        {
            int iFinal = i;
            assertEquals(prefix + " " + i + ": ", DataTypeUtility.valueToString(expected.get(i).getFirst(), expected.get(i).getSecond(), null), TestUtil.fx(() -> ((StructuredTextField)rowCells.get(iFinal)).getText()));
        }
    }

    @OnThread(Tag.Simulation)
    private List<Pair<DataType, @Value Object>> getRowVals(RecordSet recordSet, int targetRow)
    {
        return recordSet.getColumns().stream().<Pair<DataType, @Value Object>>map(c -> {
            try
            {
                return new Pair<DataType, @Value Object>(c.getType(), c.getType().getCollapsed(targetRow));
            }
            catch (InternalException | UserException e)
            {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());
    }

    @OnThread(Tag.Any)
    private void rightClickRowLabel(TableId id, int targetRow)
    {
        Node rowLabel = findRowLabel(id, targetRow);
        if (rowLabel == null)
            throw new RuntimeException("Row label "+ targetRow + " not found.  Labels: " + findVisRowLabels(id));
        rightClickOn(rowLabel);
    }

    @OnThread(Tag.Any)
    private String findVisRowLabels(TableId id)
    {
        NodeQuery tableDisplay = queryTableDisplay(id);
        return tableDisplay.lookup((Node l) -> l instanceof Label).<Label>queryAll().stream().map(l -> TestUtil.fx(() -> l.getText())).sorted().collect(Collectors.joining(", "));
    }

    @OnThread(Tag.Any)
    private @Nullable Node findRowLabel(TableId id, int targetRow)
    {
        NodeQuery tableDisplay = queryTableDisplay(id);
        return tableDisplay.lookup((Node l) -> l instanceof Label && TestUtil.fx(() -> ((Label)l).getText()).equals(Integer.toString(targetRow))).query();
    }

    @OnThread(Tag.Any)
    private NodeQuery queryTableDisplay(TableId id)
    {
        return lookup(".tableDisplay").match(t -> t instanceof TableDisplay && ((TableDisplay) t).getTable().getId().equals(id));
    }

    @OnThread(Tag.Any)
    private void scrollToRow(TableId id, int targetRow)
    {
        // Bring table to front:
        clickOn("#id-menu-view").clickOn(".id-menu-view-find");
        write(id.getRaw());
        push(KeyCode.ENTER);

        Node tableDisplay = lookup(".tableDisplay").match(t -> t instanceof TableDisplay && ((TableDisplay) t).getTable().getId().equals(id)).query();
        if (tableDisplay == null)
            throw new RuntimeException("Table " + id + " not found");
        @NonNull Node tableDisplayFinal = tableDisplay;
        VirtScrollStrTextGrid grid = TestUtil.fx(() -> ((TableDisplay)tableDisplayFinal)._test_getGrid());
        moveTo(TestUtil.fx(() -> grid.getNode()));
        // Avoid infinite loop in case of test failure -- limit amount of scrolls:
        for (int scrolls = 0; targetRow < TestUtil.fx(() -> grid._test_getFirstLogicalVisibleRowIncl()) && scrolls < targetRow; scrolls++)
        {
            scroll(VerticalDirection.UP);
        }
        for (int scrolls = 0; targetRow >= TestUtil.fx(() -> grid._test_getLastLogicalVisibleRowExcl()) && scrolls < targetRow; scrolls++)
        {
            scroll(VerticalDirection.DOWN);
        }
        // Wait for animated scroll to finish:
        TestUtil.delay(500);
    }
}