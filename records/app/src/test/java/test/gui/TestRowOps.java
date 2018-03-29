package test.gui;

import annotation.qual.Value;
import annotation.units.AbsRowIndex;
import annotation.units.TableDataRowIndex;
import com.google.common.collect.ImmutableList;
import com.pholser.junit.quickcheck.From;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.When;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.stage.Stage;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.units.qual.A;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testfx.framework.junit.ApplicationTest;
import org.testfx.service.query.NodeQuery;
import records.data.CellPosition;
import records.data.Column;
import records.data.ColumnId;
import records.data.EditableRecordSet;
import records.data.ImmediateDataSource;
import records.data.RecordSet;
import records.data.Table;
import records.data.Table.InitialLoadDetails;
import records.data.TableId;
import records.data.TableManager;
import records.data.datatype.DataType;
import records.data.datatype.DataTypeUtility;
import records.data.datatype.DataTypeValue;
import records.error.InternalException;
import records.error.UserException;
import records.gui.MainWindow.MainWindowActions;
import records.gui.TableDisplay;
import records.gui.grid.VirtualGrid;
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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

@RunWith(JUnitQuickcheck.class)
public class TestRowOps extends ApplicationTest implements CheckCSVTrait, ClickOnTableHeaderTrait
{
    @SuppressWarnings("units")
    public static final @TableDataRowIndex int ONE_ROW = 1;
    @OnThread(Tag.Any)
    @SuppressWarnings("nullness")
    private Stage windowToUse;
    
    @OnThread(Tag.Any)
    @SuppressWarnings("nullness")
    private VirtualGrid virtualGrid;
    @OnThread(Tag.Any)
    @SuppressWarnings("nullness")
    private TableManager tableManager;

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
        @When(seed=1L) @From(GenExpressionValueForwards.class) @From(GenExpressionValueBackwards.class) ExpressionValue expressionValue,
        @When(seed=1L) @From(GenRandom.class) Random r) throws Exception
    {
        if (expressionValue.recordSet.getLength() == 0)
            return; // Can't delete if there's no rows!
        if (expressionValue.recordSet.getColumns().isEmpty())
            return; // Likewise if there's no columns

        TableManager manager = new DummyManager();
        manager.getTypeManager()._test_copyTaggedTypesFrom(expressionValue.typeManager);

        Table srcData = new ImmediateDataSource(manager, new InitialLoadDetails(null, CellPosition.ORIGIN, null), new EditableRecordSet(expressionValue.recordSet));
        manager.record(srcData);

        InitialLoadDetails ild = new InitialLoadDetails(null, new CellPosition(CellPosition.row(1), CellPosition.col(2 + expressionValue.recordSet.getColumns().size())), null);
        Table calculated = new Transform(manager, ild, srcData.getId(), ImmutableList.of(new Pair<>(new ColumnId("Result"), expressionValue.expression)));
        manager.record(calculated);

        MainWindowActions details = TestUtil.openDataAsTable(windowToUse, manager).get();
        tableManager = details._test_getTableManager();
        virtualGrid = details._test_getVirtualGrid();

        @SuppressWarnings("units")
        @TableDataRowIndex int randomRow = r.nextInt(expressionValue.recordSet.getLength());

        scrollToRow(srcData.getId(), randomRow);
        rightClickRowLabel(srcData.getId(), randomRow);
        clickOn(".id-virtGrid-row-delete");
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
        exportToCSVAndCheck(virtualGrid, details._test_getTableManager(),"After deleting " + randomRow, expectedSrcContent, srcData.getId());
        exportToCSVAndCheck(virtualGrid, details._test_getTableManager(),"After deleting " + randomRow, expectedCalcContent, calculated.getId());
    }

    @Property(trials = 10)
    @OnThread(Tag.Any)
    public void propTestInsertRow(
        @When(seed=1L) @From(GenImmediateData.class)ImmediateData_Mgr srcDataAndMgr,
        @When(seed=1L) @From(GenRandom.class) Random r) throws UserException, InternalException, InterruptedException, ExecutionException, InvocationTargetException, IOException
    {
        if (srcDataAndMgr.data.isEmpty() || srcDataAndMgr.data.get(0).getData().getColumns().isEmpty())
            return; // Can't insert if there's no table or no columns

        TableManager manager = srcDataAndMgr.mgr;
        Table srcData = srcDataAndMgr.data.get(0);

        Column sortBy = srcData.getData().getColumns().get(r.nextInt(srcData.getData().getColumns().size()));
        InitialLoadDetails ild = new InitialLoadDetails(null, new CellPosition(CellPosition.row(1), CellPosition.col(2 + srcData.getData().getColumns().size())), null);
        Table calculated = TestUtil.sim(() -> new Sort(manager, ild, srcData.getId(), ImmutableList.of(sortBy.getName())));
        MainWindowActions details = TestUtil.sim(() -> {
            manager.record(calculated);
            try
            {
                return TestUtil.openDataAsTable(windowToUse, manager);
            } catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }).get();
        tableManager = details._test_getTableManager();
        virtualGrid = details._test_getVirtualGrid();

        int srcLength = TestUtil.sim(() -> srcData.getData().getLength());
        @SuppressWarnings("units")
        @TableDataRowIndex int targetNewRow = r.nextInt(srcLength + 1);
        boolean originalWasEmpty = false;

        // Can't do insert-after if target is first row. Can't do insert-before if it's last row
        if (targetNewRow < srcLength && (targetNewRow == 0 || r.nextBoolean()))
        {
            // Let's do insert-before to get the new row
            scrollToRow(srcData.getId(), targetNewRow);
            rightClickRowLabel(srcData.getId(), targetNewRow);
            clickOn(".id-virtGrid-row-insertBefore");
            TestUtil.delay(500);
        }
        else if (targetNewRow > 0)
        {
            // Insert-after:
            @TableDataRowIndex int beforeTargetRow = targetNewRow - ONE_ROW;
            scrollToRow(srcData.getId(), beforeTargetRow);
            rightClickRowLabel(srcData.getId(), beforeTargetRow);
            clickOn(".id-virtGrid-row-insertAfter");
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
        @SuppressWarnings("units")
        @TableDataRowIndex int beforeDefault = 0;
        @Nullable Pair<Integer, @Value Object> firstAfterDefault = null;
        @Nullable @Value Object sortDefault_ = TestUtil.<@Nullable @Value Object>sim(() -> sortBy.getDefaultValue());
        if (sortDefault_ == null)
        {
            fail("Default value null for sortBy column: " + sortBy.getName() + " " + sortBy.getType());
            return;
        }
        @NonNull @Value Object sortDefault = sortDefault_;

        int newSrcLength = TestUtil.sim(() -> srcData.getData().getLength());
        for (int i = 0; i < newSrcLength; i++)
        {
            int iFinal = i;
            @Value Object value = TestUtil.<@Value Object>sim(() -> sortBy.getType().getCollapsed(iFinal));
            int cmp = TestUtil.sim(() -> Utility.compareValues(value, sortDefault));
            // It will be before us if either it is strictly lower, or it is equal
            // and it started before us.
            if (cmp < 0 || (i < targetNewRow && cmp == 0))
            {
                beforeDefault += ONE_ROW;
            }
            else
            {
                @Nullable Pair<Integer, @Value Object> curFirstAfterDefault = firstAfterDefault;
                if (TestUtil.sim(() -> curFirstAfterDefault == null || Utility.compareValues(value, curFirstAfterDefault.getSecond()) < 0))
                {
                    firstAfterDefault = new Pair<>(i, value);
                }
            }
        }
        // Position when sorted is simply the number before it in sort order:
        @TableDataRowIndex int positionPostSort = beforeDefault;

        // TODO check for default data in inserted spot

        scrollToRow(srcData.getId(), targetNewRow - ONE_ROW);
        TestUtil.sleep(1000);
        // Row still there, but data from the row after it:
        if (targetNewRow < newSrcLength)
        {
            String prefix = "Sorted by " + sortBy.getName().getRaw() + " inserted at " + targetNewRow + " before default is " + beforeDefault + " first after default " + (firstAfterDefault == null ? "null" : Integer.toString(firstAfterDefault.getFirst())) + ";";
            TestUtil.sim_(() -> checkVisibleRowData(prefix, srcData.getId(), targetNewRow + ONE_ROW, getRowVals(srcData.getData(), targetNewRow)));
            if (firstAfterDefault != null)
            {
                scrollToRow(calculated.getId(), beforeDefault + ONE_ROW);
                Pair<Integer, @Value Object> firstAfterDefaultFinal = firstAfterDefault;
                TestUtil.sim_(() -> checkVisibleRowData(prefix, calculated.getId(), positionPostSort + ONE_ROW, getRowVals(srcData.getData(), firstAfterDefaultFinal.getFirst())));
            }
        }



        List<Pair<String, List<String>>> expectedSrcContent = new ArrayList<>();
        List<Pair<String, List<String>>> expectedCalcContent = new ArrayList<>();
        for (Column column : srcData.getData().getColumns())
        {
            DataTypeValue columnType = column.getType();
            Pair<String, List<String>> colData = new Pair<>(column.getName().getRaw(), TestUtil.sim(() -> CheckCSVTrait.collapse(srcData.getData().getLength(), columnType)));
            // Add new default data at right point:
            @Nullable @Value Object defaultValue_ = TestUtil.<@Nullable @Value Object>sim(() -> column.getDefaultValue());
            if (defaultValue_ == null)
            {
                fail("Null default value for column " + column.getName().getRaw());
            }
            else
            {
                @NonNull @Value Object defaultValue = defaultValue_;
                String defaultAsString = TestUtil.sim(() -> DataTypeUtility.valueToString(columnType, defaultValue, null));
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
        TestUtil.sim_(() -> {
            try
            {
                exportToCSVAndCheck(virtualGrid, details._test_getTableManager(),"After inserting " + targetNewRow, expectedSrcContent, srcData.getId());
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        });
                // TODO sort expected output
                //exportToCSVAndCheck("After inserting " + targetNewRow, expectedCalcContent, calculated.getId());

    }

    @OnThread(Tag.Simulation)
    private void checkVisibleRowData(String prefix, TableId tableId, @TableDataRowIndex int targetRow, List<Pair<DataType, @Value Object>> expected) throws InternalException, UserException
    {
        // This is deliberately low-tech.  We really do want to
        // check the visible data on screen, not what the internals
        // report as they could be wrong.
        // Find the row label.  Should be visible based on previous actions:
        Node rowLabel = findRowLabel(tableId, targetRow);
        if (rowLabel == null)
            throw new RuntimeException("No row label for zero-based row " + targetRow + " in " + findVisRowLabels(tableId) + "focused: " + TestUtil.fx(() -> targetWindow().getScene().getFocusOwner()));
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
    private void rightClickRowLabel(TableId id, @TableDataRowIndex int targetRow) throws UserException
    {
        Node rowLabel = findRowLabel(id, targetRow);
        if (rowLabel == null)
            throw new RuntimeException("Row label for 0-based " + targetRow + " not found.  Labels: " + findVisRowLabels(id));
        rightClickOn(rowLabel);
    }

    @OnThread(Tag.Any)
    private String findVisRowLabels(TableId id)
    {
        NodeQuery tableDisplay = queryTableDisplay(id);
        return tableDisplay.lookup((Node l) -> l instanceof Label).<Label>queryAll().stream().map(l -> TestUtil.fx(() -> l.getText())).sorted().collect(Collectors.joining(", "));
    }

    // Note: table ID header will get clicked, to expose the labels
    @OnThread(Tag.Any)
    private @Nullable Node findRowLabel(TableId id, @TableDataRowIndex int targetRow) throws UserException
    {
        // Move to first cell in that row, which will make row labels visible
        // and ensure we are at correct Y position
        keyboardMoveTo(virtualGrid, TestUtil.tablePosition(tableManager,id).offsetByRowCols(3 + targetRow, 0));
        return lookup(".virt-grid-row-label-pane").match(Node::isVisible)
            .lookup(".virt-grid-row-label").match((Node l) -> l instanceof Label && TestUtil.fx(() -> ((Label)l).getText().trim().equals(Integer.toString(1 + targetRow)))).query();
    }

    @OnThread(Tag.Any)
    private NodeQuery queryTableDisplay(TableId id)
    {
        // TODO This is broken in new scheme
        return lookup(".tableDisplay");
    }

    @OnThread(Tag.Any)
    private void scrollToRow(TableId id, @TableDataRowIndex int targetRow) throws UserException
    {
        keyboardMoveTo(virtualGrid, TestUtil.tablePosition(tableManager, id).offsetByRowCols(targetRow + 3, 0));
    }
}